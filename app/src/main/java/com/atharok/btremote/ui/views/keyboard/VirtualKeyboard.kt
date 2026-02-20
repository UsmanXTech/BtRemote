package com.atharok.btremote.ui.views.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.ui.components.customButtons.IconRawButton
import com.atharok.btremote.ui.components.customButtons.RemoteIconSurfaceButton
import com.atharok.btremote.ui.theme.surfaceElevationHigh

@Composable
fun VirtualKeyboardModalBottomSheet(
    mustClearInputField: Boolean,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, Boolean) -> Unit,
    onShowKeyboardBottomSheetChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    remoteViewModel: com.atharok.btremote.presentation.viewmodel.RemoteViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val voiceState by remoteViewModel.voiceState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    KeyboardModalBottomSheet(
        onShowKeyboardBottomSheetChanged = onShowKeyboardBottomSheetChanged,
        windowInsets = WindowInsets.ime,
        modifier = modifier
    ) {
        val focusRequester = remember { FocusRequester() }
        val textState = remember { mutableStateOf("") }

        // Sync textState with voice input
        LaunchedEffect(voiceState.spokenText) {
            if (voiceState.spokenText.isNotEmpty()) {
                val newText = voiceState.spokenText
                val oldText = textState.value
                
                // Diff logic for live voice writing
                var commonPrefixLength = 0
                val minLength = minOf(oldText.length, newText.length)
                while (commonPrefixLength < minLength && oldText[commonPrefixLength] == newText[commonPrefixLength]) {
                    commonPrefixLength++
                }

                val addedText = newText.substring(commonPrefixLength)
                if (addedText.isNotEmpty()) {
                    sendTextReport(addedText, false)
                }
                
                textState.value = newText
            }
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                remoteViewModel.startVoiceInput()
            }
        }

        StatelessKeyboardView(
            mustClearInputField = mustClearInputField,
            focusRequester = focusRequester,
            text = textState.value,
            isVoiceActive = voiceState.isSpeaking,
            onVoiceClick = {
                if (voiceState.isSpeaking) {
                    remoteViewModel.stopVoiceInput()
                } else {
                    val permission = android.Manifest.permission.RECORD_AUDIO
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        remoteViewModel.startVoiceInput()
                    } else {
                        permissionLauncher.launch(permission)
                    }
                }
            },
            onTextChange = { newText ->
                val oldText = textState.value
                
                // Live writing logic:
                var commonPrefixLength = 0
                val minLength = minOf(oldText.length, newText.length)
                while (commonPrefixLength < minLength && oldText[commonPrefixLength] == newText[commonPrefixLength]) {
                    commonPrefixLength++
                }

                val backspacesCount = oldText.length - commonPrefixLength
                if (backspacesCount > 0) {
                    repeat(backspacesCount) {
                        sendKeyboardKeyReport(RemoteButtonProperties.KeyboardBackspaceButton.input)
                        sendKeyboardKeyReport(com.atharok.btremote.common.utils.REMOTE_INPUT_NONE)
                    }
                }

                val addedText = newText.substring(commonPrefixLength)
                if (addedText.isNotEmpty()) {
                    sendTextReport(addedText, false)
                }

                textState.value = newText
            },
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            sendTextReport = sendTextReport,
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
    isVoiceActive: Boolean,
    onVoiceClick: () -> Unit,
    onTextChange: (String) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { com.atharok.btremote.ui.components.TextNormalSecondary(text = stringResource(id = R.string.keyboard)) },
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = onVoiceClick) {
                        androidx.compose.material3.Icon(
                            imageVector = if (isVoiceActive) AppIcons.Mic else AppIcons.MicOff,
                            contentDescription = "Voice Input",
                            tint = if (isVoiceActive) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        sendKeyboardKeyReport(RemoteButtonProperties.KeyboardEnterButton.input)
                        sendKeyboardKeyReport(com.atharok.btremote.common.utils.REMOTE_INPUT_NONE)
                        if(mustClearInputField) {
                            onTextChange("")
                        }
                    }
                )
            )
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