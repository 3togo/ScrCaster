package io.github.togo3.scrcaster.widgets

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.togo3.scrcaster.NativeCoreFacade
import io.github.togo3.scrcaster.R
import io.github.togo3.scrcaster.constants.ScrcpyPresets
import io.github.togo3.scrcaster.constants.UiSpacing
import io.github.togo3.scrcaster.connection.ConnectionStatus
import io.github.togo3.scrcaster.connection.QrImage
import io.github.togo3.scrcaster.connection.QrPairingUiState
import io.github.togo3.scrcaster.connection.StatusLine
import io.github.togo3.scrcaster.models.ConnectionTarget
import io.github.togo3.scrcaster.models.DeviceShortcut
import io.github.togo3.scrcaster.scaffolds.ArrowSlider
import io.github.togo3.scrcaster.scaffolds.SuperTextField
import io.github.togo3.scrcaster.scan.QrScanDialog
import io.github.togo3.scrcaster.scrcpy.Scrcpy
import io.github.togo3.scrcaster.scrcpy.Shared.Codec
import io.github.togo3.scrcaster.scrcpy.TouchEventHandler
import io.github.togo3.scrcaster.services.AppRuntime
import io.github.togo3.scrcaster.services.LocalInputService
import io.github.togo3.scrcaster.storage.ScrcpyOptions
import io.github.togo3.scrcaster.storage.Settings
import io.github.togo3.scrcaster.storage.Storage
import io.github.togo3.scrcaster.storage.Storage.scrcpyOptions
import io.github.togo3.scrcaster.ui.confirm
import io.github.togo3.scrcaster.ui.contextClick
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.roundToInt

