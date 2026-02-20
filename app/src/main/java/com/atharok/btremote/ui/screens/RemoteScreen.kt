package com.atharok.btremote.ui.screens

import android.bluetooth.BluetoothHidDevice
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.atharok.btremote.R
import com.atharok.btremote.common.utils.AppIcons
import com.atharok.btremote.common.utils.getKeyboardLayout
import com.atharok.btremote.domain.entities.DeviceHidConnectionState
import com.atharok.btremote.domain.entities.RemoteNavigationEntity
import com.atharok.btremote.domain.entities.remoteInput.MouseAction
import com.atharok.btremote.domain.entities.remoteInput.keyboard.KeyboardLanguage
import com.atharok.btremote.domain.entities.remoteInput.keyboard.virtualKeyboard.VirtualKeyboardLayout
import com.atharok.btremote.domain.entities.settings.RemoteSettings
import com.atharok.btremote.presentation.services.BluetoothHidService
import com.atharok.btremote.presentation.viewmodel.RemoteViewModel
import com.atharok.btremote.ui.components.AppScaffold
import com.atharok.btremote.ui.components.BasicDropdownMenuItem
import com.atharok.btremote.ui.components.BasicIconButton
import com.atharok.btremote.ui.components.FadeAnimatedContent
import com.atharok.btremote.ui.components.LoadingDialog
import com.atharok.btremote.ui.components.MoreOverflowMenu
import com.atharok.btremote.ui.components.TextLarge
import com.atharok.btremote.ui.theme.surfaceElevationMedium
import com.atharok.btremote.ui.views.RemoteScreenHelpModalBottomSheet
import com.atharok.btremote.ui.views.keyboard.AdvancedKeyboard
import com.atharok.btremote.ui.views.keyboard.AdvancedKeyboardModalBottomSheet
import com.atharok.btremote.ui.views.keyboard.VirtualKeyboardModalBottomSheet
import com.atharok.btremote.ui.views.mouse.MousePadLayout
import com.atharok.btremote.ui.views.remote.MinimalistRemoteView
import com.atharok.btremote.ui.views.remote.MoreButtonsDialog
import com.atharok.btremote.ui.views.remote.RemoteView
import com.atharok.btremote.ui.views.remote.TVChannelDialog
import com.atharok.btremote.ui.views.remoteNavigation.RemoteDirectionalPadNavigation
import com.atharok.btremote.ui.views.remoteNavigation.RemoteSwipeNavigation
import org.koin.androidx.compose.koinViewModel

private enum class NavigationToggle {
    DIRECTION,
    MOUSE
}

