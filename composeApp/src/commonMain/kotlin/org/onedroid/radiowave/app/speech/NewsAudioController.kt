package org.onedroid.radiowave.app.speech

expect class NewsAudioController(context: Any) {
    fun play(uri: String, maxDurationMs: Long?, onDone: () -> Unit)
    fun stop()
}
