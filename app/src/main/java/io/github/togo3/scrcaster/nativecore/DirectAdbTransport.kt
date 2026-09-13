package io.github.togo3.scrcaster.nativecore

import android.util.Base64
import android.util.Log
import io.github.togo3.scrcaster.storage.AdbClientData
import io.github.togo3.scrcaster.storage.AppSettings
import io.github.togo3.scrcaster.storage.Storage
import kotlinx.coroutines.runBlocking
import java.io.*
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * Low-level transport helper that manages local RSA keys and creates
 * `DirectAdbConnection` instances for direct TCP/TLS ADB connections.
 *
 * This type is responsible for persisting the private key and performing
 * pairing/connect discovery helpers.
 */
internal object DirectAdbTransport {

    private val keyLock = Any()

    @Volatile
    private var cachedKeys: Pair<PrivateKey, ByteArray>? = null

    private fun keys(): Pair<PrivateKey, ByteArray> =
        cachedKeys ?: synchronized(keyLock) {
            cachedKeys ?: runBlocking { loadOrCreate() }.also { cachedKeys = it }
        }

    val privateKey: PrivateKey get() = keys().first
    val publicKeyX509: ByteArray get() = keys().second

    fun getPrivateKeyPem(): String? = cachedKeys?.let { (priv, _) ->
        "-----BEGIN PRIVATE KEY-----\n" +
                Base64.encodeToString(priv.encoded, Base64.DEFAULT) +
                "-----END PRIVATE KEY-----"
    }

    fun getPublicKeyPem(): String? = cachedKeys?.let { (_, pub) ->
        "-----BEGIN PUBLIC KEY-----\n" +
                Base64.encodeToString(pub, Base64.DEFAULT) +
                "-----END PUBLIC KEY-----"
    }

    @Volatile
    var keyName: String = AppSettings.ADB_KEY_NAME.defaultValue

    /**
     * 通过 TCP 连接 ADB 设备
     */
    fun connect(host: String, port: Int, timeoutMs: Int = 10_000): DirectAdbConnection {
        Log.i(TAG, "connect(): opening direct adbd transport to $host:$port (timeout=${timeoutMs}ms)")
        val conn = DirectAdbConnection(
            host,
            port,
            privateKey,
            publicKeyX509,
            keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
            tcpMarker = true,
        )
        try {
            conn.handshake(timeoutMs)
            Log.i(TAG, "connect(): handshake success for $host:$port")
            return conn
        } catch (error: Throwable) {
            conn.close()
            throw error
        }
    }

    /**
     * 通过 USB 流连接 ADB 设备
     *
     * @param inputStream USB 输入流
     * @param outputStream USB 输出流
     * @param deviceId USB 设备 ID (可选)
     * @return DirectAdbConnection 连接实例
     */
    fun connectUsb(
        inputStream: InputStream,
        outputStream: OutputStream,
        deviceId: Int? = null,
    ): DirectAdbConnection {
        Log.i(TAG, "connectUsb(): opening USB ADB transport (deviceId=$deviceId)")
        val conn = DirectAdbConnection(
            inputStream,
            outputStream,
            privateKey,
            publicKeyX509,
            keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
            deviceId,
        )
        try {
            conn.handshake()
            Log.i(TAG, "connectUsb(): handshake success for USB device $deviceId")
            return conn
        } catch (error: Throwable) {
            conn.close()
            throw error
        }
    }

    fun pair(host: String, port: Int, pairingCode: String): Boolean {
        val targetHost = host.trim()
        val targetCode = pairingCode.trim()
        require(targetHost.isNotBlank()) { "host is blank" }
        require(targetCode.isNotBlank()) { "pairing code is blank" }

        val pairingKey = AdbPairingKey(
            privateKey = privateKey,
            alias = keyName.ifBlank { AppSettings.ADB_KEY_NAME.defaultValue },
        )
        return DirectAdbPairingClient(targetHost, port, targetCode, pairingKey).use {
            it.start()
        }
    }

    fun discoverPairingService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? =
        AdbMdnsDiscoverer.discoverPairingService(timeoutMs, includeLanDevices)

