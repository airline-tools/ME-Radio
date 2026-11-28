package org.onedroid.radiowave.app.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.play
import platform.AVFAudio.stop
import platform.Foundation.NSObject
import platform.Foundation.NSURL

actual class NewsAudioController actual constructor(context: Any) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: AVAudioPlayer? = null
    private var completion: (() -> Unit)? = null
    private var clipJob: Job? = null

    private val audioDelegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            if (this@NewsAudioController.player === player) finish(player)
        }
    }

    actual fun play(uri: String, maxDurationMs: Long?, onDone: () -> Unit) {
        stop()
        completion = onDone
        val url = NSURL.URLWithString(uri) ?: run {
            finishWithoutPlayer()
            return
        }
        val nextPlayer = AVAudioPlayer(uRL = url, error = null)
        if (nextPlayer == null) {
            finishWithoutPlayer()
            return
        }
        player = nextPlayer
        nextPlayer.delegate = audioDelegate
        if (!nextPlayer.prepareToPlay() || !nextPlayer.play()) {
            finish(nextPlayer)
            return
        }
        if (maxDurationMs != null && maxDurationMs > 0L) {
            clipJob = scope.launch {
                delay(maxDurationMs)
                if (this@NewsAudioController.player === nextPlayer) finish(nextPlayer)
            }
        }
    }

    private fun finish(finishedPlayer: AVAudioPlayer) {
        if (player !== finishedPlayer) return
        clipJob?.cancel()
        clipJob = null
        player = null
        finishedPlayer.delegate = null
        finishedPlayer.stop()
        val callback = completion
        completion = null
        callback?.invoke()
    }

    private fun finishWithoutPlayer() {
        val callback = completion
        completion = null
        callback?.invoke()
    }

    actual fun stop() {
        clipJob?.cancel()
        clipJob = null
        completion = null
        val current = player
        player = null
        if (current != null) {
            current.delegate = null
            current.stop()
        }
    }
}
