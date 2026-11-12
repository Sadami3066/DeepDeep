package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.host.external.DdpEntry
import io.github.whywood.deepdeep.plugin.host.external.DdpSigning
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider

internal class ExternalPluginDdpFixture(private val directory: File) {
    private val provider = BouncyCastleProvider()

    private val keyPair = KeyPairGenerator.getInstance("Ed25519", provider).generateKeyPair()

    fun build(
        name: String,
        pluginId: String,
        version: String = "1.0.0",
        entryClass: String = "$pluginId.Plugin",
        dexes: List<ByteArray>,
        tamperedSignature: Boolean = false,
        missingDex: Boolean = false,
    ): File {
        val signed = linkedMapOf<String, ByteArray>()
        signed["assets/plugin.json"] = manifest(pluginId, version, entryClass).toByteArray(Charsets.UTF_8)
        dexes.forEachIndexed { index, bytes ->
            signed[if (index == 0) "classes.dex" else "classes${index + 1}.dex"] = bytes
        }
        val digests = signed.map { (path, bytes) -> DdpEntry(path, bytes.size.toLong(), DdpSigning.sha256Hex(bytes)) }
        val payload = DdpSigning.canonicalDigestBytes(digests)
        val signature = Signature.getInstance("Ed25519", provider).run {
            initSign(keyPair.private)
            update(payload)
            sign()
        }
        val signatureJson = buildString {
            append("{\"algorithm\":\"Ed25519\",")
            append("\"keyId\":\"").append(DdpSigning.keyId(keyPair.public)).append("\",")
            append("\"publicKey\":\"").append(DdpSigning.encodeBase64(keyPair.public.encoded)).append("\",")
            append("\"signature\":\"").append(DdpSigning.encodeBase64(signature)).append("\"}")
        }
        val written = LinkedHashMap(signed)
        if (tamperedSignature) {
            val first = written.getValue("classes.dex")
            written["classes.dex"] = first.copyOf().also { bytes -> bytes[0] = (bytes[0].toInt() xor 0xFF).toByte() }
        }
        if (missingDex) written.remove("classes.dex")
        val target = File(directory, name)
        target.parentFile?.mkdirs()
        writeZip(target, written + (DdpSigning.SIGNATURE_ENTRY to signatureJson.toByteArray(Charsets.UTF_8)))
        return target
    }

    private fun manifest(pluginId: String, version: String, entryClass: String): String = buildString {
        append("{\"id\":\"").append(pluginId).append("\",")
        append("\"version\":\"").append(version).append("\",")
        append("\"apiLevel\":").append(PLUGIN_API_LEVEL).append(',')
        append("\"entryClass\":\"").append(entryClass).append("\",")
        append("\"name\":{\"default\":\"E2E Fixture\"},")
        append("\"summary\":{\"default\":\"E2E Fixture\"},")
        append("\"capabilities\":[],")
        append("\"provides\":{\"tools\":[]}}")
    }

    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    companion object {
        fun sampleDexes(bundle: File): List<ByteArray> = ZipFile(bundle).use { zip ->
            zip.entries().asSequence()
                .mapNotNull { entry -> dexOrdinal(entry.name)?.let { ordinal -> ordinal to entry } }
                .sortedBy { (ordinal, _) -> ordinal }
                .map { (_, entry) -> zip.getInputStream(entry).use { it.readBytes() } }
                .toList()
        }

        fun corruptDex(): ByteArray = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)

        private fun dexOrdinal(name: String): Int? {
            val match = Regex("^classes([0-9]*)\\.dex$").find(name) ?: return null
            return match.groupValues[1].takeIf { it.isNotEmpty() }?.toInt() ?: 0
        }
    }
}
