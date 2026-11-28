package org.onedroid.radiowave.app.speech

expect class NewsAudioController(context: Any) {
    fun play(bytes: ByteArray, maxDurationMs: Long?, onDone: () -> Unit)
    fun stop()
}