@Composable
internal fun DeviceTile(
    device: DeviceShortcut,
    isConnected: Boolean,
    actionEnabled: Boolean,
    actionInProgress: Boolean,
    editing: Boolean,
    connectedAddress: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: () -> Unit,
    onCancelAction: () -> Unit,
    onEditorSave: (DeviceShortcut) -> Unit,
    onEditorDelete: () -> Unit,
    onEditorCancel: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scrcpyProfilesState by Storage.scrcpyProfiles.state.collectAsState()

    var draft by remember(editing, device.id) {
        mutableStateOf(if (editing) device else null)
    }
    var originalDraft by remember(editing, device.id) {
        mutableStateOf(if (editing) device else null)
    }
    val draftAddresses = remember(editing, device.id) {
        mutableStateListOf<String>().also {
            if (editing) it.addAll(device.addresses)
        }
    }

    fun buildDraftFromAddresses(): DeviceShortcut {
        val c = draft ?: return device
        val cleaned = draftAddresses
            .map { it.trim().replace('：', ':') }
            .filter { it.isNotBlank() }
        return c.copy(addresses = cleaned.ifEmpty { listOf("") })
    }

    LaunchedEffect(editing, draft) {
        if (!editing) return@LaunchedEffect
        delay(Settings.BUNDLE_SAVE_DELAY)
        val updated = buildDraftFromAddresses()
        if (updated != device && updated.host.isNotBlank()) {
            onEditorSave(updated)
        }
    }

    val currentDraft = draft ?: device
    val currentOriginalDraft = originalDraft ?: device
    val profileNames = remember(scrcpyProfilesState.profiles) {
        scrcpyProfilesState.profiles.map { it.name }
    }
    val profileIds = remember(scrcpyProfilesState.profiles) {
        scrcpyProfilesState.profiles.map { it.id }
    }
    val profileDropdownIndex = remember(currentDraft.scrcpyProfileId, profileIds) {
        profileIds.indexOf(currentDraft.scrcpyProfileId).coerceAtLeast(0)
    }

    Card(
        colors = CardDefaults.defaultColors(
            color =
                if (isConnected) colorScheme.surfaceContainer
                else colorScheme.surfaceContainer.copy(alpha = 0.6f),
        ),
        pressFeedbackType = if (!editing) PressFeedbackType.Sink else PressFeedbackType.None,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!isConnected)
                        Modifier.combinedClickable(
                            onClick = {
                                haptic.contextClick()
                                onClick()
                            },
                            onLongClick = onLongClick,
                        )
                    else Modifier,
                )
                .padding(UiSpacing.PageItem),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // status dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color =
                                if (isConnected) Color(0xFF44C74F)
                                else colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Spacer(modifier = Modifier.width(UiSpacing.PageItem))
                // device name/address
                Column {
                    Text(
                        device.name.ifBlank { device.host },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        connectedAddress ?: "${device.host}:${device.port}",
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionInProgress) {
                    CircularProgressIndicator(progress = null)
                    Spacer(Modifier.width(UiSpacing.Medium))
                    TextButton(
                        text = stringResource(R.string.button_cancel),
                        onClick = onCancelAction,
                        colors = ButtonDefaults.textButtonColors(),
                    )
                } else {
                    TextButton(
                        text = stringResource(
                            if (!isConnected) R.string.button_connect
                            else R.string.button_disconnect,
                        ),
                        onClick = onAction,
                        enabled = actionEnabled,
                        colors =
                            if (!isConnected && device.startScrcpyOnConnect)
                                ButtonDefaults.textButtonColorsPrimary()
                            else
                                ButtonDefaults.textButtonColors(),
                    )
                }
            }
        }

        AnimatedVisibility(editing) {
            Column(
                modifier = Modifier.padding(vertical = UiSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = UiSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    SuperTextField(
                        value = currentDraft.name,
                        onValueChange = { draft = currentDraft.copy(name = it) },
                        label = stringResource(R.string.label_device_name),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    draftAddresses.forEachIndexed { index, addr ->
                        SuperTextField(
                            value = addr,
                            onValueChange = {
                                draftAddresses[index] = it
                                draft = (draft ?: device).copy(addresses = draftAddresses.toList())
                            },
                            label = stringResource(R.string.label_ip_port),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            onFocusLost = {
                                draftAddresses[index] = draftAddresses[index].replace('：', ':')
                            },
                            trailingIcon = {
                                Row(
                                    modifier = Modifier.padding(end = UiSpacing.Medium),
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.contextClick()
                                            draftAddresses.add(index + 1, "")
                                            draft = (draft ?: device).copy(addresses = draftAddresses.toList())
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.AddCircle,
                                            contentDescription = stringResource(R.string.cd_add_address),
                                        )
                                    }
                                    if (draftAddresses.size > 1) {
                                        IconButton(
                                            onClick = {
                                                haptic.contextClick()
                                                draftAddresses.removeAt(index)
                                                draft = (draft ?: device).copy(addresses = draftAddresses.toList())
                                            },
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Delete,
                                                contentDescription = stringResource(R.string.cd_delete_address),
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                    CheckboxPreference(
                        title = stringResource(R.string.device_config_start_immediately),
                        checkboxLocation = CheckboxLocation.End,
                        checked = currentDraft.startScrcpyOnConnect,
                        onCheckedChange = {
                            draft = currentDraft.copy(startScrcpyOnConnect = it)
                        },
                    )
                    AnimatedVisibility(currentDraft.startScrcpyOnConnect) {
                        CheckboxPreference(
                            title = stringResource(R.string.device_config_direct_fullscreen),
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentDraft.startScrcpyOnConnect
                                    && currentDraft.openFullscreenOnStart,
                            enabled = currentDraft.startScrcpyOnConnect,
                            onCheckedChange = {
                                draft = currentDraft.copy(openFullscreenOnStart = it)
                            },
                        )
                    }
                    val textGlobal = stringResource(R.string.text_global)
                    OverlayDropdownPreference(
                        title = stringResource(R.string.device_config_scrcpy_config),
                        items = profileNames,
                        selectedIndex = profileDropdownIndex,
                        onSelectedIndexChange = {
                            val profileId = profileIds.getOrElse(it) {
                                ScrcpyOptions.GLOBAL_PROFILE_ID
                            }
                            val profileName = profileNames.getOrElse(it) { textGlobal }
                            val deviceName = currentDraft.name.ifBlank { currentDraft.host }
                            draft = currentDraft.copy(scrcpyProfileId = profileId)
                            AppRuntime.snackbar(
                                R.string.device_switched_profile,
                                deviceName,
                                profileName,
                            )
                        },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
                ) {
                    TextButton(
                        text = stringResource(R.string.button_cancel),
                        onClick = {
                            onEditorSave(currentOriginalDraft)
                            onEditorCancel()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.button_delete),
                        onClick = onEditorDelete,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.button_done),
                        onClick = onEditorCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DeviceTileList(
    devices: List<DeviceShortcut>,
    isConnected: (DeviceShortcut) -> Boolean,
    actionEnabled: Boolean,
    actionInProgress: (DeviceShortcut) -> Boolean,
    editingDeviceId: String?,
    currentTarget: ConnectionTarget? = null,
    onClick: (DeviceShortcut) -> Unit,
    onLongClick: (DeviceShortcut) -> Unit,
    onAction: (DeviceShortcut) -> Unit,
    onCancelAction: (DeviceShortcut) -> Unit,
    onEditorSave: (DeviceShortcut, DeviceShortcut) -> Unit,
    onEditorDelete: (DeviceShortcut) -> Unit,
    onEditorCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
    ) {
        devices.forEach { device ->
            DeviceTile(
                device = device,
                isConnected = isConnected(device),
                actionEnabled = actionEnabled,
                actionInProgress = actionInProgress(device),
                editing = editingDeviceId == device.id,
                connectedAddress = currentTarget?.let {
                    if (device.matchesAddress(it)) it.toString()
                    else null
                },
                onClick = { onClick(device) },
                onLongClick = { onLongClick(device) },
                onAction = { onAction(device) },
                onCancelAction = { onCancelAction(device) },
                onEditorSave = { updated -> onEditorSave(device, updated) },
                onEditorDelete = { onEditorDelete(device) },
                onEditorCancel = onEditorCancel,
            )
        }
    }
}

@Composable
internal fun QuickConnectCard(
    input: String,
    onValueChange: (String) -> Unit,
    onFocusLost: (() -> Unit)? = null,
    onConnect: () -> Unit,
    onCancelConnect: () -> Unit,
    onAddDevice: () -> Unit,
    connecting: Boolean = false,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    Card(
        colors = CardDefaults.defaultColors(color = colorScheme.primaryContainer),
        pressFeedbackType =
            if (enabled) PressFeedbackType.Tilt
            else PressFeedbackType.None,
        insideMargin = PaddingValues(UiSpacing.Content),
        onClick = haptic::contextClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical)) {
            Row(
                modifier = Modifier.padding(horizontal = UiSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddLink,
                    contentDescription = stringResource(R.string.device_quick_connect_title),
                    tint = colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.device_quick_connect_title),
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onPrimaryContainer,
                )
            }
            SuperTextField(
                value = input,
                onValueChange = onValueChange,
                label = "IP:PORT",
                enabled = enabled,
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                onFocusLost = onFocusLost,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_add_device),
                    onClick = {
                        haptic.contextClick()
                        onAddDevice()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                )
                if (connecting) {
                    TextButton(
                        text = stringResource(R.string.button_cancel),
                        onClick = onCancelConnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(),
                    )
                } else {
                    TextButton(
                        text = stringResource(R.string.button_direct_connect),
                        onClick = {
                            haptic.confirm()
                            onConnect()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}
