package org.onedroid.radiowave.app.player

import androidx.compose.runtime.Composable
import org.onedroid.radiowave.domain.Radio

interface PlayerRepository {
    fun play(audioUrl: String, referer: String? = null)
    @Composable
    fun PLayerUI(
        radio: Radio,
        isSaved: Boolean,
        onSaveClick: () -> Unit,
        onWebpageClick: () -> Unit,
        onShareClick: () -> Unit,
        onRetryClick: () -> Unit,
        onCloseClick: () -> Unit
    )
    fun onCleared()
}