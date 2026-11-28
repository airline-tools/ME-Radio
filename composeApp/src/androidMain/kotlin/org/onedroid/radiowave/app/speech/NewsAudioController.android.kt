package org.onedroid.radiowave.app.speech

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

actual class NewsAudioController actual constructor(context: Any) {
    private val appContext = (context as Context).applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var completion: (() -> Unit)? = null
    private var clipStopper: Runnable? = null

    actual fun play(uri: String, maxDurationMs: Long?, onDone: () -> Unit) {
        stop()
        completion = onDone
        val nextPlayer = MediaPlayer()
        player = nextPlayer
        nextPlayer.setOnPreparedListener { prepared ->
            if (player !== prepared) {
                prepared.release()
                return@setOnPreparedListener
            }
            prepared.start()
            if (maxDurationMs != null && maxDurationMs > 0L) {
                val stopper = Runnable {
                    if (player === prepared) finish(prepared)
                }
                clipStopper = stopper
                handler.postDelayed(stopper, maxDurationMs)
            }
        }
        nextPlayer.setOnCompletionListener { completed ->
            if (player === completed) finish(completed)
        }
        nextPlayer.setOnErrorListener { failed, _, _ ->
            if (player === failed) finish(failed)
            true
        }
        try {
            nextPlayer.setDataSource(appContext, Uri.parse(uri))
            nextPlayer.prepareAsync()
        } catch (_: Throwable) {
            finish(nextPlayer)
        }
    }

    private fun finish(finishedPlayer: MediaPlayer) {
        if (player !== finishedPlayer) return
        clipStopper?.let(handler::removeCallbacks)
        clipStopper = null
        player = null
        runCatching { finishedPlayer.stop() }
        runCatching { finishedPlayer.release() }
        val callback = completion
        completion = null
        callback?.invoke()
    }

    actual fun stop() {
        clipStopper?.let(handler::removeCallbacks)
        clipStopper = null
        completion = null
        val current = player
        player = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }
}
