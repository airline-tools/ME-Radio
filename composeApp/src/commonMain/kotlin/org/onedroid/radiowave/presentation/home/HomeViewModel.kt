package org.onedroid.radiowave.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.onedroid.radiowave.app.player.PlayerRepository
import org.onedroid.radiowave.app.utils.UiText
import org.onedroid.radiowave.app.utils.onError
import org.onedroid.radiowave.app.utils.onSuccess
import org.onedroid.radiowave.data.mappers.toUiText
import org.onedroid.radiowave.domain.Radio
import org.onedroid.radiowave.domain.RadioRepository

class HomeViewModel(
    private val radioRepository: RadioRepository,
    val playerRepository: PlayerRepository
) : ViewModel() {

    private var observeSaveJob: Job? = null
    private var savedStatusJob: Job? = null
    private var searchJob: Job? = null
    private var tuneJob: Job? = null
    private var autoSearchJob: Job? = null

    var radios by mutableStateOf<List<Radio>>(emptyList())
        private set

    val categories = listOf("الكل", "اغاني", "منوعات", "رياضة", "دراما", "قرآن", "اخبار")

    var selectedCategory by mutableStateOf("الكل")
        private set

    val filteredRadios: List<Radio>
        get() = if (selectedCategory == "الكل") radios
        else radios.filter { it.category == selectedCategory }

    var savedRadios by mutableStateOf<List<Radio>>(emptyList())
        private set

    var showFavoritesOnly by mutableStateOf(false)
        private set

    private val navigationRadios: List<Radio>
        get() {
            val source = if (showFavoritesOnly) {
                val savedIds = savedRadios.mapTo(mutableSetOf()) { it.id }
                radios.filter { it.id in savedIds }
            } else filteredRadios
            return source.filter {
                it.playableUrl.isNotBlank() || !it.masperoName.isNullOrBlank()
            }
        }

    var isLoading by mutableStateOf(false)
        private set

    var errorMsg by mutableStateOf<UiText?>(null)
        private set

    var isSearchActive by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    var isSearchLoading by mutableStateOf(false)
        private set

    var searchErrorMsg by mutableStateOf<UiText?>(null)
        private set

    var searchResult by mutableStateOf<List<Radio>>(emptyList())
        private set

    var selectedRadio by mutableStateOf<Radio?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isTuning by mutableStateOf(false)
        private set

    var playerMessage by mutableStateOf<String?>(null)
        private set

    var isAutoSearch by mutableStateOf(false)
        private set

    var isSaved by mutableStateOf(true)
        private set

    var isAboutDialogShowing by mutableStateOf(false)
        private set

    private var catalogLoaded = false

    init {
        getRadios()
        observeSavedRadio()
        observeSearchQuery()
    }

    fun toggleAboutDialog() {
        isAboutDialogShowing = !isAboutDialogShowing
    }

    fun toggleSearch() {
        isSearchActive = !isSearchActive
    }

    fun selectCategory(category: String) {
        if (selectedCategory == category) return
        stopAutoSearch()
        selectedCategory = category
    }

    fun toggleFavoritesOnly() {
        stopAutoSearch()
        showFavoritesOnly = !showFavoritesOnly
    }

    fun play(streamUrl: String) {
        if (streamUrl.isBlank()) return
        isPlaying = true
        playerRepository.play(streamUrl, selectedRadio?.streamReferer, selectedRadio?.name)
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
    }

    fun closePlayer() {
        stopAutoSearch()
        tuneJob?.cancel()
        isTuning = false
        selectedRadio = null
        savedStatusJob?.cancel()
    }

    fun retrySelectedRadio() {
        selectedRadio?.let { tuneStation(it, forceRefresh = true) }
    }

    private fun tuneStation(station: Radio, forceRefresh: Boolean = false) {
        tuneJob?.cancel()
        isTuning = false
        playerMessage = null

        // Use the URL already refreshed during app startup immediately.
        // Re-fetching Maspero on every tap adds a network round-trip and makes
        // station selection feel unresponsive. Refresh only when there is no
        // usable URL or the user explicitly retries.
        if (!forceRefresh && station.playableUrl.isNotBlank()) {
            selectedRadio = station
            play(station.playableUrl)
            return
        }

        isTuning = true
        playerMessage = "جارٍ تحديث رابط البث المباشر…"
        tuneJob = viewModelScope.launch {
            radioRepository.getMEStations()
                .onSuccess { stations ->
                    // Maspero IDs differ from the app catalog; match by Maspero name.
                    val expectedName = station.masperoName?.let(::normalizeStationName)
                    val fresh = stations.firstOrNull { candidate ->
                        candidate.id == station.id ||
                            (!expectedName.isNullOrBlank() &&
                                normalizeStationName(candidate.name) == expectedName)
                    }
                    if (fresh != null && fresh.playableUrl.isNotBlank()) {
                        val updated = station.copy(
                            url = fresh.playableUrl,
                            urlResolved = fresh.playableUrl,
                            homepage = fresh.homepage ?: station.homepage,
                            streamReferer = fresh.streamReferer ?: station.streamReferer
                        )
                        radios = radios.map { current ->
                            if (current.id == station.id) updated else current
                        }
                        selectedRadio = updated
                        playerMessage = null
                        play(updated.playableUrl)
                    } else if (station.playableUrl.isNotBlank()) {
                        // Keep a previously working URL as a fallback, but do not
                        // label the station unavailable just because refresh failed.
                        selectedRadio = station
                        playerMessage = "تعذر تحديث الرابط؛ نجرب الرابط المحفوظ"
                        play(station.playableUrl)
                    } else {
                        playerMessage = "رابط البث غير متاح حاليًا"
                    }
                    isTuning = false
                }
                .onError {
                    isTuning = false
                    if (station.playableUrl.isNotBlank()) {
                        selectedRadio = station
                        playerMessage = "تعذر تحديث الرابط؛ نجرب الرابط المحفوظ"
                        play(station.playableUrl)
                    } else {
                        playerMessage = "تعذر جلب رابط البث. حاول مرة أخرى"
                    }
                }
        }
    }

    private fun normalizeStationName(value: String): String = value.trim()
        .replace("ـ", "")
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace(Regex("\\s+"), "")
        .lowercase()

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun selectedRadio(radio: Radio) {
        stopAutoSearch()
        selectStationInternal(radio)
    }

    private fun selectStationInternal(radio: Radio) {
        val station = radios.firstOrNull { it.id == radio.id } ?: radio
        // Reset immediately so the previous station's favorite state cannot
        // briefly affect the newly selected station while Room emits its value.
        isSaved = false
        observeSavedStatus(station.id)
        selectedRadio = station
        tuneStation(station)
    }

    fun playPrevious() = playRelativeStation(-1)

    fun playNext() = playRelativeStation(1)

    private fun playRelativeStation(direction: Int) {
        // Previous/next follow the same active scope as AUTO scan.
        val available = navigationRadios
        if (available.isEmpty()) return
        val currentIndex = available.indexOfFirst { it.id == selectedRadio?.id }
        val nextIndex = if (currentIndex < 0) {
            if (direction > 0) 0 else available.lastIndex
        } else {
            (currentIndex + direction + available.size) % available.size
        }
        selectedRadio(available[nextIndex])
    }

    fun toggleAutoSearch() {
        if (isAutoSearch) {
            stopAutoSearch()
            return
        }

        val available = navigationRadios
        if (available.isEmpty()) return

        isAutoSearch = true
        autoSearchJob = viewModelScope.launch {
            var index = available.indexOfFirst { it.id == selectedRadio?.id }
                .let { if (it < 0) 0 else it }
            while (isAutoSearch) {
                selectStationInternal(available[index % available.size])
                delay(10_000)
                index++
            }
        }
    }

    private fun stopAutoSearch() {
        isAutoSearch = false
        autoSearchJob?.cancel()
        autoSearchJob = null
    }

    fun toggleSavedRadio(radio: Radio) = viewModelScope.launch {
        if (savedRadios.any { it.id == radio.id }) {
            radioRepository.deleteFromSaved(radio.id)
        } else {
            radioRepository.saveRadio(radio)
        }
    }

    fun refreshStations() {
        getRadios(forceRefresh = true)
    }

    fun getRadios(forceRefresh: Boolean = false) {
        if (isLoading || (catalogLoaded && !forceRefresh)) return
        viewModelScope.launch {
            isLoading = true
            radioRepository.getMEStations()
                .onSuccess { stations ->
                    // Wait for the saved-radio snapshot before merging. This avoids
                    // losing a previously working URL when the catalog or Maspero
                    // feed temporarily returns an empty stream URL.
                    val savedSnapshot = radioRepository.getSavedRadios().first()
                    val savedById = savedSnapshot.associateBy { it.id }

                    val mergedStations = stations.map { station ->
                        val savedUrl = savedById[station.id]?.playableUrl
                            ?.takeIf { it.isNotBlank() }
                        val selectedUrl = station.playableUrl.takeIf { it.isNotBlank() }
                            ?: savedUrl

                        station.copy(
                            url = selectedUrl.orEmpty(),
                            urlResolved = selectedUrl ?: station.urlResolved
                        )
                    }

                    radios = mergedStations
                    // Keep the current selection in sync with updated metadata,
                    // and move to the first available station if it was removed.
                    selectedRadio = selectedRadio?.let { current ->
                        mergedStations.firstOrNull { it.id == current.id }
                    } ?: mergedStations.firstOrNull()

                    // Persist only valid refreshed URLs. Never replace a working
                    // saved stream with a blank URL if the Maspero feed is unavailable.
                    savedSnapshot.forEach { saved ->
                        val refreshed = mergedStations.firstOrNull { it.id == saved.id }
                        if (refreshed != null &&
                            (
                                (refreshed.playableUrl.isNotBlank() &&
                                    refreshed.playableUrl != saved.playableUrl) ||
                                refreshed.name != saved.name ||
                                refreshed.frequency != saved.frequency ||
                                refreshed.category != saved.category
                            )
                        ) {
                            radioRepository.saveRadio(refreshed)
                        }
                    }

                    catalogLoaded = true
                    errorMsg = null
                    isLoading = false
                }
                .onError { error ->
                    errorMsg = error.toUiText()
                    isLoading = false
                }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            snapshotFlow { searchQuery to radios }
                .distinctUntilChanged()
                .debounce(250L)
                .collect { (query, stationList) ->
                    if (query.isBlank()) {
                        searchErrorMsg = null
                        searchResult = emptyList()
                    } else {
                        searchResult = stationList.filter { station ->
                            station.name.contains(query, ignoreCase = true) ||
                                station.frequency.orEmpty().contains(query, ignoreCase = true) ||
                                station.category.orEmpty().contains(query, ignoreCase = true)
                        }
                    }
                }
        }
    }

    private fun observeSavedRadio() {
        observeSaveJob?.cancel()
        observeSaveJob = radioRepository.getSavedRadios().onEach { radios ->
            savedRadios = radios
        }.launchIn(viewModelScope)
    }

    private fun observeSavedStatus(radioId: String) {
        savedStatusJob?.cancel()
        savedStatusJob = viewModelScope.launch {
            radioRepository.isSaved(radioId).collect { saved ->
                this@HomeViewModel.isSaved = saved
            }
        }
    }

    fun saveRadio(radio: Radio) {
        toggleSavedRadio(radio)
    }

    override fun onCleared() {
        super.onCleared()
        playerRepository.onCleared()
    }
}
