package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.PluginValidation
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class DdpPackageException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal data class DdpEntry(val path: String, val size: Long, val sha256: String)

internal class DdpPackage private constructor(
    val file: File,
    val size: Long,
    val bundleSha256: String,
    val entries: List<DdpEntry>,
    val manifestEntry: DdpEntry,
    val dexEntries: List<DdpEntry>,
    val signatureEntry: DdpEntry?,
    private val manifestBytes: ByteArray,
    private val maxUncompressedBytes: Long,
) {
    val entryDigest: String by lazy { DdpSigning.entryDigest(entries) }

    fun manifestJson(): JsonObject = try {
        Json.parseToJsonElement(manifestBytes.toString(Charsets.UTF_8)).jsonObject
    } catch (error: Exception) {
        throw DdpPackageException("invalid $MANIFEST_ENTRY: ${error.message}", error)
    }

    fun readEntryBytes(path: String): ByteArray {
        val expected = entries.firstOrNull { it.path == path }
            ?: throw DdpPackageException("plugin package is missing $path")
        val bytes = try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(path) ?: throw DdpPackageException("plugin package is missing $path")
                zip.getInputStream(entry).use { input -> readLimited(input) }
            }
        } catch (error: DdpPackageException) {
            throw error
        } catch (error: Exception) {
            throw DdpPackageException("failed to read $path: ${error.message}", error)
        }
        if (bytes.size.toLong() != expected.size || DdpSigning.sha256Hex(bytes) != expected.sha256) {
            throw DdpPackageException("plugin package changed while reading $path")
        }
        return bytes
    }

    fun dexBuffers(): Array<ByteBuffer> = dexEntries.map { ByteBuffer.wrap(readEntryBytes(it.path)) }.toTypedArray()

    fun extractAssets(targetDir: File) {
        extract(entries.filter { it.path.startsWith(ASSETS_PREFIX) }, targetDir) { it.path.removePrefix(ASSETS_PREFIX) }
    }

    fun extractLib(targetDir: File) {
        extract(entries.filter { it.path.startsWith(LIB_PREFIX) }, targetDir) { it.path.removePrefix(LIB_PREFIX) }
    }

    fun copyBundleTo(target: File) {
        target.parentFile?.mkdirs()
        val copied = try {
            file.copyTo(target, overwrite = true)
        } catch (error: Exception) {
            throw DdpPackageException("failed to copy plugin bundle: ${error.message}", error)
        }
        val copiedSha = try {
            DdpSigning.sha256Hex(copied)
        } catch (error: Exception) {
            throw DdpPackageException("failed to verify copied plugin bundle: ${error.message}", error)
        }
        if (copied.length() != size || copiedSha != bundleSha256) {
            copied.delete()
            throw DdpPackageException("plugin bundle changed while copying")
        }
    }

    private fun extract(files: List<DdpEntry>, targetDir: File, relativePath: (DdpEntry) -> String) {
        if (files.isEmpty()) return
        val root = targetDir.canonicalFile
        ZipFile(file).use { zip ->
            files.forEach { metadata ->
                val relative = relativePath(metadata)
                val target = File(targetDir, relative).canonicalFile
                if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) {
                    throw DdpPackageException("unsafe extraction path: $relative")
                }
                val entry = zip.getEntry(metadata.path)
                    ?: throw DdpPackageException("plugin package is missing ${metadata.path}")
                target.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance(SHA_256)
                var written = 0L
                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            written += read
                            if (written > maxUncompressedBytes) {
                                throw DdpPackageException("plugin package contents are too large")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (written != metadata.size || DdpSigning.hex(digest.digest()) != metadata.sha256) {
                    target.delete()
                    throw DdpPackageException("plugin package changed while extracting ${metadata.path}")
                }
            }
        }
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxUncompressedBytes) throw DdpPackageException("plugin package contents are too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val MANIFEST_ENTRY: String = "assets/plugin.json"

        const val ASSETS_PREFIX: String = "assets/"

        const val LIB_PREFIX: String = "lib/"

        const val DEFAULT_MAX_BYTES: Long = 100L * 1024 * 1024

        const val DEFAULT_MAX_UNCOMPRESSED_BYTES: Long = 256L * 1024 * 1024

        private const val SHA_256 = "SHA-256"

        private const val BUFFER_SIZE = 64 * 1024

        private val DEX_NAME = Regex("^classes([0-9]*)\\.dex$")

        fun read(
            file: File,
            maxBytes: Long = DEFAULT_MAX_BYTES,
            maxUncompressedBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
        ): DdpPackage {
            if (!file.isFile) throw DdpPackageException("plugin package not found: ${file.path}")
            val size = file.length()
            if (size <= 0L) throw DdpPackageException("plugin package is empty")
            if (size > maxBytes) throw DdpPackageException("plugin package is too large: $size bytes")
            val bundleSha256 = try {
                DdpSigning.sha256Hex(file)
            } catch (error: Exception) {
                throw DdpPackageException("failed to read plugin package: ${error.message}", error)
            }
            val entries = mutableListOf<DdpEntry>()
            val seenPaths = mutableSetOf<String>()
            var manifestBytes: ByteArray? = null
            var totalBytes = 0L
            try {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        if (entry.isDirectory || entry.name.endsWith("/")) return@forEach
                        val path = entry.name
                        if (!PluginValidation.isSafeRelativePath(path)) {
                            throw DdpPackageException("unsafe plugin package entry: $path")
                        }
                        if (!seenPaths.add(path)) throw DdpPackageException("duplicate plugin package entry: $path")
                        val manifest = path == MANIFEST_ENTRY
                        val manifestOutput = if (manifest) ByteArrayOutputStream() else null
                        val digest = MessageDigest.getInstance(SHA_256)
                        var entryBytes = 0L
                        zip.getInputStream(entry).use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                entryBytes += read
                                totalBytes += read
                                if (entryBytes > maxUncompressedBytes || totalBytes > maxUncompressedBytes) {
                                    throw DdpPackageException("plugin package contents are too large")
                                }
                                digest.update(buffer, 0, read)
                                manifestOutput?.write(buffer, 0, read)
                            }
                        }
                        entries += DdpEntry(path, entryBytes, DdpSigning.hex(digest.digest()))
                        manifestOutput?.let { manifestBytes = it.toByteArray() }
                    }
                }
            } catch (error: DdpPackageException) {
                throw error
            } catch (error: Exception) {
                throw DdpPackageException("invalid plugin package: ${error.message}", error)
            }
            val manifestEntry = entries.firstOrNull { it.path == MANIFEST_ENTRY }
                ?: throw DdpPackageException("plugin package is missing $MANIFEST_ENTRY")
            val dexEntries = entries.mapNotNull { entry ->
                dexOrdinal(entry.path)?.let { ordinal -> ordinal to entry }
            }.sortedBy { it.first }.map { it.second }
            if (dexEntries.isEmpty()) throw DdpPackageException("plugin package has no classes*.dex")
            val duplicates = dexEntries.groupBy { dexOrdinal(it.path) }.filterValues { it.size > 1 }
            if (duplicates.isNotEmpty()) throw DdpPackageException("plugin package has duplicate dex entry: ${duplicates.keys.first()}")
            return DdpPackage(
                file = file,
                size = size,
                bundleSha256 = bundleSha256,
                entries = entries,
                manifestEntry = manifestEntry,
                dexEntries = dexEntries,
                signatureEntry = entries.firstOrNull { it.path == DdpSigning.SIGNATURE_ENTRY },
                manifestBytes = manifestBytes ?: ByteArray(0),
                maxUncompressedBytes = maxUncompressedBytes,
            )
        }

        internal fun dexOrdinal(path: String): Int? {
            val match = DEX_NAME.matchEntire(path) ?: return null
            val suffix = match.groupValues[1]
            return if (suffix.isEmpty()) 1 else suffix.toIntOrNull() ?: return null
        }
    }
}
