package org.onedroid.radiowave.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

actual class NewsSpeechController actual constructor(context: Any) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var completion: (() -> Unit)? = null
    private var pendingText: String? = null
    private var ready = false
    private val engine = TextToSpeech((context as Context).applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val language = engineSafeLanguage()
            engine.setLanguage(language)
            engine.setSpeechRate(1.0f)
            pendingText?.let { text ->
                pendingText = null
                speakNow(text)
            }
        }
    }

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    val callback = completion
                    completion = null
                    callback?.invoke()
                }
            }

            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    val callback = completion
                    completion = null
                    callback?.invoke()
                }
            }
        })
    }

    fun speak(text: String, onDone: () -> Unit) {
        stop()
        completion = onDone
        if (!ready) {
            pendingText = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        pendingText = null
        completion = null
        if (ready) engine.stop()
    }

    private fun engineSafeLanguage(): Locale {
        val arabicEgypt = Locale("ar", "EG")
        val result = engine.setLanguage(arabicEgypt)
        return if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) Locale("ar") else arabicEgypt
    }

    private companion object {
        const val UTTERANCE_ID = "me-radio-news"
    }
}
