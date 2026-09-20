package io.github.togo3.scrcaster

import android.content.ComponentName
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class TvRemoteAccess(val message: Int) {
    DISABLED(R.string.tv_remote_controls_disabled),
    WAITING(R.string.tv_remote_controls_waiting),
    ACTIVE(R.string.tv_remote_controls_enabled),
}

internal fun tvRemoteAccess(enabled: Boolean, connected: Boolean): TvRemoteAccess = when {
    !enabled -> TvRemoteAccess.DISABLED
    connected -> TvRemoteAccess.ACTIVE
    else -> TvRemoteAccess.WAITING
}

@Composable
internal fun rememberTvRemoteAccess(): TvRemoteAccess {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resolver = context.contentResolver
    fun isEnabled(): Boolean {
        val component = ComponentName(context, TvRemoteAccessibilityService::class.java)
        return Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    }
    var enabled by remember(context) { mutableStateOf(isEnabled()) }
    val connected by TvRemoteAccessibilityService.connected.collectAsState()
    DisposableEffect(resolver, lifecycle) {
        // Observe our component setting too: the global flag can stay on for another service.
        // Configured access alone does not prove the TV has bound our key handler.
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = isEnabled() }
        }
        listOf(Settings.Secure.ACCESSIBILITY_ENABLED, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .forEach { resolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer) }
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = isEnabled()
        }
        lifecycle.addObserver(resume)
        enabled = isEnabled()
        onDispose {
            resolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(resume)
        }
    }
    return tvRemoteAccess(enabled, connected)
}
