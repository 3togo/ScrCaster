package io.github.togo3.scrcaster.storage

import org.junit.Assert.*
import org.junit.Test
import io.github.togo3.scrcaster.storage.AppSettings.FullscreenVirtualButtonDock as Dock

class FullscreenVirtualButtonDockTest {

    @Test
    fun fromStoredValueParsesValidRawValues() {
        assertEquals(Dock.FOLLOW_TOP, Dock.fromStoredValue("FOLLOW_TOP"))
        assertEquals(Dock.FOLLOW_BOTTOM, Dock.fromStoredValue("FOLLOW_BOTTOM"))
        assertEquals(Dock.FOLLOW_LEFT, Dock.fromStoredValue("FOLLOW_LEFT"))
        assertEquals(Dock.FOLLOW_RIGHT, Dock.fromStoredValue("FOLLOW_RIGHT"))
        assertEquals(Dock.FIXED_TOP, Dock.fromStoredValue("FIXED_TOP"))
        assertEquals(Dock.FIXED_BOTTOM, Dock.fromStoredValue("FIXED_BOTTOM"))
        assertEquals(Dock.FIXED_LEFT, Dock.fromStoredValue("FIXED_LEFT"))
        assertEquals(Dock.FIXED_RIGHT, Dock.fromStoredValue("FIXED_RIGHT"))
    }

    @Test
    fun fromStoredValueDefaultsToFollowBottomForUnknownValue() {
        assertEquals(Dock.FOLLOW_BOTTOM, Dock.fromStoredValue("UNKNOWN"))
        assertEquals(Dock.FOLLOW_BOTTOM, Dock.fromStoredValue(""))
        assertEquals(Dock.FOLLOW_BOTTOM, Dock.fromStoredValue("follow_top"))
    }

    @Test
    fun toStoredValueRoundTripsWithFromStoredValue() {
        Dock.entries.forEach { dock ->
            assertEquals(dock, Dock.fromStoredValue(dock.toStoredValue()))
        }
    }

    @Test
    fun modeIndexIsZeroForFollowAndOneForFixed() {
        assertEquals(0, Dock.FOLLOW_TOP.modeIndex)
        assertEquals(0, Dock.FOLLOW_BOTTOM.modeIndex)
        assertEquals(0, Dock.FOLLOW_LEFT.modeIndex)
        assertEquals(0, Dock.FOLLOW_RIGHT.modeIndex)
        assertEquals(1, Dock.FIXED_TOP.modeIndex)
        assertEquals(1, Dock.FIXED_BOTTOM.modeIndex)
        assertEquals(1, Dock.FIXED_LEFT.modeIndex)
        assertEquals(1, Dock.FIXED_RIGHT.modeIndex)
    }

    @Test
    fun directionIndexMapsCorrectly() {
        assertEquals(0, Dock.FOLLOW_TOP.directionIndex)
        assertEquals(0, Dock.FIXED_TOP.directionIndex)
        assertEquals(1, Dock.FOLLOW_BOTTOM.directionIndex)
        assertEquals(1, Dock.FIXED_BOTTOM.directionIndex)
        assertEquals(2, Dock.FOLLOW_LEFT.directionIndex)
        assertEquals(2, Dock.FIXED_LEFT.directionIndex)
        assertEquals(3, Dock.FOLLOW_RIGHT.directionIndex)
        assertEquals(3, Dock.FIXED_RIGHT.directionIndex)
    }

    @Test
    fun fromModeAndDirectionProducesCorrectDock() {
        assertEquals(Dock.FOLLOW_TOP, Dock.fromModeAndDirection(0, 0))
        assertEquals(Dock.FOLLOW_BOTTOM, Dock.fromModeAndDirection(0, 1))
        assertEquals(Dock.FOLLOW_LEFT, Dock.fromModeAndDirection(0, 2))
        assertEquals(Dock.FOLLOW_RIGHT, Dock.fromModeAndDirection(0, 3))
        assertEquals(Dock.FIXED_TOP, Dock.fromModeAndDirection(1, 0))
        assertEquals(Dock.FIXED_BOTTOM, Dock.fromModeAndDirection(1, 1))
        assertEquals(Dock.FIXED_LEFT, Dock.fromModeAndDirection(1, 2))
        assertEquals(Dock.FIXED_RIGHT, Dock.fromModeAndDirection(1, 3))
    }

    @Test
    fun fromModeAndDirectionDefaultsToBottomForInvalidDirection() {
        assertEquals(Dock.FOLLOW_BOTTOM, Dock.fromModeAndDirection(0, 99))
        assertEquals(Dock.FIXED_BOTTOM, Dock.fromModeAndDirection(1, -1))
    }

    @Test
    fun fromModeAndDirectionRoundTripsWithModeAndDirectionIndices() {
        Dock.entries.forEach { dock ->
            val reconstructed = Dock.fromModeAndDirection(dock.modeIndex, dock.directionIndex)
            assertEquals(dock, reconstructed)
        }
    }

    @Test
    fun isFixedIsFalseForFollowAndTrueForFixed() {
        assertFalse(Dock.FOLLOW_TOP.isFixed)
        assertFalse(Dock.FOLLOW_BOTTOM.isFixed)
        assertFalse(Dock.FOLLOW_LEFT.isFixed)
        assertFalse(Dock.FOLLOW_RIGHT.isFixed)
        assertTrue(Dock.FIXED_TOP.isFixed)
        assertTrue(Dock.FIXED_BOTTOM.isFixed)
        assertTrue(Dock.FIXED_LEFT.isFixed)
        assertTrue(Dock.FIXED_RIGHT.isFixed)
    }

