package org.onedroid.radiowave.app.player

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryResult
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.onedroid.radiowave.app.utils.Result
import org.onedroid.radiowave.domain.Radio
import org.onedroid.radiowave.domain.RadioRepository

internal object RadioPlaybackConfig {
    @Volatile
    var referer: String? = null
}

class RadioPlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaLibrarySession: MediaLibrarySession
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var retryCount = 0
    private var retryRunnable: Runnable? = null
    @Volatile private var libraryStations: List<Radio> = emptyList()
    @Volatile private var savedStationIds: Set<String> = emptySet()

    private val categories = listOf("اغاني", "منوعات", "رياضة", "دراما", "قرآن", "اخبار")
    private val rootId = "me-radio-root"
    private val favoritesId = "me-radio-favorites"
    private val allStationsId = "category:all"

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ) = Futures.immediateFuture(
            LibraryResult.ofItem(browsableItem(rootId, "ME Radio"), params)
        )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ) = Futures.immediateFuture(
            LibraryResult.ofItemList(
                ImmutableList.copyOf(childrenFor(parentId).drop(page * pageSize).take(pageSize)),
                params
            )
        )

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ) = Futures.immediateFuture(
            findLibraryItem(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            RadioPlaybackConfig.referer = mediaItem?.mediaMetadata?.extras
                ?.getString("me_radio_referer")
                ?.takeIf { it.startsWith("https://") }
            retryCount = 0
            retryRunnable?.let(mainHandler::removeCallbacks)
            retryRunnable = null
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                retryRunnable?.let(mainHandler::removeCallbacks)
                retryRunnable = null
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            scheduleReconnect()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val dataSourceFactory = DataSource.Factory {
            val sourceReferer = RadioPlaybackConfig.referer?.takeIf { it.startsWith("https://") }
            val headers = if (sourceReferer != null) {
                mapOf(
                    "Referer" to sourceReferer,
                    "Origin" to "https://www.maspero.eg",
                    "Accept" to "*/*"
                )
            } else {
                mapOf("Accept" to "*/*")
            }

            DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
                .createDataSource()
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(playerListener)
            }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, libraryCallback).build()
        loadLibrary()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaLibrarySession

    private fun loadLibrary() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val repository = GlobalContext.get().get<RadioRepository>()
                val result = repository.getMEStations()
                val stations = (result as? Result.Success)?.data.orEmpty()
                val saved = repository.getSavedRadios().first()
                val savedById = saved.associateBy { it.id }

                libraryStations = stations.map { station ->
                    val savedUrl = savedById[station.id]?.playableUrl?.takeIf { it.isNotBlank() }
                    val streamUrl = station.playableUrl.takeIf { it.isNotBlank() } ?: savedUrl
                    if (streamUrl == null) station
                    else station.copy(url = streamUrl, urlResolved = streamUrl)
                }.filter { it.playableUrl.isNotBlank() || !it.masperoName.isNullOrBlank() }
                savedStationIds = saved.mapTo(mutableSetOf()) { it.id }

                launch(Dispatchers.Main) {
                    if (::mediaLibrarySession.isInitialized) {
                        mediaLibrarySession.notifyChildrenChanged(rootId, categories.size + 2, null)
                        mediaLibrarySession.notifyChildrenChanged(allStationsId, libraryStations.size, null)
                        categories.forEach { category ->
                            val count = libraryStations.count { it.category == category }
                            mediaLibrarySession.notifyChildrenChanged(categoryId(category), count, null)
                        }
                        mediaLibrarySession.notifyChildrenChanged(favoritesId, savedStationIds.size, null)
                    }
                }
            } catch (_: Exception) {
                // Keep the session available so the car can retry browsing.
            }
        }
    }

    private fun childrenFor(parentId: String): List<MediaItem> = when {
        parentId == rootId -> listOf(browsableItem(allStationsId, "كل المحطات")) +
            categories.map { browsableItem(categoryId(it), it) } + browsableItem(favoritesId, "المفضلة")
        parentId == allStationsId -> libraryStations.map(::playableItem)
        parentId == favoritesId -> libraryStations
            .filter { it.id in savedStationIds }
            .map(::playableItem)
        parentId.startsWith("category:") -> {
            val category = categories.firstOrNull { categoryId(it) == parentId }
            libraryStations.filter { it.category == category }.map(::playableItem)
        }
        else -> emptyList()
    }

    private fun findLibraryItem(mediaId: String): MediaItem? {
        if (mediaId == rootId) return browsableItem(rootId, "ME Radio")
        if (mediaId == favoritesId) return browsableItem(favoritesId, "المفضلة")
        if (mediaId == allStationsId) return browsableItem(allStationsId, "كل المحطات")
        categories.firstOrNull { categoryId(it) == mediaId }?.let {
            return browsableItem(mediaId, it)
        }
        return libraryStations.firstOrNull { it.id == mediaId }?.let(::playableItem)
    }

    private fun categoryId(category: String) = "category:$category"

    private fun browsableItem(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun playableItem(radio: Radio) = MediaItem.Builder()
        .setMediaId(radio.id)
        .setUri(radio.playableUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(radio.name)
                .setArtist("ME Radio")
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(android.os.Bundle().apply {
                    putString("me_radio_referer", radio.streamReferer)
                })
                .build()
        )
        .build()

    private fun scheduleReconnect() {
        if (!::player.isInitialized || !player.playWhenReady || player.mediaItemCount == 0) return
        if (retryCount >= RETRY_DELAYS_MS.size) return

        retryRunnable?.let(mainHandler::removeCallbacks)
        val waitMs = RETRY_DELAYS_MS[retryCount]
        retryCount++

        retryRunnable = Runnable {
            if (player.playWhenReady && player.mediaItemCount > 0) {
                player.prepare()
                player.play()
            }
        }.also { mainHandler.postDelayed(it, waitMs) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        retryRunnable?.let(mainHandler::removeCallbacks)
        if (::mediaLibrarySession.isInitialized) mediaLibrarySession.release()
        if (::player.isInitialized) {
            player.removeListener(playerListener)
            player.release()
        }
        super.onDestroy()
    }

    private companion object {
        val RETRY_DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}
