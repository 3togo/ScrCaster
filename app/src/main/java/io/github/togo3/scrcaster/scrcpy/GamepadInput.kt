package io.github.togo3.scrcaster.scrcpy

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Captures gamepad key/motion events from the controller-side device and forwards
 * them as a UHID virtual gamepad. A UHID_CREATE is sent lazily on the first event
 * of a controller; UHID_INPUT on every state change; UHID_DESTROY on [destroy].
 *
 * The scrcpy server has no dedicated gamepad control message; it forwards a virtual
 * HID gamepad over the UHID messages, mirroring upstream scrcpy `--gamepad=uhid`.
 * HID descriptor/report details live in [GamepadHid].
 */
class GamepadInputHandler(
    private val scope: CoroutineScope,
    private val deviceName: () -> String,
    private val onUhidCreate: suspend (
        id: Int,
        vendorId: Int,
        productId: Int,
        name: String,
        reportDesc: ByteArray,
    ) -> Unit,
    private val onUhidInput: suspend (id: Int, data: ByteArray) -> Unit,
    private val onUhidDestroy: suspend (id: Int) -> Unit,
    private val onDebugChanged: ((String) -> Unit)? = null,
) {
    private class DeviceState {
        val slot = GamepadHid.Slot()
        var created = false
    }

    private val states = HashMap<Int, DeviceState>()
    private val idByDevice = HashMap<Int, Int>()
    private var nextId = GamepadHid.FIRST_ID

    // Serializes UHID_CREATE + UHID_INPUT for all devices so an input can never be
    // written ahead of the create of a controller (which would make the server drop it).
    private val sendMutex = Mutex()

    /** Handle a gamepad button key event. Returns true if it was consumed. */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!GamepadHid.isGameController(InputDevice.getDevice(event.deviceId))) return false
        val bit = GamepadHid.buttonBit(event.keyCode) ?: return false
        val deviceId = event.deviceId
        val state = ensureDevice(deviceId) ?: return true
        when (event.action) {
            KeyEvent.ACTION_DOWN -> state.slot.buttons = state.slot.buttons or bit
            KeyEvent.ACTION_UP -> state.slot.buttons = state.slot.buttons and bit.inv()
            else -> return true
        }
        send(deviceId, state)
        return true
    }

    /** Handle a gamepad axis motion event. Returns true if it was consumed. */
    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (!GamepadHid.isGameController(InputDevice.getDevice(event.deviceId))) return false
        val deviceId = event.deviceId
        val state = ensureDevice(deviceId) ?: return true
        val device = InputDevice.getDevice(deviceId)
        val slot = state.slot

        slot.axisLeftX = readAxis(device, event, MotionEvent.AXIS_X, 65535) ?: slot.axisLeftX
        slot.axisLeftY = readAxis(device, event, MotionEvent.AXIS_Y, 65535) ?: slot.axisLeftY
        slot.axisRightX = readAxis(device, event, MotionEvent.AXIS_RX, 65535)
            ?: readAxis(device, event, MotionEvent.AXIS_Z, 65535)
                    ?: slot.axisRightX
        slot.axisRightY = readAxis(device, event, MotionEvent.AXIS_RY, 65535)
            ?: readAxis(device, event, MotionEvent.AXIS_RZ, 65535)
                    ?: slot.axisRightY
        slot.axisLeftTrigger = readAxis(device, event, MotionEvent.AXIS_LTRIGGER, 32767)
            ?: slot.axisLeftTrigger
        slot.axisRightTrigger = readAxis(device, event, MotionEvent.AXIS_RTRIGGER, 32767)
            ?: slot.axisRightTrigger

        // D-pad: some controllers report it only as a hat axis (AXIS_HAT_X/Y),
        // while others (or the OS compatibility layer) report it as KEYCODE_DPAD_*
        // key events. Handle the hat axis here so the d-pad still works either way.
        val hatX = readRawAxis(device, event, MotionEvent.AXIS_HAT_X)
        val hatY = readRawAxis(device, event, MotionEvent.AXIS_HAT_Y)
        if (hatX != null || hatY != null) {
            val hx = hatX ?: 0f
            val hy = hatY ?: 0f
            // Recompute the 4 d-pad direction bits from the analog hat position.
            slot.buttons = slot.buttons and GamepadHid.DPAD_BITS_MASK.inv()
            if (hy < -0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_UP
            if (hy > 0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_DOWN
            if (hx < -0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_LEFT
            if (hx > 0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_RIGHT
        }

        send(deviceId, state)
        return true
    }

    /** Destroy all created UHID devices (call on session end / screen dispose). */
    fun destroy() {
        val ids = idByDevice.values.toList()
        idByDevice.clear()
        states.clear()
        for (id in ids) {
            scope.launch {
                try {
                    onUhidDestroy(id)
                } catch (t: Throwable) {
                    Log.w(TAG, "uhidDestroy failed", t)
                }
            }
        }
    }

    private fun ensureDevice(deviceId: Int): DeviceState? {
        var state = states[deviceId]
        if (state == null) {
            val id = nextId++
            if (id > 0xFFFF) {
                // Exhausted ids; drop new controllers rather than breaking the stream.
                return null
            }
            idByDevice[deviceId] = id
            state = DeviceState()
            states[deviceId] = state
        }
        return state
    }

    private fun send(deviceId: Int, state: DeviceState) {
        val id = idByDevice[deviceId] ?: return
        val report = GamepadHid.buildReport(state.slot)
        scope.launch {
            // Hold the lock across create+input so the create is always transmitted
            // before the first input of a controller (even under a burst of events).
            sendMutex.withLock {
                if (!state.created) {
                    try {
                        onUhidCreate(
                            id,
                            GamepadHid.VENDOR_ID,
                            GamepadHid.PRODUCT_ID,
                            deviceName(),
                            GamepadHid.reportDescriptor,
                        )
                        state.created = true
                    } catch (t: Throwable) {
                        Log.w(TAG, "uhidCreate failed", t)
                        // Keep created=false so a later event retries the create.
                    }
                }
                if (state.created) {
                    try {
                        onUhidInput(id, report)
                    } catch (t: Throwable) {
                        Log.w(TAG, "uhidInput failed", t)
                    }
                }
            }
        }
        notifyDebug()
    }

    private fun notifyDebug() {
        val callback = onDebugChanged ?: return
        callback(buildDebug())
    }

    private fun buildDebug(): String {
        if (states.isEmpty()) return ""
        return states.entries
            .sortedBy { it.key }
            .joinToString("\n") { (deviceId, st) ->
                val id = idByDevice[deviceId] ?: return@joinToString ""
                val s = st.slot
                buildString {
                    append("GP#$id")
                    append(" btn=0x").append((s.buttons and 0xFFFF).toString(16).padStart(4, '0'))
                    append(" L=(").append(s.axisLeftX).append(',').append(s.axisLeftY).append(')')
                    append(" R=(").append(s.axisRightX).append(',').append(s.axisRightY).append(')')
                    append(" LT=").append(s.axisLeftTrigger)
                    append(" RT=").append(s.axisRightTrigger)
                    append(" DPAD=").append(GamepadHid.dpadValue(s.buttons))
                }
            }
    }

    private fun readAxis(
        device: InputDevice?,
        event: MotionEvent,
        axis: Int,
        outMax: Int,
    ): Int? {
        // Only consider axes the controller actually exposes (via its motion ranges),
        // so an absent axis falls through to the next fallback axis.
        val range = device?.let { runCatching { it.getMotionRange(axis) }.getOrNull() }
            ?: return null
        val value = event.getAxisValue(axis)
        val min = range.min
        val max = range.max
        return GamepadHid.rescale(value, min, max, outMax)
    }

    /** Raw (unscaled) axis value, or null when the device does not expose [axis]. */
    private fun readRawAxis(
        device: InputDevice?,
        event: MotionEvent,
        axis: Int,
    ): Float? {
        val present = device?.let { runCatching { it.getMotionRange(axis) }.isSuccess } ?: false
        return if (present) event.getAxisValue(axis) else null
    }

    private companion object {
        const val TAG = "GamepadInput"
    }
}