@Composable
fun RemoteScreen(
    isBluetoothServiceRunning: Boolean,
    bluetoothDeviceHidConnectionState: DeviceHidConnectionState,
    closeApp: () -> Unit,
    navigateUp: () -> Unit,
    navigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    remoteViewModel: RemoteViewModel = koinViewModel(),
    context: Context = LocalContext.current
) {
    val configuration = LocalConfiguration.current

    val remoteSettings by remoteViewModel
        .remoteSettingsFlow.collectAsStateWithLifecycle(RemoteSettings())

    // Remote
    var navigationToggle by rememberSaveable(remoteSettings.useMouseNavigationByDefault) { mutableStateOf(if(remoteSettings.useMouseNavigationByDefault) NavigationToggle.MOUSE else NavigationToggle.DIRECTION) }

    // Keyboard
    var isKeyboardVisible: Boolean by rememberSaveable { mutableStateOf(false) }

    // More Buttons
    var isMoreButtonsVisible: Boolean by rememberSaveable { mutableStateOf(false) }

    // Help
    var isHelpBottomSheetVisible: Boolean by remember { mutableStateOf(false) }

    BackHandler(enabled = true, onBack = closeApp)

    DisposableEffect(isBluetoothServiceRunning) {
        if(!isBluetoothServiceRunning) {
            navigateUp()
        }
        onDispose {}
    }

    DisposableEffect(bluetoothDeviceHidConnectionState.state) {
        if(bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_DISCONNECTED) {
            navigateUp()
        }
        onDispose {}
    }

    StatelessRemoteScreen(
        deviceName = bluetoothDeviceHidConnectionState.deviceName,
        bluetoothDeviceHidConnectionState = bluetoothDeviceHidConnectionState,
        isLandscapeMode = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        topBarActions = {
            TopBarActions(
                navigateToSettings = navigateToSettings,
                disconnectDevice = {
                    remoteViewModel.disconnectDevice()
                },
                navigationToggle = navigationToggle,
                onNavigationToggleChanged = { navigationToggle = it },
                useAdvancedKeyboardIntegrated = remoteSettings.useAdvancedKeyboard && remoteSettings.useAdvancedKeyboardIntegrated,
                isKeyboardVisible = isKeyboardVisible,
                onKeyboardVisibleChanged = { isKeyboardVisible = it },
                isMoreButtonsVisible = isMoreButtonsVisible,
                onMoreButtonsVisibleChanged = { isMoreButtonsVisible = it },
                isHelpBottomSheetVisible = isHelpBottomSheetVisible,
                onHelpBottomSheetVisibleChanged = { isHelpBottomSheetVisible = it }
            )
        },
        remoteLayout = {
            val isAdvancedKeyboardVisible = remember {
                derivedStateOf { remoteSettings.useAdvancedKeyboard && remoteSettings.useAdvancedKeyboardIntegrated && isKeyboardVisible }
            }
            val rememberedSendRemoteKeyReport = remember { remoteViewModel.sendRemoteReport }
            val rememberedSendKeyboardKeyReport = remember { remoteViewModel.sendKeyboardReport }
            RemoteLayout(
                useMinimalistRemote = remoteSettings.useMinimalistRemote,
                isAdvancedKeyboardVisible = isAdvancedKeyboardVisible.value,
                onMoreButtonsVisibleChanged = { isMoreButtonsVisible = !isMoreButtonsVisible },
                keyboardLanguage = remoteSettings.keyboardLanguage,
                hapticFeedbackEnabled = remoteSettings.hapticFeedback,
                sendRemoteKeyReport = rememberedSendRemoteKeyReport,
                sendKeyboardKeyReport = rememberedSendKeyboardKeyReport
            )
        },
        navigationLayout = {
            val rememberedSendRemoteKeyReport = remember { remoteViewModel.sendRemoteReport }
            val rememberedSendKeyboardKeyReport = remember { remoteViewModel.sendKeyboardReport }
            val rememberedSendMouseKeyReport = remember { remoteViewModel.sendMouseReport }
            NavigationLayout(
                remoteSettings = remoteSettings,
                sendRemoteKeyReport = rememberedSendRemoteKeyReport,
                sendKeyboardKeyReport = rememberedSendKeyboardKeyReport,
                sendMouseKeyReport = rememberedSendMouseKeyReport,
                navigationToggle = navigationToggle,
            )
        },
        overlayView = {
            // Dialog / ModalBottomSheet
            when {
                bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_DISCONNECTING -> {
                    LoadingDialog(
                        title = stringResource(id = R.string.connection),
                        message = stringResource(id = R.string.bluetooth_device_disconnecting_message, bluetoothDeviceHidConnectionState.deviceName),
                        buttonText = stringResource(id = R.string.disconnect),
                        onButtonClick = {
                            remoteViewModel.disconnectDevice()
                            BluetoothHidService.stop(context) // force disconnection
                        }
                    )
                }
                isHelpBottomSheetVisible -> {
                    RemoteScreenHelpModalBottomSheet(
                        onDismissRequest = { isHelpBottomSheetVisible = false },
                        modifier = modifier
                    )
                }
                isKeyboardVisible && (!remoteSettings.useAdvancedKeyboard || !remoteSettings.useAdvancedKeyboardIntegrated) -> {
                    KeyboardModalBottomSheet(
                        useAdvancedKeyboard = remoteSettings.useAdvancedKeyboard,
                        keyboardLanguage = remoteSettings.keyboardLanguage,
                        mustClearInputField = remoteSettings.mustClearInputField,
                        sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport,
                        sendTextReport = remoteViewModel.sendTextReport,
                        onShowKeyboardChanged = { isKeyboardVisible = it },
                        remoteViewModel = remoteViewModel
                    )
                }
                isMoreButtonsVisible -> {
                    MoreButtonsDialog(
                        sendRemoteKeyReport = remoteViewModel.sendRemoteReport,
                        sendKeyboardKeyReport = remoteViewModel.sendKeyboardReport,
                        onDismissRequest = { isMoreButtonsVisible = false }
                    )
                }
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatelessRemoteScreen(
    deviceName: String,
    bluetoothDeviceHidConnectionState: DeviceHidConnectionState,
    isLandscapeMode: Boolean,
    topBarActions: @Composable (RowScope.() -> Unit),
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    overlayView: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    AppScaffold(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_normal))
            ) {
                TextLarge(text = deviceName)
                if (bluetoothDeviceHidConnectionState.state == BluetoothHidDevice.STATE_CONNECTED) {
                    androidx.compose.material3.Icon(
                        imageVector = AppIcons.EnabledAutoConnect,
                        contentDescription = stringResource(id = R.string.connected_on, deviceName),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        modifier = modifier,
        scrollBehavior = null,
        topBarActions = topBarActions
    ) { innerPadding ->

        if(isLandscapeMode) {
            RemoteLandscapeView(
                remoteLayout = remoteLayout,
                navigationLayout = navigationLayout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            RemotePortraitView(
                remoteLayout = remoteLayout,
                navigationLayout = navigationLayout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }

        // Dialog / ModalBottomSheet
        overlayView()
    }
}

@Composable
private fun RemoteLandscapeView(
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var rowSize by remember { mutableStateOf(Size.Zero) }

    Row(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            rowSize = layoutCoordinates.size.toSize() },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = with(LocalDensity.current) { (0.5f * rowSize.width).toDp() })
                .align(Alignment.CenterVertically)
                .padding(
                    start = dimensionResource(id = R.dimen.padding_large),
                    top = dimensionResource(id = R.dimen.padding_large),
                    bottom = dimensionResource(id = R.dimen.padding_large)
                ),
        ) {
            navigationLayout()
        }

        Box(
            modifier = Modifier.align(Alignment.CenterVertically),
            contentAlignment = Alignment.Center
        ) {
            remoteLayout()
        }
    }
}

@Composable
private fun RemotePortraitView(
    remoteLayout: @Composable () -> Unit,
    navigationLayout: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .heightIn(
                    max = with(LocalConfiguration.current) {
                        if (screenHeightDp >= screenWidthDp * 1.9f) // Si la hauteur de l'appareil est suffisamment haute par rapport à sa largeur (ratio ~ 1/2)
                            screenWidthDp.dp // On peut se permettre de prendre pour hauteur la largeur de l'écran
                        else // Sinon
                            (screenHeightDp * 0.50).dp // On prend 50% de la hauteur de l'écran
                    }
                )
                .align(Alignment.CenterHorizontally),
        ) {
            remoteLayout()
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(
                    start = dimensionResource(id = R.dimen.padding_large),
                    end = dimensionResource(id = R.dimen.padding_large),
                    bottom = dimensionResource(id = R.dimen.padding_large)
                ),
            contentAlignment = Alignment.Center
        ) {
            navigationLayout()
        }
    }
}

@Composable
private fun RemoteLayout(
    useMinimalistRemote: Boolean,
    isAdvancedKeyboardVisible: Boolean,
    onMoreButtonsVisibleChanged: () -> Unit,
    keyboardLanguage: KeyboardLanguage,
    hapticFeedbackEnabled: Boolean,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit
) {
    FadeAnimatedContent(targetState = isAdvancedKeyboardVisible) {
        if (it) {
            AdvancedKeyboard(
                keyboardLanguage = keyboardLanguage,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.padding_large)),
                keyElevation = surfaceElevationMedium()
            )
        } else {
            RemoteButtonsLayouts(
                useMinimalistRemote = useMinimalistRemote,
                onMoreButtonsVisibleChanged = onMoreButtonsVisibleChanged,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                sendRemoteKeyReport = sendRemoteKeyReport,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
            )
        }
    }
}

