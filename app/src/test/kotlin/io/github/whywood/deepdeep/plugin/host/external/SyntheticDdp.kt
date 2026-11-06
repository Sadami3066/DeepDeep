package io.github.whywood.deepdeep.plugin.host.external

import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object SyntheticDdp {
    const val PLUGIN_ID: String = "io.github.whywood.deepdeep.plugintest"

    const val PLUGIN_VERSION: String = "1.0.0"

    const val ENTRY_CLASS: String = "io.github.whywood.deepdeep.plugintest.TestPlugin"

    val DEX_BYTES: ByteArray = byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00)

    val ICON_BYTES: ByteArray = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    val LIB_BYTES: ByteArray = byteArrayOf(0x05, 0x06, 0x07)

    data class SigningKey(val pair: KeyPair, val keyId: String, val publicKeyBase64: String)

    data class BuildRequest(
        val manifest: String = manifestJson(),
        val key: SigningKey? = null,
        val algorithm: String = "Ed25519",
        val keyIdOverride: String? = null,
        val signatureOverride: ByteArray? = null,
        val rawSignatureEntry: ByteArray? = null,
        val tamperedIcon: Boolean = false,
        val reversedOrder: Boolean = false,
        val extraEntries: Map<String, ByteArray> = emptyMap(),
    )

    fun newSigningKey(): SigningKey {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        return signingKey(pair)
    }

    fun signingKey(pair: KeyPair): SigningKey = SigningKey(
        pair = pair,
        keyId = DdpSigning.keyId(pair.public),
        publicKeyBase64 = Base64.getEncoder().encodeToString(pair.public.encoded),
    )

    fun manifestJson(
        id: String = PLUGIN_ID,
        version: String = PLUGIN_VERSION,
        apiLevel: Int = 4,
        tools: List<String> = listOf("sample_tool"),
        entryClass: String? = ENTRY_CLASS,
        publisherKeyId: String? = null,
        minHostVersion: String? = null,
    ): String = buildString {
        append("{\n")
        append("  \"id\": \"").append(id).append("\",\n")
        append("  \"version\": \"").append(version).append("\",\n")
        append("  \"apiLevel\": ").append(apiLevel).append(",\n")
        append("  \"capabilities\": [\"app\", \"files\", \"chat\"],\n")
        if (entryClass != null) append("  \"entryClass\": \"").append(entryClass).append("\",\n")
        append("  \"name\": { \"default\": \"Plugin Test\" },\n")
        append("  \"summary\": { \"default\": \"Plugin Test Summary\" },\n")
        if (minHostVersion != null) append("  \"minHostVersion\": \"").append(minHostVersion).append("\",\n")
        if (publisherKeyId != null) {
            append("  \"publisher\": { \"name\": \"Test Publisher\", \"keyId\": \"").append(publisherKeyId).append("\" },\n")
        }
        append("  \"provides\": { \"tools\": [")
        append(tools.joinToString(", ") { "\"$it\"" })
        append("] }\n")
        append("}")
    }

    fun build(directory: File, name: String = "plugin.dpp", request: BuildRequest = BuildRequest()): File {
        val content = LinkedHashMap<String, ByteArray>()
        content["assets/plugin.json"] = request.manifest.toByteArray(Charsets.UTF_8)
        content["classes.dex"] = DEX_BYTES
        content["assets/icon.png"] = ICON_BYTES
        content["lib/arm64-v8a/libsample.so"] = LIB_BYTES
        request.extraEntries.forEach { (path, bytes) -> content[path] = bytes }
        val written = LinkedHashMap(content)
        if (request.tamperedIcon) written["assets/icon.png"] = ICON_BYTES.reversedArray()
        val signatureEntry = when {
            request.rawSignatureEntry != null -> request.rawSignatureEntry
            request.key != null -> signEntry(content, request)
            else -> null
        }
        val entries = if (request.reversedOrder) {
            written.entries.toList().reversed().map { it.key to it.value }
        } else {
            written.entries.map { it.key to it.value }
        }
        val target = File(directory, name)
        target.parentFile?.mkdirs()
        writeZip(target, entries + listOfNotNull(signatureEntry?.let { DdpSigning.SIGNATURE_ENTRY to it }))
        return target
    }

    fun writeZip(file: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun sha256Hex(bytes: ByteArray): String = DdpSigning.sha256Hex(bytes)

    private fun signEntry(content: Map<String, ByteArray>, request: BuildRequest): ByteArray {
        val key = request.key ?: error("missing signing key")
        val entries = content.map { (path, bytes) ->
            DdpEntry(path, bytes.size.toLong(), sha256Hex(bytes))
        }
        val payload = DdpSigning.canonicalDigestBytes(entries)
        val signature = request.signatureOverride ?: sign(payload, key.pair.private)
        val json = buildJsonObject {
            put("algorithm", request.algorithm)
            put("keyId", request.keyIdOverride ?: key.keyId)
            put("publicKey", key.publicKeyBase64)
            put("signature", Base64.getEncoder().encodeToString(signature))
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun sign(payload: ByteArray, privateKey: PrivateKey): ByteArray {
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(payload)
        return signer.sign()
    }
}
