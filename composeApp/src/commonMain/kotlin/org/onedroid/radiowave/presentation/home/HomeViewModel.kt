package org.onedroid.radiowave.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var searchJob: Job? = null

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
        selectedCategory = category
    }

    fun play(streamUrl: String) {
        if (streamUrl.isBlank()) return
        isPlaying = true
        playerRepository.play(streamUrl)
    }

    fun togglePlayPause() {
        isPlaying = !isPlaying
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun selectedRadio(radio: Radio) {
        if (radio.url.isBlank()) return
        observeSavedStatus(radio.id)
        selectedRadio = radio
    }

    fun getRadios() {
        if (isLoading || catalogLoaded) return
        viewModelScope.launch {
            isLoading = true
            radioRepository.getMEStations()
                .onSuccess { stations ->
                    radios = stations
                    // Replace saved copies with refreshed catalog URLs when available.
                    savedRadios = savedRadios.map { saved ->
                        stations.firstOrNull { it.id == saved.id } ?: saved
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

    private fun observeSavedStatus(radioId: String) = viewModelScope.launch {
        radioRepository.isSaved(radioId).collect { isSaved ->
            this@HomeViewModel.isSaved = isSaved
        }
    }

    fun saveRadio(radio: Radio) = viewModelScope.launch {
        if (isSaved) {
            radioRepository.deleteFromSaved(radio.id)
        } else {
            radioRepository.saveRadio(radio)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerRepository.onCleared()
    }
}
