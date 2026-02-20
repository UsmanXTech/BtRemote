package com.atharok.btremote.common.utils

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceToTextParserState(
    val spokenText: String = "",
    val isSpeaking: Boolean = false,
    val error: String? = null,
    val rmsDb: Float = 0f
)

class VoiceToTextParser(
    private val app: Application
) : RecognitionListener {

    private val _state = MutableStateFlow(VoiceToTextParserState())
    val state: StateFlow<VoiceToTextParserState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(app).apply {
                setRecognitionListener(this@VoiceToTextParser)
            }
        }
    }

    fun startListening(languageCode: String = "en-US") {
        _state.update { VoiceToTextParserState(isSpeaking = true) }

        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            _state.update { it.copy(error = "Speech recognition is not available", isSpeaking = false) }
            return
        }

        // Must be called on Main Thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                ensureRecognizer()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isSpeaking = false) }
            }
        }
    }

    fun stopListening() {
        _state.update { it.copy(isSpeaking = false, rmsDb = 0f) }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            recognizer?.stopListening()
        }
    }

    fun destroy() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _state.update { it.copy(error = null) }
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {
        _state.update { it.copy(rmsDb = rmsdB) }
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _state.update { it.copy(isSpeaking = false) }
    }

    override fun onError(error: Int) {
        if (error == SpeechRecognizer.ERROR_CLIENT) return
        _state.update { it.copy(error = "Error: $error", isSpeaking = false) }
    }

    override fun onResults(results: Bundle?) {
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)?.let { text ->
            _state.update { it.copy(spokenText = text) }
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.getOrNull(0)?.let { text ->
            _state.update { it.copy(spokenText = text) }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
