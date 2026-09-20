package io.github.togo3.scrcaster.services

import io.github.togo3.scrcaster.models.ConnectionTarget
import io.github.togo3.scrcaster.models.DeviceConnectionType
import io.github.togo3.scrcaster.storage.ScrcpyOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class DeviceAdbConnectionCoordinatorTest {

    @Test
    fun `DeviceAdbSessionState has correct defaults`() {
        val state = DeviceAdbSessionState()
        assertFalse(state.isConnected)
        assertEquals("Disconnected", state.statusLine)
        assertEquals(null, state.currentTarget)
        assertEquals("Disconnected", state.connectedDeviceLabel)
        assertFalse(state.isQuickConnected)
        assertEquals(ScrcpyOptions.GLOBAL_PROFILE_ID, state.connectedScrcpyProfileId)
        assertTrue(state.audioForwardingSupported)
        assertTrue(state.cameraMirroringSupported)
    }

    @Test
    fun `DeviceAdbSessionState copy modifies fields correctly`() {
        val original = DeviceAdbSessionState()
        val modified = original.copy(
            isConnected = true,
            statusLine = "Connected to 192.168.1.100:5555",
            connectedDeviceLabel = "Pixel 8",
        )
        assertTrue(modified.isConnected)
        assertEquals("Connected to 192.168.1.100:5555", modified.statusLine)
        assertEquals("Pixel 8", modified.connectedDeviceLabel)
        assertFalse(modified.isQuickConnected)
    }

    @Test
    fun `probeTcpReachable returns true for open local port`() = runBlocking {
        ServerSocket(0).use { server ->
            val port = server.localPort
            val coordinator = DeviceAdbConnectionCoordinator()
            val result = coordinator.probeTcpReachable("127.0.0.1", port, 2000)
            assertTrue("Should detect open port on localhost", result)
        }
    }

    @Test
    fun `probeTcpReachable returns false for closed port`() = runBlocking {
        val coordinator = DeviceAdbConnectionCoordinator()
        val result = coordinator.probeTcpReachable("127.0.0.1", 1, 500)
        assertFalse("Should not detect closed port", result)
    }

    @Test
    fun `probeTcpReachable returns false for unreachable host`() = runBlocking {
        val coordinator = DeviceAdbConnectionCoordinator()
        val result = coordinator.probeTcpReachable("192.0.2.1", 5555, 500)
        assertFalse("Should not detect unreachable host", result)
    }

    @Test
    fun `probeTcpReachable resolves localhost alias`() = runBlocking {
        ServerSocket(0).use { server ->
            val port = server.localPort
            val coordinator = DeviceAdbConnectionCoordinator()
            val result = coordinator.probeTcpReachable("localhost", port, 2000)
            assertTrue("Should resolve 'localhost' to 127.0.0.1 and detect open port", result)
        }
    }

    @Test
    fun `connectFirstReachable throws when no addresses provided`() {
        val coordinator = DeviceAdbConnectionCoordinator()
        assertThrows(NoSuchElementException::class.java) {
            runBlocking {
                coordinator.connectFirstReachable(emptyList(), 5000, 1000)
            }
        }
    }

    @Test
    fun `connectFirstReachable throws when all addresses unreachable`() {
        val coordinator = DeviceAdbConnectionCoordinator()
        assertThrows(NoSuchElementException::class.java) {
            runBlocking {
                coordinator.connectFirstReachable(
                    listOf("192.0.2.1:5555", "192.0.2.2:5556"),
                    5000,
                    500,
                )
            }
        }
    }

    @Test
    fun `connectFirstReachable throws when only USB addresses provided`() {
        val coordinator = DeviceAdbConnectionCoordinator()
        assertThrows(NoSuchElementException::class.java) {
            runBlocking {
                coordinator.connectFirstReachable(
                    listOf("usb:0x18D1/0x4EE7#123"),
                    5000,
                    1000,
                )
            }
        }
    }

    @Test
    fun `connectFirstReachable throws for invalid address strings`() {
        val coordinator = DeviceAdbConnectionCoordinator()
        assertThrows(NoSuchElementException::class.java) {
            runBlocking {
                coordinator.connectFirstReachable(
                    listOf("not-a-valid-address"),
                    5000,
                    1000,
                )
            }
        }
    }

    @Test
    fun `connectFirstReachable throws for mixed invalid and USB addresses`() {
        val coordinator = DeviceAdbConnectionCoordinator()
        assertThrows(NoSuchElementException::class.java) {
            runBlocking {
                coordinator.connectFirstReachable(
                    listOf("usb:0x18D1/0x4EE7#123", "garbage"),
                    5000,
                    1000,
                )
            }
        }
    }

    @Test
    fun `ConnectedDeviceInfo has correct defaults from empty batch`() {
        val info = ConnectedDeviceInfo(
            model = "",
            serial = "",
            manufacturer = "",
            brand = "",
            device = "",
            androidRelease = "",
            sdkInt = -1,
        )
        assertEquals("", info.model)
        assertEquals("", info.serial)
        assertEquals(-1, info.sdkInt)
    }

    @Test
    fun `ConnectedDeviceInfo stores all fields`() {
        val info = ConnectedDeviceInfo(
            model = "Pixel 8",
            serial = "ABC123",
            manufacturer = "Google",
            brand = "google",
            device = "shiba",
            androidRelease = "14",
            sdkInt = 34,
        )
        assertEquals("Pixel 8", info.model)
        assertEquals("ABC123", info.serial)
        assertEquals("Google", info.manufacturer)
        assertEquals("google", info.brand)
        assertEquals("shiba", info.device)
        assertEquals("14", info.androidRelease)
        assertEquals(34, info.sdkInt)
    }

    @Test
    fun `ConnectionTarget unmarshalFrom parses LAN address with port`() {
        val target = ConnectionTarget.unmarshalFrom("192.168.1.100:5555")
        assertEquals("192.168.1.100", target?.host)
        assertEquals(5555, target?.port)
        assertEquals(DeviceConnectionType.LAN, target?.connectionType)
        assertEquals(null, target?.deviceId)
    }

    @Test
    fun `ConnectionTarget unmarshalFrom parses LAN address without port`() {
        val target = ConnectionTarget.unmarshalFrom("192.168.1.100")
        assertEquals("192.168.1.100", target?.host)
        assertEquals(5555, target?.port)
    }

    @Test
    fun `ConnectionTarget unmarshalFrom parses IPv6 address with brackets`() {
        val target = ConnectionTarget.unmarshalFrom("[::1]:5555")
        assertEquals("::1", target?.host)
        assertEquals(5555, target?.port)
    }

    @Test
    fun `ConnectionTarget unmarshalFrom parses USB address`() {
        val target = ConnectionTarget.unmarshalFrom("usb:0x18D1/0x4EE7#123")
        assertEquals("0x18D1/0x4EE7", target?.host)
        assertEquals(0, target?.port)
        assertEquals(123, target?.deviceId)
        assertEquals(DeviceConnectionType.USB, target?.connectionType)
    }

    @Test
    fun `ConnectionTarget unmarshalFrom returns null for invalid USB format`() {
        val target = ConnectionTarget.unmarshalFrom("usb:invalid")
        assertEquals(null, target)
    }

    @Test
    fun `ConnectionTarget toString formats LAN correctly`() {
        val target = ConnectionTarget(host = "192.168.1.100", port = 5555)
        assertEquals("192.168.1.100:5555", target.toString())
    }

    @Test
    fun `ConnectionTarget toString formats IPv6 LAN with brackets`() {
        val target = ConnectionTarget(host = "::1", port = 5555)
        assertEquals("[::1]:5555", target.toString())
    }

    @Test
    fun `ConnectionTarget toString formats USB correctly`() {
        val target = ConnectionTarget(
            host = "0x18D1/0x4EE7",
            port = 0,
            deviceId = 123,
            connectionType = DeviceConnectionType.USB,
        )
        assertEquals("usb:0x18D1/0x4EE7#123", target.toString())
    }
}
