package org.onedroid.radiowave.app.speech

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVSpeechBoundaryImmediate
import platform.AVFoundation.AVSpeechSynthesisVoice
import platform.AVFoundation.AVSpeechSynthesizer
import platform.AVFoundation.AVSpeechSynthesizerDelegateProtocol
import platform.AVFoundation.AVSpeechUtterance
import platform.AVFoundation.speakUtterance
import platform.AVFoundation.stopSpeakingAtBoundary
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
