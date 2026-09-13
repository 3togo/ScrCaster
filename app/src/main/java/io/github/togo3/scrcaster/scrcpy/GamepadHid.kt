package io.github.togo3.scrcaster.scrcpy

import android.view.InputDevice
import android.view.KeyEvent
import kotlin.math.roundToInt

object GamepadHid {
    const val VENDOR_ID = 0x045e // Microsoft
    const val PRODUCT_ID = 0x028e // Xbox 360 pad
    const val NAME = "Microsoft X-Box 360 Pad"
    const val REPORT_SIZE = 15

    // Reserved for future keyboard/mouse UHID devices (matches upstream ids).
    const val FIRST_ID = 3

    // Low 16 bits are forwarded "as is" into the report; the 4 dpad direction
    // bits live above them and are translated into a single hat-switch byte.
    private const val BUTTONS_MASK = 0xFFFF
    const val BIT_DPAD_UP = 0x10000
    const val BIT_DPAD_DOWN = 0x20000
    const val BIT_DPAD_LEFT = 0x40000
    const val BIT_DPAD_RIGHT = 0x80000
    const val DPAD_BITS_MASK = BIT_DPAD_UP or BIT_DPAD_DOWN or BIT_DPAD_LEFT or BIT_DPAD_RIGHT

    /**
     * True when [device] is a game controller. Android reports gamepad input across
     * several sources (SOURCE_GAMEPAD for buttons, SOURCE_JOYSTICK for axes, and
     * even KEYCODE_DPAD_* key events), so we key off the INPUT DEVICE's capabilities
     * rather than any single event source.
     */
    fun isGameController(device: InputDevice?): Boolean {
        if (device == null) return false
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
                (sources and InputDevice.SOURCE_JOYSTICK) != 0
    }

    /**
     * Standard gamepad report descriptor (Xbox-style). 15-byte reports:
     *   [0..1] left stick X, [2..3] left stick Y (0..65535)
     *   [4..5] right stick X, [6..7] right stick Y
     *   [8..9] L2, [10..11] R2 (0..32767)
     *   [12..13] buttons (16-bit little-endian), [14] hat switch (0..8)
     */
    val reportDescriptor: ByteArray = intArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x05, // Usage (Gamepad)
        0xA1, 0x01, // Collection (Application)
        0xA1, 0x00, // Collection (Physical)

        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x30, // Usage (X)   left stick x
        0x09, 0x31, // Usage (Y)   left stick y
        0x09, 0x33, // Usage (Rx)  right stick x
        0x09, 0x34, // Usage (Ry)  right stick y
        0x15, 0x00, // Logical Minimum (0)
        0x27, 0xFF, 0xFF, 0x00, 0x00, // Logical Maximum (65535)
        0x75, 0x10, // Report Size (16)
        0x95, 0x04, // Report Count (4)
        0x81, 0x02, // Input (Data, Variable, Absolute)

        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x32, // Usage (Z)
        0x09, 0x35, // Usage (Rz)
        0x15, 0x00, // Logical Minimum (0)
        0x26, 0xFF, 0x7F, // Logical Maximum (32767)
        0x75, 0x10, // Report Size (16)
        0x95, 0x02, // Report Count (2)
        0x81, 0x02, // Input (Data, Variable, Absolute)

        0x05, 0x09, // Usage Page (Buttons)
        0x19, 0x01, // Usage Minimum (1)
        0x29, 0x10, // Usage Maximum (16)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x95, 0x10, // Report Count (16)
        0x75, 0x01, // Report Size (1)
        0x81, 0x02, // Input (Data, Variable, Absolute)

        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x39, // Usage (Hat switch)
        0x15, 0x01, // Logical Minimum (1)
        0x25, 0x08, // Logical Maximum (8)
        0x75, 0x04, // Report Size (4)
        0x95, 0x01, // Report Count (1)
        0x81, 0x42, // Input (Data, Variable, Null State)

        0xC0, // End Collection
        0xC0, // End Collection
    ).map { it.toByte() }.toByteArray()

    /** Per-device gamepad state. */
    data class Slot(
        var buttons: Int = 0,
        var axisLeftX: Int = 0x8000,
        var axisLeftY: Int = 0x8000,
        var axisRightX: Int = 0x8000,
        var axisRightY: Int = 0x8000,
        var axisLeftTrigger: Int = 0,
        var axisRightTrigger: Int = 0,
    )

    /** Android keycode -> report bit (or dpad direction bit). */
    fun buttonBit(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> 0x0001 // south
        KeyEvent.KEYCODE_BUTTON_B -> 0x0002 // east
        KeyEvent.KEYCODE_BUTTON_X -> 0x0008 // west
        KeyEvent.KEYCODE_BUTTON_Y -> 0x0010 // north
        KeyEvent.KEYCODE_BUTTON_L1 -> 0x0040 // left shoulder
        KeyEvent.KEYCODE_BUTTON_R1 -> 0x0080 // right shoulder
        KeyEvent.KEYCODE_BUTTON_SELECT -> 0x0400 // back
        KeyEvent.KEYCODE_BUTTON_START -> 0x0800 // start
        KeyEvent.KEYCODE_BUTTON_MODE -> 0x1000 // guide
        KeyEvent.KEYCODE_BUTTON_THUMBL -> 0x2000 // left stick
        KeyEvent.KEYCODE_BUTTON_THUMBR -> 0x4000 // right stick
        KeyEvent.KEYCODE_DPAD_UP -> BIT_DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> BIT_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> BIT_DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> BIT_DPAD_RIGHT
        else -> null
    }

    /**
     * Hat-switch value for the current dpad directions:
     *      8 1 2
     *      7 0 3
     *      6 5 4
     */
    fun dpadValue(buttons: Int): Int {
        val up = buttons and BIT_DPAD_UP != 0
        val down = buttons and BIT_DPAD_DOWN != 0
        val left = buttons and BIT_DPAD_LEFT != 0
        val right = buttons and BIT_DPAD_RIGHT != 0
        return when {
            up && left -> 8
            up && right -> 2
            up -> 1
            down && left -> 6
            down && right -> 4
            down -> 5
            left -> 7
            right -> 3
            else -> 0
        }
    }

    fun buildReport(slot: Slot): ByteArray {
        val data = ByteArray(REPORT_SIZE)
        writeLe16(data, 0, slot.axisLeftX)
        writeLe16(data, 2, slot.axisLeftY)
        writeLe16(data, 4, slot.axisRightX)
        writeLe16(data, 6, slot.axisRightY)
        writeLe16(data, 8, slot.axisLeftTrigger)
        writeLe16(data, 10, slot.axisRightTrigger)
        writeLe16(data, 12, slot.buttons and BUTTONS_MASK)
        data[14] = dpadValue(slot.buttons).toByte()
        return data
    }

    /** Normalize a raw axis value into [0, outMax]. */
    fun rescale(value: Float, min: Float, max: Float, outMax: Int): Int {
        if (max <= min) return outMax / 2
        val normalized = ((value - min) / (max - min)).coerceIn(0f, 1f)
        return (normalized * outMax).roundToInt().coerceIn(0, outMax)
    }

    private fun writeLe16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}
