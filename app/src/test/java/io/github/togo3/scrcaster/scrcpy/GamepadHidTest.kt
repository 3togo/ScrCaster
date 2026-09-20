package io.github.togo3.scrcaster.scrcpy

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadHidTest {

    // ── buttonBit ──────────────────────────────────────────────

    @Test
    fun buttonBit_mapsAllKnownKeycodes() {
        assertEquals(0x0001, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(0x0002, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(0x0008, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(0x0010, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(0x0040, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(0x0080, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(0x0400, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(0x0800, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(0x1000, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_MODE))
        assertEquals(0x2000, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertEquals(0x4000, GamepadHid.buttonBit(KeyEvent.KEYCODE_BUTTON_THUMBR))
    }

    @Test
    fun buttonBit_mapsDpadKeycodesToHighBits() {
        assertEquals(GamepadHid.BIT_DPAD_UP, GamepadHid.buttonBit(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(GamepadHid.BIT_DPAD_DOWN, GamepadHid.buttonBit(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(GamepadHid.BIT_DPAD_LEFT, GamepadHid.buttonBit(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(GamepadHid.BIT_DPAD_RIGHT, GamepadHid.buttonBit(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun buttonBit_returnsNullForUnknownKeycode() {
        assertNull(GamepadHid.buttonBit(KeyEvent.KEYCODE_UNKNOWN))
        assertNull(GamepadHid.buttonBit(-1))
        assertNull(GamepadHid.buttonBit(0))
        assertNull(GamepadHid.buttonBit(99999))
    }

    // ── dpadValue ──────────────────────────────────────────────

    @Test
    fun dpadValue_returnsZeroWhenNoDirection() {
        assertEquals(0, GamepadHid.dpadValue(0))
    }

    @Test
    fun dpadValue_mapsCardinalDirections() {
        assertEquals(1, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_UP))
        assertEquals(5, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_DOWN))
        assertEquals(7, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_LEFT))
        assertEquals(3, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_RIGHT))
    }

    @Test
    fun dpadValue_mapsDiagonalCombinations() {
        assertEquals(8, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_UP or GamepadHid.BIT_DPAD_LEFT))
        assertEquals(2, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_UP or GamepadHid.BIT_DPAD_RIGHT))
        assertEquals(6, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_DOWN or GamepadHid.BIT_DPAD_LEFT))
        assertEquals(4, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_DOWN or GamepadHid.BIT_DPAD_RIGHT))
    }

    @Test
    fun dpadValue_ignoresLowButtonBits() {
        // Low 16 bits are button bits, not dpad bits — they must not affect dpad value.
        assertEquals(1, GamepadHid.dpadValue(GamepadHid.BIT_DPAD_UP or 0xFFFF))
        assertEquals(0, GamepadHid.dpadValue(0xFFFF))
    }

    // ── buildReport ────────────────────────────────────────────

    @Test
    fun buildReport_hasCorrectSize() {
        val report = GamepadHid.buildReport(GamepadHid.Slot())
        assertEquals(GamepadHid.REPORT_SIZE, report.size)
        assertEquals(15, report.size)
    }

    @Test
    fun buildReport_writesAxisValuesAtCorrectOffsets() {
        val slot = GamepadHid.Slot(
            axisLeftX = 0x1234,
            axisLeftY = 0x5678,
            axisRightX = 0x9ABC,
            axisRightY = 0xDEF0,
            axisLeftTrigger = 0x0102,
            axisRightTrigger = 0x0304,
        )
        val report = GamepadHid.buildReport(slot)

        // Left stick X at [0..1] (little-endian)
        assertEquals(0x34, report[0].toInt() and 0xFF)
        assertEquals(0x12, report[1].toInt() and 0xFF)
        // Left stick Y at [2..3]
        assertEquals(0x78, report[2].toInt() and 0xFF)
        assertEquals(0x56, report[3].toInt() and 0xFF)
        // Right stick X at [4..5]
        assertEquals(0xBC, report[4].toInt() and 0xFF)
        assertEquals(0x9A, report[5].toInt() and 0xFF)
        // Right stick Y at [6..7]
        assertEquals(0xF0, report[6].toInt() and 0xFF)
        assertEquals(0xDE, report[7].toInt() and 0xFF)
        // Left trigger at [8..9]
        assertEquals(0x02, report[8].toInt() and 0xFF)
        assertEquals(0x01, report[9].toInt() and 0xFF)
        // Right trigger at [10..11]
        assertEquals(0x04, report[10].toInt() and 0xFF)
        assertEquals(0x03, report[11].toInt() and 0xFF)
    }

    @Test
    fun buildReport_writesButtonsLow16BitsAtOffset12() {
        val slot = GamepadHid.Slot(buttons = 0x0001 or 0x0008) // A + X
        val report = GamepadHid.buildReport(slot)

        assertEquals(0x09, report[12].toInt() and 0xFF)
        assertEquals(0x00, report[13].toInt() and 0xFF)
    }

    @Test
    fun buildReport_stripsDpadBitsFromButtonsField() {
        // Dpad bits live above bit 16 and must NOT appear in the buttons field.
        val slot = GamepadHid.Slot(buttons = GamepadHid.BIT_DPAD_UP or 0x0001)
        val report = GamepadHid.buildReport(slot)

        assertEquals(0x01, report[12].toInt() and 0xFF)
        assertEquals(0x00, report[13].toInt() and 0xFF)
    }

    @Test
    fun buildReport_writesDpadValueAtOffset14() {
        val slotUp = GamepadHid.Slot(buttons = GamepadHid.BIT_DPAD_UP)
        val reportUp = GamepadHid.buildReport(slotUp)
        assertEquals(1, reportUp[14].toInt() and 0xFF)

        val slotDiag = GamepadHid.Slot(buttons = GamepadHid.BIT_DPAD_DOWN or GamepadHid.BIT_DPAD_RIGHT)
        val reportDiag = GamepadHid.buildReport(slotDiag)
        assertEquals(4, reportDiag[14].toInt() and 0xFF)

        val slotNone = GamepadHid.Slot(buttons = 0)
        val reportNone = GamepadHid.buildReport(slotNone)
        assertEquals(0, reportNone[14].toInt() and 0xFF)
    }

    @Test
    fun buildReport_defaultSlotProducesCenteredAxes() {
        val report = GamepadHid.buildReport(GamepadHid.Slot())

        // All sticks centered at 0x8000
        for (offset in listOf(0, 2, 4, 6)) {
            assertEquals(0x00, report[offset].toInt() and 0xFF)
            assertEquals(0x80, report[offset + 1].toInt() and 0xFF)
        }
        // Triggers at 0
        assertEquals(0x00, report[8].toInt() and 0xFF)
        assertEquals(0x00, report[9].toInt() and 0xFF)
        assertEquals(0x00, report[10].toInt() and 0xFF)
        assertEquals(0x00, report[11].toInt() and 0xFF)
        // No buttons, no dpad
        assertEquals(0x00, report[12].toInt() and 0xFF)
        assertEquals(0x00, report[13].toInt() and 0xFF)
        assertEquals(0x00, report[14].toInt() and 0xFF)
    }

    // ── rescale ────────────────────────────────────────────────

    @Test
    fun rescale_normalRangeMapsCorrectly() {
        // value at min -> 0
        assertEquals(0, GamepadHid.rescale(-1f, -1f, 1f, 65535))
        // value at max -> outMax
        assertEquals(65535, GamepadHid.rescale(1f, -1f, 1f, 65535))
        // value at midpoint -> outMax/2
        assertEquals(32768, GamepadHid.rescale(0f, -1f, 1f, 65535))
    }

    @Test
    fun rescale_minEqualsMaxReturnsMidpoint() {
        assertEquals(32767, GamepadHid.rescale(0.5f, 1f, 1f, 65535))
        assertEquals(500, GamepadHid.rescale(0.5f, 1f, 1f, 1000))
        assertEquals(0, GamepadHid.rescale(0.5f, 1f, 1f, 0))
    }

    @Test
    fun rescale_clampsValuesOutsideRange() {
        // Below min -> 0
        assertEquals(0, GamepadHid.rescale(-2f, -1f, 1f, 65535))
        // Above max -> outMax
        assertEquals(65535, GamepadHid.rescale(2f, -1f, 1f, 65535))
    }

    @Test
    fun rescale_differentOutputMax() {
        assertEquals(127, GamepadHid.rescale(1f, -1f, 1f, 127))
        assertEquals(0, GamepadHid.rescale(-1f, -1f, 1f, 127))
        assertEquals(64, GamepadHid.rescale(0f, -1f, 1f, 127))
    }

    // ── Slot default values ────────────────────────────────────

    @Test
    fun slot_defaultValuesAreCorrect() {
        val slot = GamepadHid.Slot()
        assertEquals(0, slot.buttons)
        assertEquals(0x8000, slot.axisLeftX)
        assertEquals(0x8000, slot.axisLeftY)
        assertEquals(0x8000, slot.axisRightX)
        assertEquals(0x8000, slot.axisRightY)
        assertEquals(0, slot.axisLeftTrigger)
        assertEquals(0, slot.axisRightTrigger)
    }

    @Test
    fun slot_customValuesArePreserved() {
        val slot = GamepadHid.Slot(
            buttons = 0x0001,
            axisLeftX = 0xFFFF,
            axisLeftY = 0x0000,
            axisRightX = 0x1234,
            axisRightY = 0x5678,
            axisLeftTrigger = 0x7FFF,
            axisRightTrigger = 0x0001,
        )
        assertEquals(0x0001, slot.buttons)
        assertEquals(0xFFFF, slot.axisLeftX)
        assertEquals(0x0000, slot.axisLeftY)
        assertEquals(0x1234, slot.axisRightX)
        assertEquals(0x5678, slot.axisRightY)
        assertEquals(0x7FFF, slot.axisLeftTrigger)
        assertEquals(0x0001, slot.axisRightTrigger)
    }

    // ── reportDescriptor ───────────────────────────────────────

    @Test
    fun reportDescriptor_isNotEmpty() {
        assert(GamepadHid.reportDescriptor.isNotEmpty())
    }

    @Test
    fun reportDescriptor_startsWithGenericDesktopGamepadUsage() {
        val desc = GamepadHid.reportDescriptor
        // 0x05, 0x01 = Usage Page (Generic Desktop)
        assertEquals(0x05, desc[0].toInt() and 0xFF)
        assertEquals(0x01, desc[1].toInt() and 0xFF)
        // 0x09, 0x05 = Usage (Gamepad)
        assertEquals(0x09, desc[2].toInt() and 0xFF)
        assertEquals(0x05, desc[3].toInt() and 0xFF)
    }

    @Test
    fun reportDescriptor_endsWithEndCollectionTags() {
        val desc = GamepadHid.reportDescriptor
        val len = desc.size
        // Last two bytes should be 0xC0 (End Collection)
        assertEquals(0xC0, desc[len - 2].toInt() and 0xFF)
        assertEquals(0xC0, desc[len - 1].toInt() and 0xFF)
    }

    @Test
    fun reportDescriptor_containsHatSwitchUsage() {
        val desc = GamepadHid.reportDescriptor
        // Find 0x09, 0x39 (Usage Hat switch)
        var found = false
        for (i in 0 until desc.size - 1) {
            if ((desc[i].toInt() and 0xFF) == 0x09 && (desc[i + 1].toInt() and 0xFF) == 0x39) {
                found = true
                break
            }
        }
        assert(found) { "Hat switch usage not found in report descriptor" }
    }
}
