package io.github.togo3.scrcaster.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.togo3.scrcaster.R
import io.github.togo3.scrcaster.constants.ScrcpyPresets
import io.github.togo3.scrcaster.constants.UiSpacing
import io.github.togo3.scrcaster.miuix.SpinnerEntry
import io.github.togo3.scrcaster.models.DeviceShortcuts
import io.github.togo3.scrcaster.models.ScrcpyOptions.Crop
import io.github.togo3.scrcaster.models.ScrcpyOptions.NewDisplay
import io.github.togo3.scrcaster.scaffolds.*
import io.github.togo3.scrcaster.core.AspectRatio
import io.github.togo3.scrcaster.scrcpy.ClientOptions
import io.github.togo3.scrcaster.scrcpy.Scrcpy
import io.github.togo3.scrcaster.scrcpy.Shared.*
import io.github.togo3.scrcaster.services.AppRuntime
import io.github.togo3.scrcaster.storage.ScrcpyOptions
import io.github.togo3.scrcaster.storage.ScrcpyProfiles
import io.github.togo3.scrcaster.storage.Settings
import io.github.togo3.scrcaster.storage.Storage.quickDevices
import io.github.togo3.scrcaster.storage.Storage.scrcpyOptions
import io.github.togo3.scrcaster.storage.Storage.scrcpyProfiles
import io.github.togo3.scrcaster.ui.*
import io.github.togo3.scrcaster.widgets.RecordPreferences
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.math.roundToInt

internal enum class ProfileDialogMode {
    Create,
    Rename,
}

@Composable
internal fun ProfileNameDialog(
    mode: ProfileDialogMode?,
    initialInput: String,
    profiles: List<ScrcpyProfiles.Profile>,
    initialCopySourceProfileId: String?,
    onDismissRequest: () -> Unit,
    onConfirm: (String, String?) -> Unit,
) {
    if (mode == null) return

    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val textDefault = stringResource(R.string.text_default)

    var input by rememberSaveable(mode, initialInput) { mutableStateOf(initialInput) }
    val profileNames = remember(profiles) { profiles.map { it.name } }
    val profileIds = remember(profiles) { profiles.map { it.id } }
    val copySourceItems = remember(profileNames) {
        listOf(textDefault) + profileNames
    }
    var copySourceProfileId by rememberSaveable(mode, initialCopySourceProfileId) {
        mutableStateOf(initialCopySourceProfileId)
    }
    val copySourceDropdownIndex = remember(copySourceProfileId, profileIds) {
        copySourceProfileId
            ?.let { profileIds.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) }
            ?: 0
    }

    OverlayDialog(
        show = true,
        title = when (mode) {
            ProfileDialogMode.Create -> stringResource(R.string.scrcpyopt_new_profile)
            ProfileDialogMode.Rename -> stringResource(R.string.scrcpyopt_rename_profile)
        },
        summary = stringResource(R.string.scrcpyopt_duplicate_name_hint),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(UiSpacing.ContentVertical),
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.scrcpyopt_profile_name),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(mode == ProfileDialogMode.Create) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.scrcpyopt_copy_from),
                    items = copySourceItems,
                    selectedIndex = copySourceDropdownIndex,
                    onSelectedIndexChange = { index ->
                        copySourceProfileId = if (index == 0) {
                            null
                        } else {
                            profileIds.getOrElse(index - 1) {
                                ScrcpyOptions.GLOBAL_PROFILE_ID
                            }
                        }
                    },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
            ) {
                TextButton(
                    text = stringResource(R.string.button_cancel),
                    onClick = {
                        haptic.contextClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.button_confirm),
                    onClick = {
                        haptic.confirm()
                        onConfirm(input, copySourceProfileId)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
internal fun ManageProfilesSheet(
    show: Boolean,
    profiles: List<ScrcpyProfiles.Profile>,
    selectedProfileId: String,
    onDismissRequest: () -> Unit,
    onCreateProfile: () -> Unit,
    onRenameProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onMoveProfile: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = stringResource(R.string.scrcpyopt_manage_profiles),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
        endAction = {
            IconButton(onClick = onCreateProfile) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.scrcpyopt_new_profile),
                )
            }
        },
    ) {
        val textCurrent = stringResource(R.string.scrcpyopt_current_profile)
        val textRename = stringResource(R.string.scrcpyopt_rename_profile)
        val textDelete = stringResource(R.string.scrcpyopt_delete_profile)
        ReorderableList(
            itemsProvider = {
                profiles.map { profile ->
                    ReorderableList.Item(
                        id = profile.id,
                        title = profile.name,
                        subtitle =
                            if (profile.id == selectedProfileId) textCurrent
                            else "",
                        onClick =
                            if (profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID) {
                                { onRenameProfile(profile.id) }
                            } else null,
                        dragEnabled = profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID,
                        endActions = buildList {
                            if (profile.id != ScrcpyOptions.GLOBAL_PROFILE_ID) {
                                add(
                                    ReorderableList.EndAction.Icon(
                                        icon = Icons.Rounded.Edit,
                                        contentDescription = textRename,
                                        onClick = { onRenameProfile(profile.id) },
                                    ),
                                )
                                add(
                                    ReorderableList.EndAction.Icon(
                                        icon = Icons.Rounded.DeleteOutline,
                                        contentDescription = textDelete,
                                        onClick = { onDeleteProfile(profile.id) },
                                    ),
                                )
                            }
                        },
                    )
                }
            },
            onSettle = onMoveProfile,
        ).invoke()
        Spacer(Modifier.height(UiSpacing.SheetBottom))
    }
}

@Composable
internal fun DeleteProfileDialog(
    show: Boolean,
    profileName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    OverlayDialog(
        show = show,
        title = stringResource(R.string.scrcpyopt_delete_profile),
        summary = stringResource(R.string.scrcpyopt_delete_confirm, profileName),
        defaultWindowInsetsPadding = false,
        onDismissRequest = onDismissRequest,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.ContentHorizontal),
        ) {
            TextButton(
                text = stringResource(R.string.button_cancel),
                onClick = {
                    haptic.contextClick()
                    onDismissRequest()
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.button_delete),
                onClick = {
                    haptic.confirm()
                    onConfirm()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
