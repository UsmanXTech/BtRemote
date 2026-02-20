package com.atharok.btremote.ui.views.keyboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.REMOTE_INPUT_NONE
import com.atharok.btremote.domain.entities.remoteInput.RemoteButtonProperties
import com.atharok.btremote.presentation.viewmodel.RemoteViewModel
import com.atharok.btremote.ui.components.customButtons.RemoteIconSurfaceButton
import com.atharok.btremote.ui.theme.surfaceElevationHigh
import org.koin.androidx.compose.koinViewModel

@Composable
fun VirtualKeyboardModalBottomSheet(
    mustClearInputField: Boolean,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, Boolean) -> Unit,
    onShowKeyboardBottomSheetChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    remoteViewModel: RemoteViewModel = koinViewModel()
) {
    val voiceState by remoteViewModel.voiceState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

                // Send backspaces for removed characters (rare in voice, but possible with partial results)
                val backspacesCount = oldText.length - commonPrefixLength
                if (backspacesCount > 0) {
                    repeat(backspacesCount) {
                        sendKeyboardKeyReport(RemoteButtonProperties.KeyboardBackspaceButton.input)
                        sendKeyboardKeyReport(REMOTE_INPUT_NONE)
                    }
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

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
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
            rmsDb = voiceState.rmsDb,
            onVoiceClick = {
                if (voiceState.isSpeaking) {
                    remoteViewModel.stopVoiceInput()
                } else {
                    val permission = Manifest.permission.RECORD_AUDIO
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
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
                        sendKeyboardKeyReport(REMOTE_INPUT_NONE)
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
    rmsDb: Float,
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
                    VoicePulseButton(
                        isActive = isVoiceActive,
                        rmsDb = rmsDb,
                        onClick = onVoiceClick
                    )
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        sendKeyboardKeyReport(RemoteButtonProperties.KeyboardEnterButton.input)
                        sendKeyboardKeyReport(REMOTE_INPUT_NONE)
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
private fun VoicePulseButton(
    isActive: Boolean,
    rmsDb: Float,
    onClick: () -> Unit
) {
    // Normalizing RMS: typically -2 to 10. Let's map it to a scale factor.
    val normalizedRms = if (isActive) (rmsDb.coerceIn(-2f, 12f) + 2f) / 14f else 0f
    val pulseScale by animateFloatAsState(
        targetValue = if (isActive) 1f + (normalizedRms * 0.4f) else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "PulseScale"
    )

    Box(contentAlignment = Alignment.Center) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(pulseScale * 1.2f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (isActive) AppIcons.Mic else AppIcons.MicOff,
                contentDescription = "Voice Input",
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.scale(if (isActive) 1.1f else 1f)
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
