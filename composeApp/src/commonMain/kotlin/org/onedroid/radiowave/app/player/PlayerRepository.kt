package org.onedroid.radiowave.app.player

import androidx.compose.runtime.Composable
import org.onedroid.radiowave.domain.Radio

interface PlayerRepository {
    fun play(audioUrl: String, referer: String? = null)

    @Composable
    fun PLayerUI(
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
        onStationClick: (Radio) -> Unit,
        onStationFavoriteClick: (Radio) -> Unit,
        onPreviousClick: () -> Unit,
        onNextClick: () -> Unit,
        onAutoSearchClick: () -> Unit,
        onSaveClick: () -> Unit,
        onWebpageClick: () -> Unit,
        onShareClick: () -> Unit,
        onRetryClick: () -> Unit,
        onCloseClick: () -> Unit
    )

    fun onCleared()
}
