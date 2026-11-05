package io.github.whywood.deepdeep.plugin.host.external

import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider

internal object DdpSigning {
    const val SIGNATURE_ENTRY: String = "META-INF/plugin-signature.json"

    const val ALGORITHM: String = "Ed25519"

    const val KEY_ID_PREFIX: String = "ed25519:"

    private const val META_INF_PREFIX = "META-INF/"

    private const val SHA_256 = "SHA-256"

    private const val BUFFER_SIZE = 64 * 1024

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    private val pathOrder = Comparator<DdpEntry> { first, second -> compareUtf8(first.path, second.path) }

    private val bouncyCastle: BouncyCastleProvider by lazy { BouncyCastleProvider() }

    fun canonicalDigestBytes(entries: List<DdpEntry>): ByteArray {
        val signed = entries.filterNot { it.path.startsWith(META_INF_PREFIX) }.sortedWith(pathOrder)
        val builder = StringBuilder()
        signed.forEach { entry ->
            builder.append(entry.sha256).append(' ').append(entry.size).append(' ').append(entry.path).append('\n')
        }
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    fun entryDigest(entries: List<DdpEntry>): String = sha256Hex(canonicalDigestBytes(entries))

    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance(SHA_256).digest(bytes))

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance(SHA_256)
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    fun hex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = HEX_DIGITS[value ushr 4]
            chars[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
        }
        return String(chars)
    }

    fun publicKeyFromBase64(encoded: String): PublicKey? {
        val der = decodeBase64(encoded) ?: return null
        return publicKeyFromDer(der)
    }

    fun publicKeyFromDer(der: ByteArray): PublicKey? {
        if (der.isEmpty()) return null
        val spec = X509EncodedKeySpec(der)
        runCatching { KeyFactory.getInstance(ALGORITHM).generatePublic(spec) }.getOrNull()?.let { return it }
        return runCatching { KeyFactory.getInstance(ALGORITHM, bouncyCastle).generatePublic(spec) }.getOrNull()
    }

    fun verifySignature(publicKey: PublicKey, payload: ByteArray, signature: ByteArray): Boolean {
        if (signature.isEmpty()) return false
        val verifier = runCatching { Signature.getInstance(ALGORITHM) }.getOrNull()
            ?: runCatching { Signature.getInstance(ALGORITHM, bouncyCastle) }.getOrNull()
            ?: return false
        return runCatching {
            verifier.initVerify(publicKey)
            verifier.update(payload)
            verifier.verify(signature)
        }.getOrDefault(false)
    }

    fun keyId(publicKey: PublicKey): String = KEY_ID_PREFIX + sha256Hex(publicKey.encoded)

    fun keyId(der: ByteArray): String = KEY_ID_PREFIX + sha256Hex(der)

    fun decodeBase64(encoded: String): ByteArray? = runCatching { Base64.getDecoder().decode(encoded.trim()) }.getOrNull()

    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun compareUtf8(first: String, second: String): Int {
        val left = first.toByteArray(Charsets.UTF_8)
        val right = second.toByteArray(Charsets.UTF_8)
        val shared = minOf(left.size, right.size)
        for (index in 0 until shared) {
            val difference = (left[index].toInt() and 0xFF) - (right[index].toInt() and 0xFF)
            if (difference != 0) return difference
        }
        return left.size - right.size
    }
}
