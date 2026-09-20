package io.github.togo3.scrcaster

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Receives TV remote keys before vendor window policy can consume them.
 *
 * The service is deliberately limited to menu and OK keys while the TV playback Activity owns focus.
 * It never requests or inspects window content, and every other key passes through.
 */
class TvRemoteAccessibilityService : AccessibilityService() {
    companion object {
        private val connectionState = MutableStateFlow(false)
        internal val connected = connectionState.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectionState.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        connectionState.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connectionState.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Coocaa/Skyworth firmware can swallow these keys before Activity.dispatchKeyEvent.
        // Keep this service path as well as the Activity path: app-only long-OK handling
        // failed on the physical TV; enabling this service restored the popup (2026-09-20).
        // Recheck on hardware with Back, Menu, and a held/released OK before removing it.
        if (event.keyCode !in setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) return false
        return StreamActivity.handleTvKeyFromAccessibility(event)
    }
}