@Composable
private fun RemoteButtonsLayouts(
    useMinimalistRemote: Boolean,
    onMoreButtonsVisibleChanged: () -> Unit,
    hapticFeedbackEnabled: Boolean,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit
) {
    if (useMinimalistRemote) {
        var isTVChannelButtonsVisible: Boolean by remember { mutableStateOf(false) }

        MinimalistRemoteView(
            sendRemoteKeyReport = sendRemoteKeyReport,
            onTVChannelButtonsChanged = {
                isTVChannelButtonsVisible = !isTVChannelButtonsVisible
            },
            onMoreButtonsVisibleChanged = onMoreButtonsVisibleChanged,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_normal))
        )

        if (isTVChannelButtonsVisible) {
            TVChannelDialog(
                sendRemoteKeyReport = sendRemoteKeyReport,
                sendKeyboardKeyReport = sendKeyboardKeyReport,
                onDismissRequest = {
                    isTVChannelButtonsVisible = false
                }
            )
        }
    } else {
        RemoteView(
            sendRemoteKeyReport = sendRemoteKeyReport,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_normal))
        )
    }
}

@Composable
private fun NavigationLayout(
    remoteSettings: RemoteSettings,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendMouseKeyReport: (input: MouseAction, x: Float, y: Float, wheel: Float) -> Unit,
    navigationToggle: NavigationToggle
) {
    FadeAnimatedContent(targetState = navigationToggle) {
        when(it) {
            NavigationToggle.DIRECTION -> {
                RemotePadLayout(
                    remoteNavigationMode = remoteSettings.remoteNavigationEntity,
                    sendRemoteKeyReport = sendRemoteKeyReport,
                    sendKeyboardKeyReport = sendKeyboardKeyReport,
                    useEnterForSelection = remoteSettings.useEnterForSelection,
                    hapticFeedbackEnabled = remoteSettings.hapticFeedback
                )
            }

            NavigationToggle.MOUSE -> {
                MousePadLayout(
                    mouseSpeed = remoteSettings.mouseSpeed,
                    gyroscopeSensitivity = remoteSettings.gyroscopeSensitivity,
                    shouldInvertMouseScrollingDirection = remoteSettings.shouldInvertMouseScrollingDirection,
                    useGyroscope = remoteSettings.useGyroscope,
                    hapticFeedbackEnabled = remoteSettings.hapticFeedback,
                    sendMouseInput = sendMouseKeyReport,
                    modifier = Modifier
                )
            }
        }
    }
}

