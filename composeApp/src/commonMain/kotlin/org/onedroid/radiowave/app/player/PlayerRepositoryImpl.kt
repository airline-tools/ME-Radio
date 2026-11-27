package org.onedroid.radiowave.app.player

import androidx.compose.runtime.Composable
import org.onedroid.radiowave.domain.Radio

class PlayerRepositoryImpl(
    private val playerController: PlayerController
) : PlayerRepository {
    override fun play(audioUrl: String, referer: String?, stationName: String?) {
        playerController.play(audioUrl, referer, stationName)
    }

    override fun updateStationQueue(stations: List<Radio>, currentStationId: String?) {
        playerController.updateStationQueue(stations, currentStationId)
    }

    override fun pauseForAnnouncement(): Boolean = playerController.pauseForAnnouncement()

    override fun resumeAfterAnnouncement(shouldResume: Boolean) =
        playerController.resumeAfterAnnouncement(shouldResume)

    @Composable
    override fun PLayerUI(
        radio: Radio,
        stations: List<Radio>,
        savedStations: List<Radio>,
        categories: List<String>,
        selectedCategory: String,
        isSaved: Boolean,
        isTuning: Boolean,
        playerMessage: String?,
        isAutoSearch: Boolean,
        onCategoryClick: (String) -> Unit,
        onFavoritesClick: () -> Unit,
        onStationClick: (Radio) -> Unit,
        onStationFavoriteClick: (Radio) -> Unit,
        onPreviousClick: () -> Unit,
        onNextClick: () -> Unit,
        onAutoSearchClick: () -> Unit,
        onSaveClick: () -> Unit,
        onWebpageClick: () -> Unit,
        onShareClick: () -> Unit,
        onRetryClick: () -> Unit,
        onCloseClick: () -> Unit,
        onNewsClick: () -> Unit,
        onMatchesClick: () -> Unit
    ) {
        playerController.PLayerUI(
            radio = radio,
            stations = stations,
            savedStations = savedStations,
            categories = categories,
            selectedCategory = selectedCategory,
            isSaved = isSaved,
            isTuning = isTuning,
            playerMessage = playerMessage,
            isAutoSearch = isAutoSearch,
            onCategoryClick = onCategoryClick,
            onFavoritesClick = onFavoritesClick,
            onStationClick = onStationClick,
            onStationFavoriteClick = onStationFavoriteClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            onAutoSearchClick = onAutoSearchClick,
            onSaveClick = onSaveClick,
            onWebpageClick = onWebpageClick,
            onShareClick = onShareClick,
            onRetryClick = onRetryClick,
            onCloseClick = onCloseClick,
            onNewsClick = onNewsClick,
            onMatchesClick = onMatchesClick
        )
    }

    override fun onCleared() {
        playerController.onCleared()
    }
}
