package io.github.togo3.scrcaster.nativecore

import java.io.InputStream
import java.io.OutputStream
import kotlin.time.Duration

internal interface AdbService {
    suspend fun connect(host: String, port: Int, timeout: Duration)
    suspend fun connectUsb(
        inputStream: InputStream,
        outputStream: OutputStream,
        deviceId: Int? = null,
        abortHandshake: (() -> Unit)? = null,
    )
    fun cancelPendingConnect()
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
    suspend fun shell(command: String): String
    suspend fun shellBatch(build: NativeAdbService.ShellBatchBuilder.() -> Unit): List<String>
    suspend fun discoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
        matchInstanceName: String? = null,
    ): Pair<String, Int>?

    suspend fun discoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
        matchInstanceName: String? = null,
        matchHostAddress: String? = null,
    ): Pair<String, Int>?

    suspend fun pair(host: String, port: Int, pairingCode: String): AdbPairingResult
    suspend fun startApp(
        packageName: String,
        displayId: Int? = null,
        forceStop: Boolean = false,
    ): String
}
