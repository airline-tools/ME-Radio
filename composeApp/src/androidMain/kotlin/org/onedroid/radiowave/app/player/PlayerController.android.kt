package org.onedroid.radiowave.app.player

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.app.UiModeManager
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import org.jetbrains.compose.resources.stringResource
import org.onedroid.radiowave.R
import org.onedroid.radiowave.app.theme.small
import org.onedroid.radiowave.app.theme.thin
import org.onedroid.radiowave.domain.Radio
import org.onedroid.radiowave.presentation.home.components.BookingwepRadioConsole
import org.onedroid.radiowave.presentation.home.components.AnimatedRadioCoverImage
import org.onedroid.radiowave.presentation.home.components.PlayerFullWidthView
import org.onedroid.radiowave.presentation.home.components.RadioActionButtons
import org.onedroid.radiowave.presentation.home.components.RadioDetails
import radiowave.composeapp.generated.resources.Res
import radiowave.composeapp.generated.resources.broken_image_radio
import radiowave.composeapp.generated.resources.detecting
import radiowave.composeapp.generated.resources.now_playing
import radiowave.composeapp.generated.resources.unable_to_detect

actual class PlayerController(private val context: Context) : PlayerRepository {

    private data class PendingPlayback(
        val url: String,
        val referer: String?,
        val stationName: String?
    )

    // The actual ExoPlayer lives in RadioPlaybackService. This controller is
    // only the UI connection and can be released without stopping the audio.
    private var player: MediaController? = null
    private var playerFuture: ListenableFuture<MediaController>? = null
    private var pendingPlayback: PendingPlayback? = null
    private var stationQueue: List<Radio> = emptyList()
    private var currentStationId: String? = null
    private val mainExecutor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _metadataState = MutableStateFlow<MetadataState>(MetadataState.Detecting)
    private val _isPlaying = MutableStateFlow(false)
    private val _currentPosition = MutableStateFlow(0L)
    private val metadataState = _metadataState.asStateFlow()
    private val isPlaying = _isPlaying.asStateFlow()
    private val currentPosition = _currentPosition.asStateFlow()
    private var metadataTimeoutJob: Job? = null
    private var positionUpdateJob: Job? = null

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
        onCloseClick: () -> Unit
    ) {
        val isPlayingState by isPlaying.collectAsState()
        val useStationGrid = remember { isTvOrCarDevice() }

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
            onFavoritesClick = onFavoritesClick,
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
            statusContent = { PlayerStatusIndicator(onRetryClick) },
            useStationGrid = useStationGrid
        )
    }

    private fun isTvOrCarDevice(): Boolean {
        val packageManager = context.packageManager
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val isAutomotive = packageManager.hasSystemFeature("android.hardware.type.automotive")
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isCarMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        // Many aftermarket Android head units identify as ordinary Android
        // devices. Their wide, short landscape display is a useful fallback.
        val config = context.resources.configuration
        val isHeadUnitLayout =
            config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            config.screenWidthDp >= 700 &&
            config.screenHeightDp <= 650
        return isTv || isAutomotive || isCarMode || isHeadUnitLayout
    }

    override fun updateStationQueue(stations: List<Radio>, currentStationId: String?) {
        val playable = stations.filter {
            it.playableUrl.isNotBlank() || !it.masperoName.isNullOrBlank()
        }
        stationQueue = playable

        // Updating a category/favorites filter must never start a station.
        // Only rebuild the notification queue when a stream is already loaded.
        val connected = player ?: return
        val playingItem = connected.currentMediaItem ?: return
        val items = playable.map { station ->
            MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.playableUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist("ME Radio")
                        .setExtras(android.os.Bundle().apply {
                            putString("me_radio_referer", station.streamReferer)
                        })
                        .build()
                )
                .build()
        }.toMutableList()

        // Keep the current MediaItem alive. Replacing the complete playlist
        // with setMediaItems() causes live streams to reconnect on every
        // category change, even when the selected station did not change.
        val currentId = playingItem.mediaId
        val targetCurrentIndex = items.indexOfFirst { it.mediaId == currentId }
        val currentQueueIndex = connected.currentMediaItemIndex

        if (targetCurrentIndex < 0) {
            // Keep the playing station as an anchor, then queue the new scope.
            items.add(0, playingItem)
        } else {
            // Preserve the exact active item, including its URL and Referer.
            items[targetCurrentIndex] = playingItem
        }

        val beforeCurrent = items.take(if (targetCurrentIndex < 0) 0 else targetCurrentIndex)
        val afterCurrent = items.drop(if (targetCurrentIndex < 0) 1 else targetCurrentIndex + 1)

        // Remove and insert only the items around the active item. ExoPlayer
        // keeps the current live stream and its playback position untouched.
        connected.removeMediaItems(currentQueueIndex + 1, connected.mediaItemCount)
        connected.removeMediaItems(0, currentQueueIndex)
        if (beforeCurrent.isNotEmpty()) {
            connected.addMediaItems(0, beforeCurrent)
        }
        if (afterCurrent.isNotEmpty()) {
            connected.addMediaItems(beforeCurrent.size + 1, afterCurrent)
        }
    }

    override fun play(audioUrl: String, referer: String?, stationName: String?) {
        if (audioUrl.isBlank()) return
        pendingPlayback = PendingPlayback(audioUrl, referer, stationName)
        _metadataState.value = MetadataState.Detecting

        val connectedPlayer = player
        if (connectedPlayer != null) {
            pendingPlayback = null
            startPlayback(connectedPlayer, audioUrl, referer, stationName)
        } else {
            connectToPlaybackService()
        }
    }

    private fun connectToPlaybackService() {
        if (player != null || playerFuture != null) return

        try {
            // This is invoked only after the user selects Play, while the app
            // is visible. MediaSessionService promotes itself for playback.
            context.startService(Intent(context, RadioPlaybackService::class.java))

            val token = SessionToken(
                context,
                ComponentName(context, RadioPlaybackService::class.java)
            )
            val future = MediaController.Builder(context, token).buildAsync()
            playerFuture = future

            future.addListener({
                try {
                    val connected = future.get()
                    player = connected
                    connected.addListener(playerListener)
                    _isPlaying.value = connected.isPlaying
                    _currentPosition.value = connected.currentPosition

                    val pending = pendingPlayback
                    if (pending != null) {
                        pendingPlayback = null
                        startPlayback(
                            connected,
                            pending.url,
                            pending.referer,
                            pending.stationName
                        )
                    }
                } catch (_: Exception) {
                    playerFuture = null
                    _isPlaying.value = false
                }
            }, mainExecutor)
        } catch (_: Exception) {
            playerFuture = null
            _isPlaying.value = false
        }
    }

    private fun startPlayback(
        connectedPlayer: MediaController,
        audioUrl: String,
        referer: String?,
        stationName: String?
    ) {
        // The service's data-source factory reads this value when it creates
        // the next stream request, preserving Maspero's Referer behavior.
        RadioPlaybackConfig.referer = referer?.takeIf { it.startsWith("https://") }

        val queuedItems = stationQueue.map { station ->
            MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.playableUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(station.name)
                        .setArtist("ME Radio")
                        .setExtras(android.os.Bundle().apply {
                            putString("me_radio_referer", station.streamReferer)
                        })
                        .build()
                )
                .build()
        }
        val selectedIndex = stationQueue.indexOfFirst { it.id == currentStationId }
            .takeIf { it >= 0 }
            ?: queuedItems.indexOfFirst { it.localConfiguration?.uri?.toString() == audioUrl }
                .takeIf { it >= 0 }
        if (queuedItems.isNotEmpty() && selectedIndex != null) {
            connectedPlayer.setMediaItems(queuedItems, selectedIndex, 0L)
        } else {
            val mediaItem = MediaItem.Builder()
                .setUri(audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(stationName?.takeIf { it.isNotBlank() } ?: "ME Radio")
                        .setArtist("ME Radio")
                        .setExtras(android.os.Bundle().apply {
                            putString("me_radio_referer", referer)
                        })
                        .build()
                )
                .build()
            connectedPlayer.setMediaItem(mediaItem)
        }
        _metadataState.value = MetadataState.Detecting
        connectedPlayer.prepare()
        connectedPlayer.play()
    }

    private fun pauseResume() {
        val connectedPlayer = player ?: return
        if (connectedPlayer.isPlaying) {
            connectedPlayer.pause()
        } else {
            connectedPlayer.play()
        }
        vibrateDevice()
    }

    private fun startPositionTracking() {
        positionUpdateJob?.cancel()
        positionUpdateJob = coroutineScope.launch {
            while (isActive) {
                val connectedPlayer = player
                if (connectedPlayer?.isPlaying == true) {
                    _currentPosition.value = connectedPlayer.currentPosition
                }
                delay(50L)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            metadataTimeoutJob?.cancel()

            val artist = mediaMetadata.artist?.toString() ?: ""
            val title = mediaMetadata.title?.toString() ?: ""

            if (title.isNotEmpty()) {
                val content = if (artist.isNotEmpty()) "$artist - $title" else title
                _metadataState.value = MetadataState.Available(content)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    metadataTimeoutJob?.cancel()
                    _metadataState.value = MetadataState.NotAvailable
                }

                Player.STATE_READY -> {
                    if (_metadataState.value is MetadataState.Detecting) {
                        startMetadataTimeout()
                    }
                }

                Player.STATE_BUFFERING -> {
                    _metadataState.value = MetadataState.Detecting
                }
            }
        }

        override fun onIsPlayingChanged(isNowPlaying: Boolean) {
            _isPlaying.value = isNowPlaying
            if (isNowPlaying) {
                if (_metadataState.value is MetadataState.Detecting &&
                    (metadataTimeoutJob == null || metadataTimeoutJob?.isCompleted == true)
                ) {
                    startMetadataTimeout()
                }

                if (positionUpdateJob?.isActive != true) {
                    startPositionTracking()
                }
            }
        }
    }

    init {
        startPositionTracking()
    }

    private fun startMetadataTimeout() {
        metadataTimeoutJob?.cancel()
        metadataTimeoutJob = coroutineScope.launch {
            delay(5000)
            if (_metadataState.value is MetadataState.Detecting) {
                _metadataState.value = MetadataState.NotAvailable
            }
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

    private fun volumeUp() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (currentVolume + 1).coerceAtMost(maxVolume)
        adjustVolumeWithUi(newVolume)
        vibrateDevice()
    }

    private fun volumeDown() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (currentVolume - 1).coerceAtLeast(0)
        adjustVolumeWithUi(newVolume)
        vibrateDevice()
    }

    private fun adjustVolumeWithUi(newVolume: Int) {
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume, 0
        )
    }

    private fun vibrateDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    @SuppressLint("DefaultLocale")
    @Composable
    private fun PlayerStatusIndicator(onRetryClick: () -> Unit) {

        val playbackState = remember { mutableIntStateOf(player?.playbackState ?: Player.STATE_IDLE) }
        val hasError = remember { mutableStateOf(false) }
        val currentVolumeState = remember { mutableIntStateOf(getCurrentVolume()) }
        val position by currentPosition.collectAsState()
        val isPlayingState by isPlaying.collectAsState()

        LaunchedEffect(Unit) {
            while (true) {
                currentVolumeState.intValue = getCurrentVolume()
                delay(1000) // reduced polling frequency
            }
        }

        DisposableEffect(player) {
            val connectedPlayer = player
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    playbackState.intValue = state
                    if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                        hasError.value = false
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    hasError.value = true
                }
            }

            connectedPlayer?.addListener(listener)
            onDispose { connectedPlayer?.removeListener(listener) }
        }

        val animatedVolumePercent by animateFloatAsState(
            targetValue = currentVolumeState.intValue / maxVolume.toFloat(),
            animationSpec = tween(durationMillis = 300),
            label = "volumeAnim"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (hasError.value) {
                OfflineIndicator(4.dp)
            } else if (playbackState.intValue == Player.STATE_BUFFERING) {
                Text("CONNECTING", color = Color(0xFFE8CC8B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            } else if (playbackState.intValue == Player.STATE_READY || isPlayingState) {
                LiveIndicator(4.dp)
            } else {
                Text("READY", color = Color(0xFFE8CC8B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            LinearProgressIndicator(
                progress = { animatedVolumePercent },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFC5A059),
                trackColor = Color(0xFF252B34),
            )

            when {
                hasError.value -> TextButton(onClick = onRetryClick) {
                    Text("تعذر التشغيل — تحديث رابط البث", color = Color.Red)
                }
                playbackState.intValue == Player.STATE_BUFFERING -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp)
                )

                playbackState.intValue == Player.STATE_READY || isPlayingState -> {
                    // Live radio has no meaningful elapsed-time counter.
                    Spacer(modifier = Modifier.width(65.dp))
                }

                else -> Text(stringResource(Res.string.now_playing))
            }
        }
    }

    @Composable
    fun LiveIndicator(extraSmall: Dp = 4.dp) {
        var visible by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            while (true) {
                visible = !visible
                delay(500)
            }
        }

        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 300),
            label = "liveIndicatorAlpha"
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(100))
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_circle),
                    contentDescription = "Blinking Dot",
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(Color.Red)
                )
            }
            Spacer(modifier = Modifier.width(extraSmall))
            Text(text = "Live")
        }
    }

    @Composable
    fun OfflineIndicator(extraSmall: Dp = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(100))
                    .background(Color.Gray)
            )
            Spacer(modifier = Modifier.width(extraSmall))
            Text(text = "Offline", color = Color.Gray)
        }
    }

    private fun getCurrentVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    override fun onCleared() {
        // Keep the MediaController connection and its lightweight observers alive.
        // The MediaSessionService owns ExoPlayer and must continue playing after
        // the Compose ViewModel or screen is cleared.
    }
}

sealed class MetadataState {
    data object Detecting : MetadataState()
    data object NotAvailable : MetadataState()
    data class Available(val content: String) : MetadataState()
}