    fun discoverConnectService(
        timeoutMs: Long = 12_000,
        includeLanDevices: Boolean = true,
    ): Pair<String, Int>? =
        AdbMdnsDiscoverer.discoverConnectService(timeoutMs, includeLanDevices)

    data class ImportedKeyInfo(
        val fingerprint: String,
    )

    data class KeyResetInfo(
        val fingerprint: String,
        val removedImportedKey: Boolean,
    )

    suspend fun importPrivateKey(content: String, fileName: String): ImportedKeyInfo {
        val privateKey = parsePrivateKey(content)
        validatePrivateKey(privateKey)
        val publicKeyX509 = derivePublicX509(privateKey)
        validatePublicKey(publicKeyX509)

        Storage.adbClientData.saveBundle(
            Storage.adbClientData.bundleState.value.copy(
                importedPrivateKey = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP),
                importedPrivateKeyFileName = fileName,
                importedPublicKeyX509 = Base64.encodeToString(publicKeyX509, Base64.NO_WRAP),
                importedPublicKeyFileName = "",
            ),
        )
        synchronized(keyLock) {
            cachedKeys = Pair(privateKey, publicKeyX509)
        }
        return ImportedKeyInfo(fingerprint(publicKeyX509))
    }

    suspend fun importPublicKey(content: String, fileName: String): ImportedKeyInfo {
        val privateKey = loadActivePrivateKey()
            ?: throw IllegalArgumentException("Import private key first")
        val importedPublicKey = parsePublicKey(content)
        val importedRsa = importedPublicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("Public key is not RSA")
        val privateRsa = privateKey as? RSAPrivateCrtKey
            ?: throw IllegalArgumentException("Stored private key cannot derive an RSA public key")

        require(importedRsa.modulus == privateRsa.modulus) { "Public key does not match private key" }
        require(importedRsa.publicExponent == privateRsa.publicExponent) {
            "Public key does not match private key"
        }

        val publicKeyX509 = importedPublicKey.encoded
        validatePublicKey(publicKeyX509)
        Storage.adbClientData.saveBundle(
            Storage.adbClientData.bundleState.value.copy(
                importedPublicKeyX509 = Base64.encodeToString(publicKeyX509, Base64.NO_WRAP),
                importedPublicKeyFileName = fileName,
            ),
        )
        synchronized(keyLock) {
            cachedKeys = Pair(privateKey, publicKeyX509)
        }
        return ImportedKeyInfo(fingerprint(publicKeyX509))
    }

    fun reloadKeys() {
        synchronized(keyLock) {
            cachedKeys = runBlocking { loadOrCreate() }
        }
    }

    fun resetKeys(): KeyResetInfo {
        val data = Storage.adbClientData.bundleState.value
        val hasImportedKey = data.importedPrivateKey.isNotBlank() || data.importedPublicKeyX509.isNotBlank()
        val keys = if (hasImportedKey) {
            synchronized(keyLock) {
                runBlocking {
                    Storage.adbClientData.saveBundle(
                        data.copy(
                            importedPrivateKey = "",
                            importedPrivateKeyFileName = "",
                            importedPublicKeyX509 = "",
                            importedPublicKeyFileName = "",
                        ),
                    )
                    loadOrCreate()
                }.also { cachedKeys = it }
            }
        } else {
            synchronized(keyLock) {
                runBlocking { loadOrCreate(forceNew = true) }.also { cachedKeys = it }
            }
        }
        return KeyResetInfo(fingerprint(keys.second), removedImportedKey = hasImportedKey)
    }

    /**
     * Load persisted RSA keypair from DataStore, or generate a new one.
     * Returns (privateKey, publicX509Bytes).
     */
    private suspend fun loadOrCreate(
        forceNew: Boolean = false,
    ): Pair<PrivateKey, ByteArray> {
        val adbClientData = Storage.adbClientData

        val importedPrivB64 = adbClientData.importedPrivateKey.get()
        val generatedPrivB64 = adbClientData.rsaPrivateKey.get()
        val privB64 = importedPrivB64.ifBlank { generatedPrivB64 }

        if (privB64.isNotBlank() && !forceNew) {
            try {
                val priv = generatePkcs8PrivateKey(Base64.decode(privB64, Base64.DEFAULT))
                val pubB64 = adbClientData.importedPublicKeyX509.get()
                    .ifBlank { adbClientData.rsaPublicKeyX509.get() }
                val pub =
                    if (pubB64.isNotBlank()) Base64.decode(pubB64, Base64.DEFAULT)
                    else derivePublicX509(priv).also { derivedPublicKey ->
                        val encoded = Base64.encodeToString(derivedPublicKey, Base64.NO_WRAP)
                        val current = adbClientData.bundleState.value
                        adbClientData.saveBundle(
                            if (importedPrivB64.isNotBlank()) {
                                current.copy(
                                    importedPublicKeyX509 = encoded,
                                    importedPublicKeyFileName = "",
                                )
                            } else {
                                current.copy(
                                    rsaPublicKeyX509 = encoded,
                                )
                            },
                        )
                    }

                Log.i(
                    TAG,
                    "loadOrCreate(): loaded persisted RSA key pair from DataStore, " +
                            "fp=${fingerprint(pub)}",
                )
                return Pair(priv, pub)
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "loadOrCreate(): failed to load persisted key from DataStore, regenerating",
                    e,
                )
            }
        }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val privateKeyB64 = Base64.encodeToString(kp.private.encoded, Base64.NO_WRAP)
        val publicKeyB64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
        adbClientData.saveBundle(
            AdbClientData.Bundle(
                rsaPrivateKey = privateKeyB64,
                rsaPublicKeyX509 = publicKeyB64,
                importedPrivateKey = "",
                importedPrivateKeyFileName = "",
                importedPublicKeyX509 = "",
                importedPublicKeyFileName = "",
            ),
        )

        Log.i(
            TAG,
            "loadOrCreate(): generated new RSA key pair, fp=${fingerprint(kp.public.encoded)}",
        )
        return Pair(kp.private, kp.public.encoded)
    }

    private suspend fun loadActivePrivateKey(): PrivateKey? {
        val data = Storage.adbClientData
        val privateKeyB64 = data.importedPrivateKey.get().ifBlank {
            data.rsaPrivateKey.get()
        }
        if (privateKeyB64.isBlank()) return null
        return generatePkcs8PrivateKey(Base64.decode(privateKeyB64, Base64.DEFAULT))
    }

    private fun parsePrivateKey(content: String): PrivateKey {
        val pem = readPem(content)
        val der = when {
            pem != null -> {
                require(pem.label != "ENCRYPTED PRIVATE KEY") {
                    "Encrypted private keys are not supported"
                }
                pem.bytes
            }

            else -> Base64.decode(content.filterNot(Char::isWhitespace), Base64.DEFAULT)
        }
        val kf = KeyFactory.getInstance("RSA")
        return when (pem?.label) {
            "RSA PRIVATE KEY" -> kf.generatePrivate(parsePkcs1PrivateKey(der))
            "PRIVATE KEY", null -> generatePkcs8PrivateKey(der)
            else -> throw IllegalArgumentException("Unsupported private key format: ${pem.label}")
        }
    }

    private fun generatePkcs8PrivateKey(der: ByteArray): PrivateKey {
        val kf = KeyFactory.getInstance("RSA")
        val key = runCatching { kf.generatePrivate(PKCS8EncodedKeySpec(der)) }.getOrNull()
        return key as? RSAPrivateCrtKey ?: kf.generatePrivate(parsePkcs8PrivateKey(der))
    }

    private fun parsePublicKey(content: String): PublicKey {
        val pem = readPem(content)
        val normalized = content.trim()
        val kf = KeyFactory.getInstance("RSA")
        val der = when {
            pem != null -> pem.bytes
            normalized.contains(" ") -> Base64.decode(normalized.substringBefore(' '), Base64.DEFAULT)
            else -> Base64.decode(normalized.filterNot(Char::isWhitespace), Base64.DEFAULT)
        }
        return when {
            pem?.label == "RSA PUBLIC KEY" -> kf.generatePublic(parsePkcs1PublicKey(der))
            pem?.label == "PUBLIC KEY" || pem == null && !looksLikeAdbPublicKey(der) ->
                kf.generatePublic(X509EncodedKeySpec(der))

            pem == null -> parseAdbPublicKey(der)
            else -> throw IllegalArgumentException("Unsupported public key format: ${pem.label}")
        }
    }

    private fun validatePrivateKey(privateKey: PrivateKey) {
        val rsa = privateKey as? RSAPrivateCrtKey
            ?: throw IllegalArgumentException("Private key must be an RSA CRT key")
        require(rsa.modulus.bitLength() == 2048) { "ADB RSA key must be 2048-bit" }
        require(rsa.publicExponent.signum() > 0 && rsa.publicExponent.bitLength() <= 31) {
            "Unsupported RSA public exponent"
        }
        Signature.getInstance("SHA1withRSA").apply {
            initSign(privateKey)
            update("scrcpy-adb-key-check".toByteArray())
            sign()
        }
    }

    private fun validatePublicKey(publicX509: ByteArray) {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicX509))
        val rsa = publicKey as? RSAPublicKey
            ?: throw IllegalArgumentException("Public key must be RSA")
        require(rsa.modulus.bitLength() == 2048) { "ADB RSA key must be 2048-bit" }
        require(rsa.publicExponent.signum() > 0 && rsa.publicExponent.bitLength() <= 31) {
            "Unsupported RSA public exponent"
        }
    }

    private fun derivePublicX509(privateKey: PrivateKey): ByteArray {
        val rsa = privateKey as? RSAPrivateCrtKey
            ?: throw IllegalStateException("Expected RSAPrivateCrtKey but was ${privateKey.javaClass.name}")
        val kf = KeyFactory.getInstance("RSA")
        val public = kf.generatePublic(RSAPublicKeySpec(rsa.modulus, rsa.publicExponent))
        return public.encoded
    }

    private data class PemBlock(
        val label: String,
        val bytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PemBlock

            if (label != other.label) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = label.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    private fun readPem(content: String): PemBlock? {
        val begin = Regex("-----BEGIN ([A-Z0-9 ]+)-----").find(content) ?: return null
        val label = begin.groupValues[1]
        val endMarker = "-----END $label-----"
        val end = content.indexOf(endMarker, begin.range.last + 1)
        require(end >= 0) { "Invalid PEM: missing END $label" }
        val body = content.substring(begin.range.last + 1, end)
        return PemBlock(label, Base64.decode(body.filterNot(Char::isWhitespace), Base64.DEFAULT))
    }

    private fun parsePkcs1PrivateKey(der: ByteArray): RSAPrivateCrtKeySpec {
        val reader = DerReader(der).readSequence()
        reader.readInteger() // version
        val modulus = reader.readInteger()
        val publicExponent = reader.readInteger()
        val privateExponent = reader.readInteger()
        val primeP = reader.readInteger()
        val primeQ = reader.readInteger()
        val primeExponentP = reader.readInteger()
        val primeExponentQ = reader.readInteger()
        val crtCoefficient = reader.readInteger()
        return RSAPrivateCrtKeySpec(
            modulus,
            publicExponent,
            privateExponent,
            primeP,
            primeQ,
            primeExponentP,
            primeExponentQ,
            crtCoefficient,
        )
    }

    private fun parsePkcs8PrivateKey(der: ByteArray): RSAPrivateCrtKeySpec {
        val reader = DerReader(der).readSequence()
        reader.readInteger() // version
        reader.readElement() // privateKeyAlgorithm
        return parsePkcs1PrivateKey(reader.readOctetString())
    }

    private fun parsePkcs1PublicKey(der: ByteArray): RSAPublicKeySpec {
        val reader = DerReader(der).readSequence()
        return RSAPublicKeySpec(reader.readInteger(), reader.readInteger())
    }

    private fun looksLikeAdbPublicKey(bytes: ByteArray): Boolean =
        bytes.size == ADB_PUBLIC_KEY_BYTES &&
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int == ADB_PUBLIC_KEY_WORDS

    private fun parseAdbPublicKey(bytes: ByteArray): PublicKey {
        require(looksLikeAdbPublicKey(bytes)) { "Invalid ADB public key" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val words = buf.int
        buf.int // n0inv
        val modulusLE = ByteArray(words * 4)
        buf.get(modulusLE)
        val rrLE = ByteArray(words * 4)
        buf.get(rrLE)
        val exponent = buf.int.toLong() and 0xffffffffL
        val modulus = BigInteger(1, modulusLE.reversedArray())
        return KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(modulus, BigInteger.valueOf(exponent)),
        )
    }

    private class DerReader(
        private val bytes: ByteArray,
    ) {
        private var offset = 0

        fun readSequence(): DerReader {
            val content = readTag(0x30)
            return DerReader(content)
        }

        fun readInteger(): BigInteger =
            BigInteger(readTag(0x02))

        fun readOctetString(): ByteArray =
            readTag(0x04)

        fun readElement(): ByteArray {
            require(offset < bytes.size) { "Invalid ASN.1 DER" }
            val start = offset
            offset++
            val length = readLength()
            require(offset + length <= bytes.size) { "Invalid ASN.1 DER length" }
            offset += length
            return bytes.copyOfRange(start, offset)
        }

        private fun readTag(expectedTag: Int): ByteArray {
            require(offset < bytes.size) { "Invalid ASN.1 DER" }
            val tag = bytes[offset++].toInt() and 0xff
            require(tag == expectedTag) { "Unsupported ASN.1 DER key format" }
            val length = readLength()
            require(offset + length <= bytes.size) { "Invalid ASN.1 DER length" }
            return bytes.copyOfRange(offset, offset + length).also {
                offset += length
            }
        }

        private fun readLength(): Int {
            val first = bytes[offset++].toInt() and 0xff
            if (first < 0x80) return first
            val lengthBytes = first and 0x7f
            require(lengthBytes in 1..4) { "Unsupported ASN.1 DER length" }
            var length = 0
            repeat(lengthBytes) {
                length = (length shl 8) or (bytes[offset++].toInt() and 0xff)
            }
            return length
        }
    }

    private fun fingerprint(publicX509: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicX509)
        return digest.joinToString(":") { b -> "%02x".format(b) }
    }

    private fun encodeAdbPublicKey(modulus: BigInteger, exponent: Int): ByteArray {
        val two32 = BigInteger.ONE.shiftLeft(32)
        val mask32 = two32.subtract(BigInteger.ONE)

        fun toBigEndianPadded(n: BigInteger): ByteArray {
            val raw = n.toByteArray()
            val arr = ByteArray(ADB_PUBLIC_KEY_MODULUS_BYTES)
            val src = if (raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
            src.copyInto(arr, destinationOffset = ADB_PUBLIC_KEY_MODULUS_BYTES - src.size)
            return arr
        }

        val modBE = toBigEndianPadded(modulus)
        val n0 = modulus.and(mask32)
        val n0inv = n0.modInverse(two32).negate().mod(two32).toInt()
        val r = BigInteger.ONE.shiftLeft(ADB_PUBLIC_KEY_MODULUS_BYTES * 8)
        val rrBE = toBigEndianPadded(r.multiply(r).mod(modulus))

        val buf = ByteBuffer.allocate(ADB_PUBLIC_KEY_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(ADB_PUBLIC_KEY_WORDS)
        buf.putInt(n0inv)
        for (i in ADB_PUBLIC_KEY_WORDS - 1 downTo 0) {
            val o = i * 4
            buf.put(modBE[o + 3]); buf.put(modBE[o + 2]); buf.put(modBE[o + 1]); buf.put(modBE[o])
        }
        for (i in ADB_PUBLIC_KEY_WORDS - 1 downTo 0) {
            val o = i * 4
            buf.put(rrBE[o + 3]); buf.put(rrBE[o + 2]); buf.put(rrBE[o + 1]); buf.put(rrBE[o])
        }
        buf.putInt(exponent)
        return buf.array()
    }

    private const val TAG = "DirectAdbTransport"
    private const val ADB_PUBLIC_KEY_WORDS = 64
    private const val ADB_PUBLIC_KEY_MODULUS_BYTES = 256
    private const val ADB_PUBLIC_KEY_BYTES = 4 + 4 + ADB_PUBLIC_KEY_MODULUS_BYTES +
            ADB_PUBLIC_KEY_MODULUS_BYTES + 4
}
