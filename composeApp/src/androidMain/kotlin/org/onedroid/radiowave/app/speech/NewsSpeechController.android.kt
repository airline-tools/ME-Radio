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
    private var utteranceSequence = 0
    private var activeUtteranceId: String? = null
    private lateinit var engine: TextToSpeech

    init {
        engine = TextToSpeech((context as Context).applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val arabic = Locale("ar", "EG")
                val languageStatus = engine.setLanguage(arabic)
                if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                    languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine.setLanguage(Locale("ar"))
                }
                engine.setSpeechRate(1.0f)
                pendingText?.let { text ->
                    pendingText = null
                    speakNow(text)
                }
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId == activeUtteranceId) {
                        activeUtteranceId = null
                        val callback = completion
                        completion = null
                        callback?.invoke()
                    }
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

    actual fun speak(text: String, onDone: () -> Unit) {
        stop()
        completion = onDone
        if (!ready) {
            pendingText = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        utteranceSequence += 1
        val utteranceId = "me-radio-news-$utteranceSequence"
        activeUtteranceId = utteranceId
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    actual fun stop() {
        pendingText = null
        completion = null
        activeUtteranceId = null
        if (ready) engine.stop()
    }

}
