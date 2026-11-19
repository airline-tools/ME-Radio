package org.onedroid.radiowave.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.rememberAsyncImagePainter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.stringResource
import org.onedroid.radiowave.app.theme.small
import org.onedroid.radiowave.app.theme.thin
import org.onedroid.radiowave.domain.Radio
import org.onedroid.radiowave.presentation.home.components.BookingwepRadioConsole
import org.onedroid.radiowave.presentation.home.components.AnimatedRadioCoverImage
import org.onedroid.radiowave.presentation.home.components.PlayerFullWidthView
import org.onedroid.radiowave.presentation.home.components.RadioActionButtons
import org.onedroid.radiowave.presentation.home.components.RadioDetails
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentTime
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import radiowave.composeapp.generated.resources.Res
import radiowave.composeapp.generated.resources.broken_image_radio
import radiowave.composeapp.generated.resources.detecting
import radiowave.composeapp.generated.resources.now_playing
import radiowave.composeapp.generated.resources.unable_to_detect
import kotlin.Any
import kotlin.Int
import kotlin.OptIn
import kotlin.String
import kotlin.let
import kotlin.math.max
import kotlin.math.min

actual class PlayerController : PlayerRepository {

    private val player = AVPlayer()
    private val _metadataState = MutableStateFlow<MetadataState>(MetadataState.Detecting)
    private val _isPlaying = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)

    private var timeObserver: Any? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val metadataState = _metadataState.asStateFlow()
    private val isPlaying = _isPlaying.asStateFlow()
    private val currentPosition = _currentPosition.asStateFlow()

    override fun updateStationQueue(stations: List<Radio>, currentStationId: String?) = Unit

    override fun play(audioUrl: String, referer: String?, stationName: String?) {
        val url = NSURL(string = audioUrl)
        val item = AVPlayerItem(uRL = url)
        player.replaceCurrentItemWithPlayerItem(item)
        player.play()
        _isPlaying.value = true
        startPositionTracking()
        startMetadataDetection(item)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun startPositionTracking() {
        val interval = CMTimeMakeWithSeconds(0.05, 600)
        timeObserver?.let { player.removeTimeObserver(it) }

        timeObserver = player.addPeriodicTimeObserverForInterval(interval, queue = null) {
            val timeSeconds = CMTimeGetSeconds(player.currentTime())
            _currentPosition.value = (timeSeconds * 1000).toLong()
        }
    }

    private fun startMetadataDetection(item: AVPlayerItem) {
        _metadataState.value = MetadataState.NotAvailable
    }

    override fun onCleared() {
        player.pause()
        timeObserver?.let { player.removeTimeObserver(it) }
        coroutineScope.cancel()
    }

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
    ) {
        val isPlayingState by isPlaying.collectAsState()
        BookingwepRadioConsole(
            radio = radio,
            stations = stations,
            savedStations = savedStations,
            isSaved = isSaved,
            isPlaying = isPlayingState,
            isTuning = isTuning,
            playerMessage = playerMessage,
            isAutoSearch = isAutoSearch,
            categories = categories,
            selectedCategory = selectedCategory,
            onCategoryClick = onCategoryClick,
            onCloseClick = onCloseClick,
            onPlayPauseClick = { pauseResume() },
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            onAutoSearchClick = onAutoSearchClick,
            onSaveClick = onSaveClick,
            onStationFavoriteClick = onStationFavoriteClick,
            onStationClick = onStationClick,
            onShareClick = onShareClick,
            onWebpageClick = onWebpageClick,
            statusContent = { NowPlayingIndicator() }
        )
    }

    private fun pauseResume() {
        if (player.rate.toDouble() == 0.0) {
            player.play()
            _isPlaying.value = true
        } else {
            player.pause()
            _isPlaying.value = false
        }
    }

    @Composable
    private fun NowPlayingIndicator() {
        val state by metadataState.collectAsState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = thin)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(thin)
            ) {
                Text(
                    text = stringResource(Res.string.now_playing),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally()
                ) {
                    Text(
                        modifier = Modifier.basicMarquee(
                            initialDelayMillis = 0,
                            iterations = Int.MAX_VALUE,
                        ),
                        text = when (state) {
                            is MetadataState.Detecting -> stringResource(Res.string.detecting)
                            is MetadataState.NotAvailable -> stringResource(Res.string.unable_to_detect)
                            is MetadataState.Available -> (state as MetadataState.Available).content
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

}

sealed class MetadataState {
    data object Detecting : MetadataState()
    data object NotAvailable : MetadataState()
    data class Available(val content: String) : MetadataState()
}