package org.onedroid.radiowave.app.speech

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVSpeechBoundaryImmediate
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.speakUtterance
import platform.AVFAudio.stopSpeakingAtBoundary
import platform.Foundation.NSObject

@OptIn(ExperimentalForeignApi::class)
actual class NewsSpeechController actual constructor(context: Any) {
    private val synthesizer = AVSpeechSynthesizer()
    private var completion: (() -> Unit)? = null

    private val speechDelegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance
        ) {
            val callback = completion
            completion = null
            callback?.invoke()
        }

        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didCancelSpeechUtterance: AVSpeechUtterance
        ) {
            completion = null
        }
    }

    init {
        synthesizer.delegate = speechDelegate
    }

    actual fun speak(text: String, onDone: () -> Unit) {
        stop()
        completion = onDone
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage("ar-EG")
            ?: AVSpeechSynthesisVoice.voiceWithLanguage("ar")
        utterance.rate = 0.48f
        synthesizer.speakUtterance(utterance)
    }

    actual fun stop() {
        completion = null
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
    }
}
