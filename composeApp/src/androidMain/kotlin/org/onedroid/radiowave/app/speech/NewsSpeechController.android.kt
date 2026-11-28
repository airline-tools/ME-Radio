package org.onedroid.radiowave.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
                configureArabicVoice()
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
                    if (utteranceId == activeUtteranceId) {
                        activeUtteranceId = null
                        val callback = completion
                        completion = null
                        callback?.invoke()
                    }
                }
            }
        })
    }

    /**
     * Prefer a high-quality Egyptian Arabic voice already available in the
     * selected system TTS engine. Fall back to another Arabic voice, then to
     * the engine's normal language selection if no explicit voice is usable.
     */
    private fun configureArabicVoice() {
        val egyptianArabic = Locale("ar", "EG")
        val voices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())

        val bestArabicVoice = voices
            .asSequence()
            .filter { it.locale.language.equals("ar", ignoreCase = true) }
            .filterNot { voice ->
                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true
            }
            .sortedWith(
                compareBy<Voice> { if (it.locale.country.equals("EG", ignoreCase = true)) 0 else 1 }
                    .thenBy { if (it.isNetworkConnectionRequired) 1 else 0 }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
            )
            .firstOrNull()

        val languageStatus = engine.setLanguage(egyptianArabic)
        if (bestArabicVoice != null) {
            val voiceStatus = runCatching { engine.setVoice(bestArabicVoice) }
                .getOrDefault(TextToSpeech.ERROR)
            if (voiceStatus == TextToSpeech.SUCCESS) {
                // Slightly calmer delivery for longer news bulletins.
                engine.setSpeechRate(0.94f)
                engine.setPitch(1.0f)
                return
            }
        }

        if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.setLanguage(Locale("ar"))
        }
        engine.setSpeechRate(0.94f)
        engine.setPitch(1.0f)
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
        val cleanText = text.replace(Regex("\\s+"), " ").trim()
        if (cleanText.isEmpty()) {
            completion?.invoke()
            completion = null
            return
        }

        utteranceSequence += 1
        val utteranceId = "me-radio-news-$utteranceSequence"
        activeUtteranceId = utteranceId
        engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    actual fun stop() {
        pendingText = null
        completion = null
        activeUtteranceId = null
        if (ready) engine.stop()
    }
}
