package io.github.togo3.scrcaster.nativecore

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AdbSocketStreamTest {

    @Test
    fun awaitOpenSucceedsWhenRemoteOkayReceived() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(remote = 42)
        stream.awaitOpen(timeoutMs = 1_000)
        assertEquals(42, stream.remoteId)
    }

    @Test(expected = IOException::class)
    fun awaitOpenThrowsOnTimeout() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.awaitOpen(timeoutMs = 50)
    }

    @Test
    fun onDataDeliversBytesThroughInputStream() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.onData(byteArrayOf(1, 2, 3))
        stream.onData(byteArrayOf(4, 5))

        val buf = ByteArray(5)
        val n = stream.inputStream.read(buf)
        assertEquals(3, n)
        assertArrayEquals(byteArrayOf(1, 2, 3), buf.copyOfRange(0, 3))

        val n2 = stream.inputStream.read(buf)
        assertEquals(2, n2)
        assertArrayEquals(byteArrayOf(4, 5), buf.copyOfRange(0, 2))
    }

    @Test
    fun inputStreamReadSingleByte() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.onData(byteArrayOf(0xAB.toByte(), 0xCD.toByte()))

        assertEquals(0xAB, stream.inputStream.read())
        assertEquals(0xCD, stream.inputStream.read())
    }

    @Test
    fun inputStreamReturnsNegativeOneOnEndOfStream() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.onData(byteArrayOf(1, 2, 3))
        stream.forceClose()

        val buf = ByteArray(8)
        val n = stream.inputStream.read(buf)
        assertEquals(3, n)
        assertArrayEquals(byteArrayOf(1, 2, 3), buf.copyOfRange(0, 3))

        assertEquals(-1, stream.inputStream.read(buf))
    }

    @Test
    fun inputStreamReadAcrossChunkBoundaries() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.onData(byteArrayOf(1, 2, 3, 4, 5))
        stream.onData(byteArrayOf(6, 7, 8))

        val buf = ByteArray(8)
        val n = stream.inputStream.read(buf)
        assertEquals(5, n)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), buf.copyOfRange(0, 5))

        val n2 = stream.inputStream.read(buf)
        assertEquals(3, n2)
        assertArrayEquals(byteArrayOf(6, 7, 8), buf.copyOfRange(0, 3))
    }

    @Test
    fun inputStreamPartialReadFromChunk() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.onData(byteArrayOf(1, 2, 3, 4, 5))

        val smallBuf = ByteArray(2)
        val n = stream.inputStream.read(smallBuf)
        assertEquals(2, n)
        assertArrayEquals(byteArrayOf(1, 2), smallBuf)

        val n2 = stream.inputStream.read(smallBuf)
        assertEquals(2, n2)
        assertArrayEquals(byteArrayOf(3, 4), smallBuf)

        val n3 = stream.inputStream.read(smallBuf)
        assertEquals(1, n3)
        assertEquals(5.toByte(), smallBuf[0])
    }

    @Test
    fun availableReportsRemainingBytesInCurrentChunk() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.onData(byteArrayOf(1, 2, 3, 4, 5))

        val smallBuf = ByteArray(2)
        stream.inputStream.read(smallBuf)
        assertEquals(3, stream.inputStream.available())
    }

    @Test
    fun availableIsZeroWhenNoChunkBuffered() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        assertEquals(0, stream.inputStream.available())
    }

    @Test
    fun outputStreamSendsWriteCommand() {
        val capturedCmd = AtomicInteger(0)
        val capturedArg0 = AtomicInteger(0)
        val capturedArg1 = AtomicInteger(0)
        val capturedData = AtomicReference<ByteArray>(null)

        val stream = AdbSocketStream(localId = 5, sender = { cmd, arg0, arg1, data ->
            capturedCmd.set(cmd)
            capturedArg0.set(arg0)
            capturedArg1.set(arg1)
            capturedData.set(data)
        })
        stream.onRemoteOkay(10)

        stream.outputStream.write(byteArrayOf(0x01, 0x02, 0x03))

        assertEquals(0x45545257, capturedCmd.get()) // A_WRTE
        assertEquals(5, capturedArg0.get())
        assertEquals(10, capturedArg1.get())
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), capturedData.get())
    }

    @Test
    fun outputStreamWriteSingleByte() {
        val capturedData = AtomicReference<ByteArray>(null)
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, data ->
            capturedData.set(data)
        })
        stream.onRemoteOkay(1)

        stream.outputStream.write(0x42)

        assertArrayEquals(byteArrayOf(0x42), capturedData.get())
    }

    @Test
    fun outputStreamThrowsOnWriteAfterClose() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.close()

        try {
            stream.outputStream.write(byteArrayOf(1))
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("closed"))
        }
    }

    @Test
    fun outputStreamZeroLengthWriteIsNoop() {
        val sentCount = AtomicInteger(0)
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ ->
            sentCount.incrementAndGet()
        })
        stream.onRemoteOkay(1)

        stream.outputStream.write(byteArrayOf(), 0, 0)
        assertEquals(0, sentCount.get())
    }

    @Test
    fun closeSendsAclseCommandWhenRemoteIdIsSet() {
        val capturedCmd = AtomicInteger(0)
        val stream = AdbSocketStream(localId = 3, sender = { cmd, _, _, _ ->
            capturedCmd.set(cmd)
        })
        stream.onRemoteOkay(7)
        stream.close()

        assertEquals(0x45534c43, capturedCmd.get()) // A_CLSE
        assertTrue(stream.closed)
    }

    @Test
    fun closeDoesNotSendAclseWhenRemoteIdIsZero() {
        val sentCount = AtomicInteger(0)
        val stream = AdbSocketStream(localId = 3, sender = { _, _, _, _ ->
            sentCount.incrementAndGet()
        })
        stream.close()

        assertEquals(0, sentCount.get())
        assertTrue(stream.closed)
    }

    @Test
    fun closeIsIdempotent() {
        val sentCount = AtomicInteger(0)
        val stream = AdbSocketStream(localId = 3, sender = { _, _, _, _ ->
            sentCount.incrementAndGet()
        })
        stream.onRemoteOkay(7)
        stream.close()
        stream.close()

        assertEquals(1, sentCount.get())
    }

    @Test
    fun forceCloseMarksStreamClosedAndDeliversEndOfStream() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.onData(byteArrayOf(1, 2))
        stream.forceClose()

        assertTrue(stream.closed)
        val buf = ByteArray(4)
        assertEquals(2, stream.inputStream.read(buf))
        assertEquals(-1, stream.inputStream.read(buf))
    }

    @Test
    fun onDataIgnoredAfterClose() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(1)
        stream.close()
        stream.onData(byteArrayOf(1, 2, 3))

        val buf = ByteArray(4)
        assertEquals(-1, stream.inputStream.read(buf))
    }

    @Test
    fun onRemoteOkayOnlySetsRemoteIdOnce() {
        val stream = AdbSocketStream(localId = 1, sender = { _, _, _, _ -> })
        stream.onRemoteOkay(10)
        stream.onRemoteOkay(20)

        assertEquals(10, stream.remoteId)
    }

    @Test
    fun flowControlWindowBlocksWriteUntilOkayReceived() {
        val sentCount = AtomicInteger(0)
        val stream = AdbSocketStream(
            localId = 1,
            sender = { _, _, _, _ -> sentCount.incrementAndGet() },
            flowControlWindow = 1,
        )
        stream.onRemoteOkay(1)

        stream.outputStream.write(byteArrayOf(1))
        assertEquals(1, sentCount.get())

        val writeDone = CountDownLatch(1)
        val thread = Thread {
            try {
                stream.outputStream.write(byteArrayOf(2))
                writeDone.countDown()
            } catch (e: Exception) {
                writeDone.countDown()
            }
        }
        thread.start()

        assertFalse(writeDone.await(100, TimeUnit.MILLISECONDS))

        stream.onRemoteOkay(1)
        assertTrue(writeDone.await(2_000, TimeUnit.MILLISECONDS))
        assertEquals(2, sentCount.get())
    }

    @Test
    fun flowControlWindowForceCloseUnblocksBlockedWrite() {
        val stream = AdbSocketStream(
            localId = 1,
            sender = { _, _, _, _ -> },
            flowControlWindow = 1,
        )
        stream.onRemoteOkay(1)

        stream.outputStream.write(byteArrayOf(1))

        val writeFailed = CountDownLatch(1)
        val thread = Thread {
            try {
                stream.outputStream.write(byteArrayOf(2))
            } catch (e: IOException) {
                writeFailed.countDown()
            }
        }
        thread.start()

        assertFalse(writeFailed.await(100, TimeUnit.MILLISECONDS))

        stream.forceClose()
        assertTrue(writeFailed.await(2_000, TimeUnit.MILLISECONDS))
    }

    @Test
    fun flowControlWindowZeroMeansNoFlowControl() {
        val sentCount = AtomicInteger(0)
        val stream = AdbSocketStream(
            localId = 1,
            sender = { _, _, _, _ -> sentCount.incrementAndGet() },
            flowControlWindow = 0,
        )
        stream.onRemoteOkay(1)

        stream.outputStream.write(byteArrayOf(1))
        stream.outputStream.write(byteArrayOf(2))
        stream.outputStream.write(byteArrayOf(3))

        assertEquals(3, sentCount.get())
    }
}
