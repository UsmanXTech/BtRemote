package com.atharok.btremote.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.MOUSE_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_REPORT_ID
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.domain.usecases.RemoteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.atharok.btremote.common.utils.VoiceToTextParser
import com.atharok.btremote.common.utils.VoiceToTextParserState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

private data class TextReportRequest(
    val text: String,
    val virtualKeyboardLayout: VirtualKeyboardLayout,
    val shouldSendEnter: Boolean
)

class RemoteViewModel(
    private val useCase: RemoteUseCase,
    private val voiceToTextParser: VoiceToTextParser
): ViewModel() {

    private val textReportChannel = Channel<TextReportRequest>(Channel.UNLIMITED)
    val voiceState: StateFlow<VoiceToTextParserState> = voiceToTextParser.state

    init {
        viewModelScope.launch(Dispatchers.Default) {
            textReportChannel.receiveAsFlow().collect { request ->
                useCase.sendTextReport(request.text, request.virtualKeyboardLayout, request.shouldSendEnter)
            }
        }
    }

    fun startVoiceInput(languageCode: String = "en-US") {
        voiceToTextParser.startListening(languageCode)
    }

    fun stopVoiceInput() {
        voiceToTextParser.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        voiceToTextParser.destroy()
    }

    // ---- Settings ----

    val remoteSettingsFlow: Flow<RemoteSettings> = useCase.remoteSettingsFlow

    // ---- Connection ----

    fun disconnectDevice(): Boolean = useCase.disconnectDevice()

    // ---- Send ----

    private fun sendReport(id: Int, bytes: ByteArray): Boolean {
        return useCase.sendReport(id, bytes)
    }

    // Remote
    val sendRemoteReport: (ByteArray) -> Unit = { bytes -> sendReport(REMOTE_REPORT_ID, bytes) }

    // Mouse
    val sendMouseReport: (MouseAction, Float, Float, Float) -> Unit = { input, x, y, wheel ->
        val xInt = x.roundToInt().coerceIn(-127, 127)
        val yInt = y.roundToInt().coerceIn(-127, 127)
        val bytes: ByteArray = byteArrayOf(input.byte, xInt.toByte(), yInt.toByte(), wheel.roundToInt().toByte())
        sendReport(MOUSE_REPORT_ID, bytes)
    }

    // Keyboard
    val sendKeyboardReport: (ByteArray) -> Unit = { bytes -> sendReport(KEYBOARD_REPORT_ID, bytes) }

    // Text (Keyboard)
    val sendTextReport: (String, VirtualKeyboardLayout, Boolean) -> Unit = { text, virtualKeyboardLayout, shouldSendEnter ->
        textReportChannel.trySend(TextReportRequest(text, virtualKeyboardLayout, shouldSendEnter))
    }
}