package io.github.togo3.scrcaster.connection

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.togo3.scrcaster.R
import io.github.togo3.scrcaster.TvRemoteAccessibilityService

@Composable
internal fun TvRemoteSettings(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    fun serviceEnabled(): Boolean = manager?.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
    )?.any {
        ComponentName.unflattenFromString(it.id) == ComponentName(context, TvRemoteAccessibilityService::class.java)
    } == true
    var enabled by remember(context) { mutableStateOf(serviceEnabled()) }
    DisposableEffect(lifecycle, manager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = serviceEnabled()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Text(stringResource(if (enabled) R.string.tv_remote_controls_enabled else R.string.tv_remote_controls_disabled),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    ActionButton(stringResource(if (enabled) R.string.tv_remote_controls_manage else R.string.tv_remote_controls_enable), modifier) {
        onOpen()
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.tv_remote_settings_unavailable, Toast.LENGTH_LONG).show()
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // Some TV firmware exposes neither standard settings action; keep the app open.
            }
        }
    }
    Text(stringResource(R.string.tv_remote_controls_help), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
