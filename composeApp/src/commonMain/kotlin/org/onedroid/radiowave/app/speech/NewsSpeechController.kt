package org.onedroid.radiowave.app.speech

expect class NewsSpeechController(context: Any) {
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
}