@Composable
private fun RemotePadLayout(
    remoteNavigationMode: RemoteNavigationEntity,
    sendRemoteKeyReport: (ByteArray) -> Unit,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    useEnterForSelection: Boolean,
    hapticFeedbackEnabled: Boolean
) {
    if(remoteNavigationMode == RemoteNavigationEntity.D_PAD) {
        RemoteDirectionalPadNavigation(
            sendRemoteKeyReport = sendRemoteKeyReport,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            useEnterForSelection = useEnterForSelection,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            modifier = Modifier.aspectRatio(1f)
        )
    } else {
        RemoteSwipeNavigation(
            sendRemoteKeyReport = sendRemoteKeyReport,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            useEnterForSelection = useEnterForSelection,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            modifier = Modifier
        )
    }
}

@Composable
private fun KeyboardModalBottomSheet(
    useAdvancedKeyboard: Boolean,
    keyboardLanguage: KeyboardLanguage,
    mustClearInputField: Boolean,
    sendKeyboardKeyReport: (ByteArray) -> Unit,
    sendTextReport: (String, VirtualKeyboardLayout, Boolean) -> Unit,
    onShowKeyboardChanged: (Boolean) -> Unit,
    remoteViewModel: RemoteViewModel
) {
    if (useAdvancedKeyboard) {
        AdvancedKeyboardModalBottomSheet(
            keyboardLanguage = keyboardLanguage,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            onShowKeyboardBottomSheetChanged = onShowKeyboardChanged
        )
    } else {
        var virtualKeyboardLayout: VirtualKeyboardLayout by remember {
            mutableStateOf(getKeyboardLayout(keyboardLanguage))
        }

        LaunchedEffect(keyboardLanguage) {
            virtualKeyboardLayout = getKeyboardLayout(keyboardLanguage)
        }

        VirtualKeyboardModalBottomSheet(
            mustClearInputField = mustClearInputField,
            sendKeyboardKeyReport = sendKeyboardKeyReport,
            sendTextReport = { text: String, shouldSendEnter: Boolean ->
                sendTextReport(text, virtualKeyboardLayout, shouldSendEnter)
            },
            onShowKeyboardBottomSheetChanged = onShowKeyboardChanged,
            remoteViewModel = remoteViewModel
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarActions(
    navigateToSettings: () -> Unit,
    disconnectDevice: () -> Unit,
    navigationToggle: NavigationToggle,
    onNavigationToggleChanged: (NavigationToggle) -> Unit,
    useAdvancedKeyboardIntegrated: Boolean,
    isKeyboardVisible: Boolean,
    onKeyboardVisibleChanged: (Boolean) -> Unit,
    isMoreButtonsVisible: Boolean,
    onMoreButtonsVisibleChanged: (Boolean) -> Unit,
    isHelpBottomSheetVisible: Boolean,
    onHelpBottomSheetVisibleChanged: (Boolean) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.padding(end = dimensionResource(id = R.dimen.padding_small))
    ) {
        SegmentedButton(
            selected = navigationToggle == NavigationToggle.DIRECTION,
            onClick = { onNavigationToggleChanged(NavigationToggle.DIRECTION) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {}
        ) {
            androidx.compose.material3.Icon(
                imageVector = AppIcons.Controller,
                contentDescription = stringResource(id = R.string.direction_arrows),
                modifier = Modifier.size(18.dp)
            )
        }
        SegmentedButton(
            selected = navigationToggle == NavigationToggle.MOUSE,
            onClick = { onNavigationToggleChanged(NavigationToggle.MOUSE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {}
        ) {
            androidx.compose.material3.Icon(
                imageVector = AppIcons.Mouse,
                contentDescription = stringResource(id = R.string.mouse),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if(useAdvancedKeyboardIntegrated) {
        FadeAnimatedContent(targetState = isKeyboardVisible) {
            when (it) {
                true -> {
                    // Remote
                    BasicIconButton(
                        onClick = { onKeyboardVisibleChanged(false) },
                        icon = AppIcons.RemoteControl,
                        contentDescription = stringResource(id = R.string.remote)
                    )
                }

                false -> {
                    // Keyboard
                    BasicIconButton(
                        onClick = { onKeyboardVisibleChanged(true) },
                        icon = AppIcons.Keyboard,
                        contentDescription = stringResource(id = R.string.keyboard)
                    )
                }
            }
        }
    } else {
        // Keyboard
        BasicIconButton(
            onClick = { onKeyboardVisibleChanged(!isKeyboardVisible) },
            icon = AppIcons.Keyboard,
            contentDescription = stringResource(id = R.string.keyboard)
        )
    }

    MoreOverflowMenu { closeDropdownMenu: () -> Unit ->

        // More Buttons
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.more_buttons),
            icon = AppIcons.MoreHoriz,
            onClick = {
                closeDropdownMenu()
                onMoreButtonsVisibleChanged(!isMoreButtonsVisible)
            }
        )

        // Disconnect
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.disconnect),
            icon = AppIcons.Disconnect,
            onClick = {
                closeDropdownMenu()
                disconnectDevice()
            }
        )

        // Divider
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(id = R.dimen.padding_medium))
        )

        // Help
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.help),
            icon = AppIcons.Help,
            onClick = {
                closeDropdownMenu()
                onHelpBottomSheetVisibleChanged(!isHelpBottomSheetVisible)
            }
        )

        // Settings
        BasicDropdownMenuItem(
            text = stringResource(id = R.string.settings),
            icon = AppIcons.Settings,
            onClick = {
                closeDropdownMenu()
                navigateToSettings()
            }
        )
    }
}