package io.github.togo3.scrcaster.widgets

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.togo3.scrcaster.R
import io.github.togo3.scrcaster.constants.UiAndroidKeycodes
import io.github.togo3.scrcaster.storage.AppSettings
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More

enum class VirtualButtonAction(
    val id: String,
    @field:StringRes val titleResId: Int,
    val icon: ImageVector,
    val keycode: Int?,
) {
    MORE(
        "more",
        R.string.vb_more,
        MiuixIcons.More,
        null,
    ),
    HOME(
        "home",
        R.string.vb_home,
        Icons.Rounded.Home,
        UiAndroidKeycodes.HOME,
    ),
    BACK(
        "back",
        R.string.vb_back,
        Icons.AutoMirrored.Rounded.ArrowBack,
        UiAndroidKeycodes.BACK,
    ),
    APP_SWITCH(
        "app_switch",
        R.string.vb_app_switch,
        Icons.Rounded.Apps,
        UiAndroidKeycodes.APP_SWITCH,
    ),
    MENU(
        "menu",
        R.string.vb_menu,
        Icons.Rounded.Menu,
        UiAndroidKeycodes.MENU,
    ),
    NOTIFICATION(
        "notification",
        R.string.vb_notifications,
        Icons.Rounded.Notifications,
        UiAndroidKeycodes.NOTIFICATION,
    ),
    VOLUME_UP(
        "volume_up",
        R.string.vb_volume_up,
        Icons.AutoMirrored.Rounded.VolumeUp,
        UiAndroidKeycodes.VOLUME_UP,
    ),
    VOLUME_DOWN(
        "volume_down",
        R.string.vb_volume_down,
        Icons.AutoMirrored.Rounded.VolumeDown,
        UiAndroidKeycodes.VOLUME_DOWN,
    ),
    VOLUME_MUTE(
        "volume_mute",
        R.string.vb_volume_mute,
        Icons.AutoMirrored.Rounded.VolumeOff,
        UiAndroidKeycodes.VOLUME_MUTE,
    ),
    POWER(
        "power",
        R.string.vb_lock_screen,
        Icons.Rounded.PowerSettingsNew,
        UiAndroidKeycodes.POWER,
    ),
    SCREENSHOT(
        "screenshot",
        R.string.vb_screenshot,
        Icons.Rounded.Screenshot,
        UiAndroidKeycodes.SYSRQ,
    ),
    PASSWORD_INPUT(
        "password_input",
        R.string.vb_fill_password,
        Icons.Rounded.Password,
        null,
    ),
    ALL_APPS(
        "all_apps",
        R.string.vb_all_apps,
        Icons.Rounded.Apps,
        null,
    ),
    RECENT_TASKS(
        "recent_tasks",
        R.string.vb_recent_tasks,
        Icons.Rounded.DashboardCustomize,
        null,
    ),
    TOGGLE_IME(
        "toggle_ime",
        R.string.vb_toggle_ime,
        Icons.Rounded.Keyboard,
        null,
    ),
    PASTE_LOCAL_CLIPBOARD(
        "paste_local_clipboard",
        R.string.vb_paste_clipboard,
        Icons.Rounded.ContentPaste,
        null,
    );
}

data class VirtualButtonItem(
    val action: VirtualButtonAction,
    val showOutside: Boolean,
)

object VirtualButtonActions {
    val all = VirtualButtonAction.entries

    private val byId = all.associateBy { it.id }

    fun parseStoredLayout(raw: String): List<VirtualButtonItem> {
        val parsed = raw.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { item ->
                val parts = item.trim().split(':')
                if (parts.size != 2) return@mapNotNull null
                val id = parts[0]
                val showOutside = parts[1] == "1"
                val action = byId[id] ?: return@mapNotNull null
                VirtualButtonItem(action, showOutside)
            }
            .orEmpty()
            .distinctBy { it.action.id }
        val base = parsed.ifEmpty {
            parseStoredLayout(AppSettings.VIRTUAL_BUTTONS_LAYOUT.defaultValue)
        }
        val missing = all
            .filterNot { action -> base.any { it.action == action } }
            .map { action ->
                VirtualButtonItem(
                    action = action,
                    showOutside = action == VirtualButtonAction.MORE,
                )
            }
        return base + missing
    }

    fun encodeStoredLayout(items: List<VirtualButtonItem>): String {
        return items.joinToString(",") { item ->
            "${item.action.id}:${if (item.showOutside) "1" else "0"}"
        }
    }

    fun splitLayout(items: List<VirtualButtonItem>): Pair<List<VirtualButtonAction>, List<VirtualButtonAction>> {
        val outside = items.filter { it.showOutside }.map { it.action }
        val more = items.filter { !it.showOutside }.map { it.action }
        return outside to more
    }
}
