package com.atharok.btremote.ui.views.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardLanguage
import com.atharok.btremote.ui.components.BasicIconButton
import com.atharok.btremote.ui.components.customButtons.RemoteIconSurfaceButton
import com.atharok.btremote.ui.theme.surfaceElevationHigh

@Composable
fun VirtualKeyboardModalBottomSheet(
    mustClearInputField: Boolean,
    keyboardLanguage: KeyboardLanguage,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, Boolean) -> Unit,
    sendLiveTextUpdate: (String) -> Unit,
    sendLiveTypingEnter: () -> Unit,
    resetLiveTyping: () -> Unit,
    onShowKeyboardBottomSheetChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    KeyboardModalBottomSheet(
        onShowKeyboardBottomSheetChanged = onShowKeyboardBottomSheetChanged,
        windowInsets = WindowInsets.ime,
        modifier = modifier
    ) {
        val focusRequester = remember { FocusRequester() }
        val textState = remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        StatelessKeyboardView(
            mustClearInputField = mustClearInputField,
            focusRequester = focusRequester,
            text = textState.value,
            onTextChange = {
                textState.value = it
            },
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            sendTextReport = sendTextReport,
            sendLiveTextUpdate = sendLiveTextUpdate,
            sendLiveTypingEnter = sendLiveTypingEnter,
            resetLiveTyping = resetLiveTyping,
            keyboardLanguage = keyboardLanguage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.padding_medium))
                .padding(bottom = dimensionResource(id = R.dimen.padding_max))
        )
    }
}

@Composable
private fun StatelessKeyboardView(
    mustClearInputField: Boolean,
    focusRequester: FocusRequester,
    text: String,
    onTextChange: (String) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, Boolean) -> Unit,
    sendLiveTextUpdate: (String) -> Unit,
    sendLiveTypingEnter: () -> Unit,
    resetLiveTyping: () -> Unit,
    keyboardLanguage: KeyboardLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var liveTypingEnabled by rememberSaveable { mutableStateOf(false) }
    var isListening by rememberSaveable { mutableStateOf(false) }
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    val speechIntent = remember(keyboardLanguage) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, keyboardLanguage.toSpeechLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
    }

    fun updateText(value: String) {
        onTextChange(value)
        if (liveTypingEnabled) sendLiveTextUpdate(value)
    }

    fun enableLiveTyping() {
        if (!liveTypingEnabled) {
            liveTypingEnabled = true
            if (text.isNotEmpty()) sendLiveTextUpdate(text)
        }
    }

    val latestUpdateText by rememberUpdatedState<(String) -> Unit>(::updateText)

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                enableLiveTyping()
                speechRecognizer?.startListening(speechIntent)
                isListening = speechRecognizer != null
            } catch (exception: RuntimeException) {
                isListening = false
                Toast.makeText(context, R.string.voice_recognition_unavailable, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, R.string.microphone_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            private fun publishResult(results: Bundle?) {
                val recognizedText = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?: return
                latestUpdateText(recognizedText)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
            }
            override fun onResults(results: Bundle?) {
                publishResult(results)
                isListening = false
            }
            override fun onPartialResults(partialResults: Bundle?) {
                publishResult(partialResults)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_medium))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(unbounded = true),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_small)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = ::updateText,
                modifier = Modifier
                    .weight(4f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (liveTypingEnabled) {
                            sendLiveTypingEnter()
                        } else {
                            sendTextReport(text, true)
                        }
                        if (mustClearInputField) {
                            onTextChange("")
                            resetLiveTyping()
                        }
                    }
                )
            )

            BasicIconButton(
                onClick = {
                    if (liveTypingEnabled) {
                        sendLiveTypingEnter()
                    } else {
                        sendTextReport(text, false)
                    }
                    if (mustClearInputField) {
                        onTextChange("")
                        resetLiveTyping()
                    }
                },
                icon = AppIcons.Send,
                contentDescription = stringResource(id = R.string.send),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = CircleShape
            )

        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = liveTypingEnabled,
                    onCheckedChange = { enabled ->
                        liveTypingEnabled = enabled
                        if (enabled && text.isNotEmpty()) {
                            sendLiveTextUpdate(text)
                        } else {
                            resetLiveTyping()
                        }
                    }
                )
                Text(
                    text = stringResource(id = R.string.live_typing),
                    modifier = Modifier.padding(start = dimensionResource(id = R.dimen.padding_small))
                )
            }

            Button(
                onClick = {
                    if (isListening) {
                        speechRecognizer?.stopListening()
                        isListening = false
                    } else if (speechRecognizer == null) {
                        Toast.makeText(context, R.string.voice_recognition_unavailable, Toast.LENGTH_SHORT).show()
                    } else if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        try {
                            enableLiveTyping()
                            speechRecognizer.startListening(speechIntent)
                            isListening = true
                        } catch (exception: RuntimeException) {
                            isListening = false
                            Toast.makeText(context, R.string.voice_recognition_unavailable, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        id = if (isListening) R.string.stop_voice_typing else R.string.voice_typing
                    )
                )
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            AdditionalKeyboardKeysLayout(
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(unbounded = true)
            )
        }
    }
}

@Composable
private fun AdditionalKeyboardKeysLayout(
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    spaceBetweenButtons: Dp = dimensionResource(id = R.dimen.padding_small)
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spaceBetweenButtons)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardSpaceBarButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(3f)
            )

            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardUpButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f).padding(horizontal = spaceBetweenButtons)
            )

            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardPrintScreenButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardBackspaceButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f)
            )

            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardEnterButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f).padding(horizontal = spaceBetweenButtons)
            )

            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardLeftButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f)
            )

            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardDownButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f).padding(horizontal = spaceBetweenButtons)
            )

            VirtualKeyboardButton(
                properties = RemoteButtonProperties.KeyboardRightButton,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VirtualKeyboardButton(
    properties: RemoteButtonProperties,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    RemoteIconSurfaceButton(
        properties = properties,
        sendKeyReport = sendKeyboardKeyReport,
        modifier = modifier,
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.keyboard_key_corner_radius)),
        elevation = surfaceElevationHigh(),
        iconFillFraction = 1f,
        iconPadding = dimensionResource(id = R.dimen.padding_medium)
    )
}

private fun KeyboardLanguage.toSpeechLanguageTag(): String = when (this) {
    KeyboardLanguage.ENGLISH_US -> "en-US"
    KeyboardLanguage.ENGLISH_UK -> "en-GB"
    KeyboardLanguage.SPANISH -> "es-ES"
    KeyboardLanguage.FRENCH -> "fr-FR"
    KeyboardLanguage.GERMAN -> "de-DE"
    KeyboardLanguage.RUSSIAN -> "ru-RU"
    KeyboardLanguage.CZECH -> "cs-CZ"
    KeyboardLanguage.POLISH -> "pl-PL"
    KeyboardLanguage.PORTUGUESE -> "pt-PT"
    KeyboardLanguage.BRAZILIAN -> "pt-BR"
    KeyboardLanguage.GREEK -> "el-GR"
    KeyboardLanguage.TURKISH -> "tr-TR"
    KeyboardLanguage.HEBREW -> "he-IL"
    KeyboardLanguage.BULGARIAN -> "bg-BG"
    KeyboardLanguage.UKRAINIAN -> "uk-UA"
    KeyboardLanguage.PERSIAN -> "fa-IR"
    KeyboardLanguage.ARABIC -> "ar-SA"
}