    @Test
    fun rawValueMatchesEnumName() {
        Dock.entries.forEach { dock ->
            assertEquals(dock.name, dock.rawValue)
        }
    }

    @Test
    fun fromBundleParsesValidDockValue() {
        val bundle = AppSettings.Bundle(
            languageTag = "",
            themeBaseIndex = 0,
            monet = false,
            monetSeedIndex = 0,
            monetPaletteStyle = 0,
            monetColorSpec = 0,
            squircle = false,
            blur = 0,
            navTransitionStyle = 0,
            swipeBack = false,
            floatingBottomBar = false,
            floatingBottomBarBlur = false,
            allowLandscapeOnTallPhones = false,
            lowLatency = false,
            downsizeOnDecodeError = false,
            fullscreenDebugInfo = false,
            hideSimpleConfigItems = false,
            previewCardOnTop = false,
            devicePreviewCardHeightDp = 0,
            realtimeClipboardSyncToDevice = false,
            fullscreenControlIgnoreSystemRotationLock = false,
            fullscreenControlBackToDevice = false,
            showFullscreenVirtualButtons = false,
            fullscreenVirtualButtonHeightDp = 0,
            fullscreenVirtualButtonDock = "FIXED_LEFT",
            showFullscreenFloatingButton = false,
            fullscreenFloatingButtonSizeDp = 0,
            fullscreenFloatingButtonBackgroundAlphaPercent = 0,
            fullscreenFloatingButtonRingAlphaPercent = 0,
            fullscreenCompatibilityMode = false,
            fullscreenFloatingButtonXFraction = 0f,
            fullscreenFloatingButtonYFraction = 0f,
            previewVirtualButtonShowText = false,
            virtualButtonsLayout = "",
            deviceTwoPaneConfigOnRight = false,
            customServerUri = "",
            customServerVersion = "",
            serverRemotePath = "",
            adbKeyName = "",
            adbPairingAutoDiscoverOnDialogOpen = false,
            adbQrCameraScanEnabled = false,
            adbAutoReconnectPairedDevice = false,
            adbMdnsLanDiscovery = false,
            adbAutoLoadAppListOnConnect = false,
            adbFlowControlWindow = 0,
            terminalFontSizeSp = 0f,
            terminalFontDisplayName = "",
            passwordRequireAuth = false,
            fileManagerSortBy = "",
            fileManagerSortDescending = false,
            lastUpdateCheckAt = 0L,
            clearLogsOnExit = false,
            hideDeviceLogs = false,
            gamepadDeviceName = "",
        )
        assertEquals(Dock.FIXED_LEFT, Dock.fromBundle(bundle))
    }

    @Test
    fun fromBundleDefaultsToFollowBottomForUnknownValue() {
        val bundle = AppSettings.Bundle(
            languageTag = "",
            themeBaseIndex = 0,
            monet = false,
            monetSeedIndex = 0,
            monetPaletteStyle = 0,
            monetColorSpec = 0,
            squircle = false,
            blur = 0,
            navTransitionStyle = 0,
            swipeBack = false,
            floatingBottomBar = false,
            floatingBottomBarBlur = false,
            allowLandscapeOnTallPhones = false,
            lowLatency = false,
            downsizeOnDecodeError = false,
            fullscreenDebugInfo = false,
            hideSimpleConfigItems = false,
            previewCardOnTop = false,
            devicePreviewCardHeightDp = 0,
            realtimeClipboardSyncToDevice = false,
            fullscreenControlIgnoreSystemRotationLock = false,
            fullscreenControlBackToDevice = false,
            showFullscreenVirtualButtons = false,
            fullscreenVirtualButtonHeightDp = 0,
            fullscreenVirtualButtonDock = "BOGUS",
            showFullscreenFloatingButton = false,
            fullscreenFloatingButtonSizeDp = 0,
            fullscreenFloatingButtonBackgroundAlphaPercent = 0,
            fullscreenFloatingButtonRingAlphaPercent = 0,
            fullscreenCompatibilityMode = false,
            fullscreenFloatingButtonXFraction = 0f,
            fullscreenFloatingButtonYFraction = 0f,
            previewVirtualButtonShowText = false,
            virtualButtonsLayout = "",
            deviceTwoPaneConfigOnRight = false,
            customServerUri = "",
            customServerVersion = "",
            serverRemotePath = "",
            adbKeyName = "",
            adbPairingAutoDiscoverOnDialogOpen = false,
            adbQrCameraScanEnabled = false,
            adbAutoReconnectPairedDevice = false,
            adbMdnsLanDiscovery = false,
            adbAutoLoadAppListOnConnect = false,
            adbFlowControlWindow = 0,
            terminalFontSizeSp = 0f,
            terminalFontDisplayName = "",
            passwordRequireAuth = false,
            fileManagerSortBy = "",
            fileManagerSortDescending = false,
            lastUpdateCheckAt = 0L,
            clearLogsOnExit = false,
            hideDeviceLogs = false,
            gamepadDeviceName = "",
        )
        assertEquals(Dock.FOLLOW_BOTTOM, Dock.fromBundle(bundle))
    }
}
