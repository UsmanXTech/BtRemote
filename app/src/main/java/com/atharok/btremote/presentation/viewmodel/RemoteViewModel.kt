package com.atharok.btremote.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atharok.btremote.common.utils.KEYBOARD_REPORT_ID
import com.atharok.btremote.common.utils.MOUSE_REPORT_ID
import com.atharok.btremote.common.utils.REMOTE_REPORT_ID
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.domain.usecases.RemoteUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RemoteViewModel(
    private val useCase: RemoteUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "RemoteViewModel"
    }

    val remoteSettingsFlow: Flow<RemoteSettings> = useCase.remoteSettingsFlow

    fun disconnectDevice(): Boolean {
        val activeTextJob = sendTextJob
        if (activeTextJob?.isActive == true) {
            // Let the text sender's finally block attempt key-up before disconnecting.
            activeTextJob.cancel()
            sendTextJob = null
            viewModelScope.launch {
                activeTextJob.join()
                useCase.disconnectDevice()
            }
            return true
        }
        return useCase.disconnectDevice()
    }

    fun sendReport(id: Int, bytes: ByteArray): Boolean = useCase.sendReport(id, bytes)

    val sendRemoteReport: (ByteArray) -> Unit = { bytes ->
        sendReport(REMOTE_REPORT_ID, bytes)
    }

    val sendMouseReport: (MouseAction, Float, Float, Float) -> Unit = { input, x, y, wheel ->
        val xInt = x.roundToInt().coerceIn(-127, 127)
        val yInt = y.roundToInt().coerceIn(-127, 127)
        val wheelInt = wheel.roundToInt().coerceIn(-127, 127)
        val bytes = byteArrayOf(input.byte, xInt.toByte(), yInt.toByte(), wheelInt.toByte())
        sendReport(MOUSE_REPORT_ID, bytes)
    }

    val sendKeyboardReport: (ByteArray) -> Unit = { bytes ->
        sendReport(KEYBOARD_REPORT_ID, bytes)
    }

    private var sendTextJob: Job? = null

    val sendTextReport: (String, VirtualKeyboardLayout, Boolean) -> Unit =
        { text, virtualKeyboardLayout, shouldSendEnter ->
            if (sendTextJob?.isActive != true) {
                sendTextJob = viewModelScope.launch(Dispatchers.Default) {
                    try {
                        useCase.sendTextReport(text, virtualKeyboardLayout, shouldSendEnter)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        Log.w(TAG, "Text input failed", exception)
                    }
                }
            }
        }

    override fun onCleared() {
        sendTextJob?.cancel()
        sendTextJob = null
        super.onCleared()
    }
}
