package io.github.togo3.scrcaster.connection

import io.github.togo3.scrcaster.scrcpy.Scrcpy
import io.github.togo3.scrcaster.services.ConnectionStateStore
import io.github.togo3.scrcaster.services.DeviceAdbConnectionCoordinator
import io.github.togo3.scrcaster.services.DeviceConnectionController

/**
 * Phone counterpart of [AndroidConnectionBackend].
 *
 * The phone connection layer is multi-device and driven by [DeviceConnectionController]
 * (set target -> connect USB/network -> start scrcpy, plus USB plug/unplug and
 * auto-reconnect). It only shares the minimal [ConnectionBackend] primitive with the TV,
 * so this backend delegates the shared contract and leaves the richer multi-device flow to
 * [DeviceConnectionController]. A future single-device phone screen can reuse this type
 * through the same [ConnectionBackend] contract the TV UI expects.
 */
internal class PhoneConnectionBackend(
    private val controller: DeviceConnectionController,
) : ConnectionBackend {
    override fun isStreaming() = controller.isStreaming()
    override fun cancelPendingConnect() = controller.cancelPendingConnect()
}

internal fun createPhoneConnectionBackend(
    scrcpy: Scrcpy,
    stateStore: ConnectionStateStore,
    adbCoordinator: DeviceAdbConnectionCoordinator = DeviceAdbConnectionCoordinator(),
): PhoneConnectionBackend = PhoneConnectionBackend(DeviceConnectionController(scrcpy, stateStore, adbCoordinator))
