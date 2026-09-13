package io.github.miuzarte.scrcpyforandroid

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Receives TV remote keys before vendor window policy can consume them.
 *
 * The service is deliberately limited to Back while the TV playback Activity owns focus.
 * It never requests or inspects window content, and every other key passes through.
 */
class TvRemoteAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        return StreamActivity.handleTvBackFromAccessibility(event)
    }
}
