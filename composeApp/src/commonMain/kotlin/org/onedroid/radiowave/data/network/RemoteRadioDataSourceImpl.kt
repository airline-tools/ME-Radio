package org.onedroid.radiowave.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.onedroid.radiowave.app.utils.DataError
import org.onedroid.radiowave.app.utils.RADIO_BROWSER_BASE_URL_SERVER1
import org.onedroid.radiowave.app.utils.RADIO_BROWSER_BASE_URL_SERVER2
import org.onedroid.radiowave.app.utils.RADIO_BROWSER_BASE_URL_SERVER3
import org.onedroid.radiowave.app.utils.Result
import org.onedroid.radiowave.app.utils.USER_AGENT
import org.onedroid.radiowave.app.utils.safeCall
import org.onedroid.radiowave.data.dto.MEStationDto
import org.onedroid.radiowave.data.dto.MasperoStreamDto
import org.onedroid.radiowave.data.dto.RadioSearchResponseDto
import radiowave.composeapp.generated.resources.Res

class RemoteRadioDataSourceImpl(
    private val httpClient: HttpClient,
    private val appPreferences: org.onedroid.radiowave.app.utils.AppPreferences,
) : RemoteRadioDataSource {

    private val radioBrowserBaseUrls = listOf(
        RADIO_BROWSER_BASE_URL_SERVER1,
        RADIO_BROWSER_BASE_URL_SERVER2,
        RADIO_BROWSER_BASE_URL_SERVER3,
    )

    override suspend fun searchRadio(
        query: String,
        resultLimit: Int?
    ): Result<List<RadioSearchResponseDto>, DataError.Remote> {
        return fetchFromMirrors { baseUrl ->
            safeCall<List<RadioSearchResponseDto>> {
                httpClient.get("$baseUrl/stations/search") {
                    header("User-Agent", USER_AGENT)
                    parameter("name", query)
                    parameter("limit", resultLimit)
                    parameter("hidebroken", "true")
                    parameter("order", "clickcount")
                }
            }
        }
    }

    override suspend fun fetchRadios(
        offset: Int,
        limit: Int
    ): Result<List<RadioSearchResponseDto>, DataError.Remote> {
        return fetchFromMirrors { baseUrl ->
            safeCall<List<RadioSearchResponseDto>> {
                httpClient.get("$baseUrl/stations") {
                    header("User-Agent", USER_AGENT)
                    parameter("limit", limit)
                    parameter("offset", offset)
                    parameter("hidebroken", "true")
                    parameter("order", "clickcount")
                    parameter("reverse", "true")
                }
            }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    override suspend fun fetchMEStations(): Result<List<MEStationDto>, DataError.Remote> {
        // GitHub is the source of truth. Add a cache-busting query and
        // no-cache headers so edits to stations.json are visible immediately.
        val remoteCatalog = safeCall<String> {
            httpClient.get(ME_RADIO_CATALOG_URL) {
                parameter("_refresh", Clock.System.now().toEpochMilliseconds())
                header("Accept", "application/json, text/plain, */*")
                header("Cache-Control", "no-cache, no-store")
                header("Pragma", "no-cache")
            }
        }

        val json = Json { ignoreUnknownKeys = true }
        val remoteText = (remoteCatalog as? Result.Success)?.data
        val remoteStations = remoteText?.let { text ->
            try {
                json.decodeFromString<List<MEStationDto>>(text)
            } catch (_: Exception) {
                null
            }
        }

        // Save only a successfully decoded remote catalog. If GitHub is
        // unavailable or returns invalid JSON, use the last valid local copy.
        if (remoteStations != null && remoteText != null) {
            appPreferences.saveCachedStationCatalog(remoteText)
        }

        val catalog = remoteStations
            ?: appPreferences.getCachedStationCatalog()?.let { cached ->
                try {
                    json.decodeFromString<List<MEStationDto>>(cached)
                } catch (_: Exception) {
                    null
                }
            }
            ?: try {
                json.decodeFromString<List<MEStationDto>>(
                    Res.readBytes("files/stations.json").decodeToString()
                )
            } catch (_: Exception) {
                return when (remoteCatalog) {
                    is Result.Error -> remoteCatalog
                    is Result.Success -> Result.Error(DataError.Remote.UNKNOWN)
                }
            }

        // Maspero URLs are signed and may expire. Refresh them when online;
        // when offline, retain any stream URL already present in the catalog.
        // GitHub raw may serve JSON files as text/plain. Read the response as
        // text first, then decode explicitly so Ktor content-type negotiation
        // cannot reject the Maspero feed.
        val masperoTextResult = safeCall<String> {
            httpClient.get(MASPERO_STREAMS_URL) {
                // Maspero publishes short-lived signed CDN URLs. A unique query
                // parameter and no-cache headers force a fresh snapshot on launch.
                parameter("_refresh", Clock.System.now().toEpochMilliseconds())
                header("User-Agent", "ME-Radio/1.0 Android Radio Player")
                header("Accept", "application/json, text/plain, */*")
                header("Cache-Control", "no-cache, no-store")
                header("Pragma", "no-cache")
            }
        }
        val liveStreams = when (masperoTextResult) {
            is Result.Success -> try {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString<List<MasperoStreamDto>>(masperoTextResult.data)
            } catch (_: Exception) {
                emptyList()
            }
            is Result.Error -> emptyList()
        }

        return Result.Success(
            catalog.map { station ->
                val expectedName = station.masperoName?.trim()
                val liveStation = expectedName?.let { name ->
                    liveStreams.firstOrNull {
                        normalizeStationName(it.name) == normalizeStationName(name)
                    }
                }
                val liveUrl = liveStation?.url
                    ?.trim()
                    ?.takeIf { it.startsWith("https://") && it.contains(".m3u8", ignoreCase = true) }

                station.copy(
                    stream = liveUrl ?: station.stream,
                    streamReferer = liveStation?.source ?: station.streamReferer
                )
            }
        )
    }

    private fun normalizeStationName(value: String): String =
        value.trim()
            .replace("ـ", "")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("\\s+"), "")
            .lowercase()

    private suspend fun <T> fetchFromMirrors(
        attempt: suspend (String) -> Result<T, DataError.Remote>
    ): Result<T, DataError.Remote> {
        val errors = mutableListOf<DataError.Remote>()
        for (baseUrl in radioBrowserBaseUrls) {
            when (val result = attempt(baseUrl)) {
                is Result.Success -> return result
                is Result.Error -> errors.add(result.error)
            }
        }

        return Result.Error(errors.lastOrNull() ?: DataError.Remote.UNKNOWN)
    }

    private companion object {
        const val ME_RADIO_CATALOG_URL =
            "https://raw.githubusercontent.com/airline-tools/ME-Radio/main/composeApp/src/commonMain/composeResources/files/stations.json"
        const val MASPERO_STREAMS_URL =
            "https://raw.githubusercontent.com/airline-tools/maspero-stream-finder/main/maspero-streams.json"
    }
}
