package io.github.togo3.scrcaster.nativecore
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logical ADB stream abstraction mapped to a local id. Provides blocking
 * `InputStream`/`OutputStream` implementations and lifecycle helpers used by callers.
 */
class AdbSocketStream(
    val localId: Int,
    private val sender: (cmd: Int, arg0: Int, arg1: Int, `data`: ByteArray) -> Unit,
    private val flowControlWindow: Int = 0,
): Closeable {

    companion object {
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
    }

    @Volatile
    var remoteId: Int = 0

    @Volatile
    var closed: Boolean = false

    private val latch = CountDownLatch(1)
    private val latchOk = AtomicBoolean(false)
    private val queue = LinkedBlockingQueue<Any>()

    // need notifyAll() / wait()
    private val writeLock = Object()

    @Volatile
    private var inflightWrites = 0

    private object EndOfStreamMarker

    val inputStream: InputStream = InStream()
    val outputStream: OutputStream = OutStream()

    internal fun onRemoteOkay(remote: Int) {
        if (remoteId == 0) {
            remoteId = remote
            latchOk.set(true)
            latch.countDown()
        }
        if (flowControlWindow > 0) {
            synchronized(writeLock) {
                if (inflightWrites > 0) inflightWrites--
                writeLock.notifyAll()
            }
        }
    }

    internal fun onData(data: ByteArray) {
        if (!closed) queue.offer(data)
    }

    internal fun forceClose() {
        closed = true
        queue.offer(EndOfStreamMarker)
        latch.countDown()
        if (flowControlWindow > 0) {
            synchronized(writeLock) { writeLock.notifyAll() }
        }
    }

    fun awaitOpen(timeoutMs: Long) {
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw IOException("ADB stream open timed out (localId=$localId)")
        }
        if (!latchOk.get()) {
            throw IOException("ADB stream rejected by device (localId=$localId)")
        }
    }

    override fun close() {
        if (closed) return

        closed = true
        if (remoteId != 0) runCatching {
            sender(A_CLSE, localId, remoteId, ByteArray(0))
        }
        queue.offer(EndOfStreamMarker)
        if (flowControlWindow > 0) {
            synchronized(writeLock) { writeLock.notifyAll() }
        }
    }

    private inner class InStream: InputStream() {
        private var chunk: ByteArray? = null
        private var off = 0

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) == -1) -1 else (b[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (true) {
                val c = chunk
                if (c != null && this.off < c.size) {
                    val n = minOf(len, c.size - this.off)
                    c.copyInto(b, off, this.off, this.off + n)
                    this.off += n
                    return n
                }
                chunk = null
                this.off = 0
                val next = queue.take()
                if (next === EndOfStreamMarker) {
                    return -1
                }
                chunk = next as ByteArray
            }
        }

        override fun available(): Int = chunk?.let { it.size - off } ?: 0
    }

    private inner class OutStream: OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("ADB stream closed")
            if (len == 0) return
            if (flowControlWindow > 0) {
                synchronized(writeLock) {
                    while (inflightWrites >= flowControlWindow && !closed) {
                        writeLock.wait()
                    }
                    if (closed) throw IOException("ADB stream closed")
                    inflightWrites++
                }
            }
            sender(A_WRTE, localId, remoteId, b.copyOfRange(off, off + len))
        }

        override fun flush() {}
    }
}
