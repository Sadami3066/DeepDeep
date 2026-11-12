package io.github.whywood.deepdeep.plugin.host.external

import android.content.Context
import android.util.Log
import dalvik.system.InMemoryDexClassLoader
import dalvik.system.PathClassLoader
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException

internal class PluginLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class PluginApiLevelRejectedException(val declaredApiLevel: Int) :
    Exception("plugin apiLevel $declaredApiLevel is not supported")

internal class LoadedPluginHandle private constructor(
    val pluginId: String,
    val version: String,
    val bundleSha256: String,
    val manifest: PluginManifest,
    val plugin: DeepDeepPlugin,
    val classLoader: ClassLoader,
    private val fallbackJar: File?,
) {
    @Volatile
    private var closed = false

    val isClosed: Boolean get() = closed

    fun matches(version: String, bundleSha256: String): Boolean =
        this.version == version && this.bundleSha256.equals(bundleSha256, ignoreCase = true)

    fun isLoaded(record: ExternalPluginRecord): Boolean =
        record.version == version && record.activeBundleSha256.equals(bundleSha256, ignoreCase = true)

    fun hasPendingUpdate(record: ExternalPluginRecord): Boolean =
        record.pendingVersion != null && record.pendingVersion != version

    suspend fun close() {
        if (closed) return
        closed = true
        runCatching { plugin.close() }
        fallbackJar?.let { jar -> runCatching { jar.delete() } }
    }

    companion object {
        fun load(
            context: Context,
            bundleFile: File,
            expectedSha256: String,
            expectedPublisherKeyId: String,
            hostApiLevel: Int = PLUGIN_API_LEVEL,
            hostVersion: String? = null,
        ): LoadedPluginHandle {
            val pkg = try {
                DdpPackage.read(bundleFile)
            } catch (error: CancellationException) {
                throw error
            } catch (error: DdpPackageException) {
                throw PluginLoadException("invalid plugin bundle: ${error.message}", error)
            } catch (error: Throwable) {
                throw PluginLoadException(
                    "failed to read plugin bundle: ${error.message ?: error.javaClass.simpleName}",
                    error,
                )
            }
            if (!pkg.bundleSha256.equals(expectedSha256, ignoreCase = true)) {
                throw PluginLoadException("plugin bundle sha256 mismatch: ${bundleFile.path}")
            }
            val verification = try {
                PluginPackageVerifier.verify(pkg, hostApiLevel, hostVersion)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw PluginLoadException(
                    "plugin signature verification failed: ${error.message ?: error.javaClass.simpleName}",
                    error,
                )
            }
            if (verification is VerificationResult.Rejected) {
                if (verification.code == VerificationCode.API_LEVEL_UNSUPPORTED) {
                    throw PluginApiLevelRejectedException(verification.declaredApiLevel ?: -1)
                }
                throw PluginLoadException("plugin bundle verification failed: ${verification.reason}")
            }
            val verified = verification as VerificationResult.Ok
            if (!verified.publisher.keyId.equals(expectedPublisherKeyId, ignoreCase = true)) {
                throw PluginLoadException("plugin publisher key does not match the installed record")
            }
            return load(context, bundleFile, verified.manifest, expectedSha256)
        }

        fun load(
            context: Context,
            bundleFile: File,
            manifest: PluginManifest,
            expectedSha256: String,
        ): LoadedPluginHandle {
            if (!bundleFile.isFile) throw PluginLoadException("plugin bundle not found: ${bundleFile.path}")
            val actualSha256 = try {
                DdpSigning.sha256Hex(bundleFile)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw PluginLoadException("failed to read plugin bundle: ${error.message}", error)
            }
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw PluginLoadException("plugin bundle sha256 mismatch: ${bundleFile.path}")
            }
            val entryClass = manifest.entryClass?.takeIf { it.isNotBlank() }
                ?: throw PluginLoadException("plugin manifest is missing entryClass")
            val dexBytes = readDexBytes(bundleFile)
            val buffers = dexBytes.map { ByteBuffer.wrap(it) }.toTypedArray()
            val memoryFailure = try {
                val loader = InMemoryDexClassLoader(buffers, context.classLoader)
                return LoadedPluginHandle(
                    pluginId = manifest.id,
                    version = manifest.version,
                    bundleSha256 = actualSha256,
                    manifest = manifest,
                    plugin = instantiate(entryClass, loader),
                    classLoader = loader,
                    fallbackJar = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e("PluginLoad", "in-memory dex load failed: ${manifest.id}", error)
                error
            }
            val jar = writeReadOnlyJar(context, manifest.id, actualSha256, dexBytes)
            return try {
                val loader = PathClassLoader(jar.absolutePath, context.classLoader)
                LoadedPluginHandle(
                    pluginId = manifest.id,
                    version = manifest.version,
                    bundleSha256 = actualSha256,
                    manifest = manifest,
                    plugin = instantiate(entryClass, loader),
                    classLoader = loader,
                    fallbackJar = jar,
                )
            } catch (error: CancellationException) {
                jar.delete()
                throw error
            } catch (error: Throwable) {
                Log.e("PluginLoad", "path class loader load failed: ${manifest.id}", error)
                jar.delete()
                val failure = PluginLoadException(
                    "failed to load plugin ${manifest.id}: ${error.message ?: error.javaClass.simpleName}",
                    error,
                )
                failure.addSuppressed(memoryFailure)
                throw failure
            }
        }

        private fun instantiate(entryClass: String, loader: ClassLoader): DeepDeepPlugin {
            val type = try {
                Class.forName(entryClass, true, loader)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw PluginLoadException("plugin entry class could not be loaded: $entryClass", error)
            }
            if (!DeepDeepPlugin::class.java.isAssignableFrom(type)) {
                throw PluginLoadException("$entryClass does not implement DeepDeepPlugin")
            }
            return try {
                val constructor = type.getDeclaredConstructor()
                constructor.isAccessible = true
                constructor.newInstance() as DeepDeepPlugin
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw PluginLoadException("failed to instantiate plugin entry class: $entryClass", error)
            }
        }

        private fun readDexBytes(bundleFile: File): List<ByteArray> {
            val dex = try {
                ZipFile(bundleFile).use { zip ->
                    zip.entries().asSequence()
                        .mapNotNull { entry ->
                            DdpPackage.dexOrdinal(entry.name)?.let { ordinal -> ordinal to entry }
                        }
                        .sortedBy { it.first }
                        .map { (_, entry) -> zip.getInputStream(entry).use { it.readBytes() } }
                        .toList()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                throw PluginLoadException("failed to read plugin dex: ${error.message}", error)
            }
            if (dex.isEmpty()) throw PluginLoadException("plugin bundle has no classes*.dex")
            return dex
        }

        private fun writeReadOnlyJar(
            context: Context,
            pluginId: String,
            bundleSha256: String,
            dexBytes: List<ByteArray>,
        ): File {
            val directory = (context.codeCacheDir ?: context.cacheDir).let { File(it, "plugin-dex").apply { mkdirs() } }
            val jar = File(directory, "plugin-${sanitize(pluginId)}-${bundleSha256.take(16)}.jar")
            jar.delete()
            try {
                ZipOutputStream(jar.outputStream().buffered()).use { zip ->
                    dexBytes.forEachIndexed { index, bytes ->
                        val name = if (index == 0) "classes.dex" else "classes${index + 1}.dex"
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                }
                if (!jar.setReadOnly()) throw PluginLoadException("failed to make fallback dex jar read-only")
                if (!jar.isFile) throw PluginLoadException("failed to write fallback dex jar")
            } catch (error: Exception) {
                jar.delete()
                if (error is PluginLoadException) throw error
                throw PluginLoadException("failed to write fallback dex jar: ${error.message}", error)
            }
            return jar
        }

        private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
