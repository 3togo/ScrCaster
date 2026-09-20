package io.github.togo3.scrcaster

import android.view.KeyEvent

/** Delay OK forwarding until release so opening the receiver menu never clicks the phone. */
internal class TvMenuHold {
    enum class Result { NONE, TAP, MENU }
    private var pressedCode: Int? = null
    private var startedAt = 0L
    private var systemLongPress = false

    fun key(action: Int, code: Int, time: Long, repeat: Int = 0, canceled: Boolean = false, longPress: Boolean = false): Result {
        if (canceled) { cancel(); return Result.NONE }
        if (action == KeyEvent.ACTION_DOWN && repeat == 0 && pressedCode == null) {
            pressedCode = code
            startedAt = time
        }
        if (code == pressedCode && longPress) systemLongPress = true
        if (action != KeyEvent.ACTION_UP || code != pressedCode) return Result.NONE
        // Physical Coocaa remotes may emit only DOWN and UP, with no repeat/long-press flag.
        // Keep elapsed-time detection as well as Android's explicit long-press signal.
        val held = systemLongPress || time - startedAt >= 700
        cancel()
        return if (held) Result.MENU else Result.TAP
    }

    fun cancel() { pressedCode = null; systemLongPress = false }
}
