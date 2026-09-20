package io.github.togo3.scrcaster.nativecore

import org.junit.Assert.*
import org.junit.Test

class AdbPairingResultTest {

    @Test
    fun successResultWithDefaultDeviceGuid() {
        val result = AdbPairingResult(success = true)
        assertTrue(result.success)
        assertNull(result.deviceGuid)
    }

    @Test
    fun successResultWithDeviceGuid() {
        val result = AdbPairingResult(success = true, deviceGuid = "abc-123-def")
        assertTrue(result.success)
        assertEquals("abc-123-def", result.deviceGuid)
    }

    @Test
    fun failureResult() {
        val result = AdbPairingResult(success = false)
        assertFalse(result.success)
        assertNull(result.deviceGuid)
    }

    @Test
    fun failureResultWithDeviceGuid() {
        val result = AdbPairingResult(success = false, deviceGuid = "guid-from-failed-pairing")
        assertFalse(result.success)
        assertEquals("guid-from-failed-pairing", result.deviceGuid)
    }

    @Test
    fun dataClassEqualsAndHashCode() {
        val r1 = AdbPairingResult(success = true, deviceGuid = "guid")
        val r2 = AdbPairingResult(success = true, deviceGuid = "guid")
        val r3 = AdbPairingResult(success = false, deviceGuid = "guid")
        assertEquals(r1, r2)
        assertNotEquals(r1, r3)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun dataClassCopy() {
        val original = AdbPairingResult(success = false, deviceGuid = "old")
        val copied = original.copy(success = true)
        assertTrue(copied.success)
        assertEquals("old", copied.deviceGuid)
    }

    @Suppress("USELESS_IS_CHECK")
    @Test
    fun adbInvalidPairingCodeExceptionIsException() {
        val ex = AdbInvalidPairingCodeException()
        assertTrue(ex is Exception)
        assertNull(ex.message)
    }
}
