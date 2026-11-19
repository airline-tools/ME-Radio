package org.onedroid.radiowave.app.player

import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Shared request context is set by PlayerController immediately before it
 * sends a new MediaItem to the service. Both classes run in the app process.
 */
internal object RadioPlaybackConfig {
    @Volatile
    var referer: String? = null
}

class RadioPlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val mainHandler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var retryRunnable: Runnable? = null

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

        // Create a fresh HTTP data source for each media item so the current
        // station's Referer is applied before ExoPlayer opens its stream.
        val dataSourceFactory = DataSource.Factory {
            val sourceReferer = RadioPlaybackConfig.referer
                ?.takeIf { it.startsWith("https://") }

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
                addListener(playerListener)
            }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

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
        // Keep the foreground service alive while audio is intended to play.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        retryRunnable?.let(mainHandler::removeCallbacks)
        if (::mediaSession.isInitialized) mediaSession.release()
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
