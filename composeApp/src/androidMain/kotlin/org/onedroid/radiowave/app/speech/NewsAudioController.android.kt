package org.onedroid.radiowave.app.speech

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

actual class NewsAudioController actual constructor(context: Any) {
    private val appContext = (context as Context).applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var completion: (() -> Unit)? = null
    private var clipStopper: Runnable? = null
    private var audioFile: File? = null

    actual fun play(bytes: ByteArray, maxDurationMs: Long?, onDone: () -> Unit) {
        stop()
        completion = onDone
        if (bytes.isEmpty()) {
            Log.e("NewsAudioController", "Bulletin MP3 resource is empty")
            finishWithoutPlayer()
            return
        }
        try {
            val file = File.createTempFile("news-bulletin-", ".mp3", appContext.cacheDir)
            file.writeBytes(bytes)
            audioFile = file
            val nextPlayer = MediaPlayer()
            player = nextPlayer
            nextPlayer.setOnPreparedListener { prepared ->
                if (player !== prepared) {
                    prepared.release()
                    return@setOnPreparedListener
                }
                prepared.start()
                Log.d("NewsAudioController", "Bulletin audio started (${bytes.size} bytes)")
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
            nextPlayer.setOnErrorListener { failed, what, extra ->
                Log.e("NewsAudioController", "MP3 playback error: what=$what extra=$extra")
                if (player === failed) finish(failed)
                true
            }
            nextPlayer.setDataSource(file.absolutePath)
            nextPlayer.prepareAsync()
        } catch (error: Throwable) {
            Log.e("NewsAudioController", "Unable to play bulletin MP3", error)
            finishWithoutPlayer()
        }
    }

    private fun finish(finishedPlayer: MediaPlayer) {
        if (player !== finishedPlayer) return
        clipStopper?.let(handler::removeCallbacks)
        clipStopper = null
        player = null
        runCatching { finishedPlayer.stop() }
        runCatching { finishedPlayer.release() }
        audioFile?.delete()
        audioFile = null
        val callback = completion
        completion = null
        callback?.invoke()
    }

    private fun finishWithoutPlayer() {
        val failedPlayer = player
        player = null
        if (failedPlayer != null) {
            runCatching { failedPlayer.stop() }
            runCatching { failedPlayer.release() }
        }
        audioFile?.delete()
        audioFile = null
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
        audioFile?.delete()
        audioFile = null
    }
}
