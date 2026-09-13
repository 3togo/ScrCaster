package io.github.togo3.scrcaster.pages

import androidx.compose.runtime.staticCompositionLocalOf

class ServerPicker(
    val pick: () -> Unit,
)

class TerminalFontPicker(
    val pick: () -> Unit,
)

val LocalServerPicker = staticCompositionLocalOf<ServerPicker> {
    error("No ServerPicker provided")
}

val LocalTerminalFontPicker = staticCompositionLocalOf<TerminalFontPicker> {
    error("No TerminalFontPicker provided")
}
