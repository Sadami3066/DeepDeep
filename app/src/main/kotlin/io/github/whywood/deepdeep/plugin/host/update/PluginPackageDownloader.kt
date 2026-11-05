package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.external.DdpSigning
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface DownloadResult {
    data class Ready(val file: File, val sha256: String, val size: Long) : DownloadResult

    data class Rejected(val code: UpdateRejection, val reason: String) : DownloadResult
}

internal class PluginPackageDownloader(
    private val fetcher: PluginFetcher,
    private val downloadsDir: File,
    private val maxPackageBytes: Long = PluginUpdateDefaults.MAX_PACKAGE_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun download(
        entry: PluginIndexEntry,
        onProgress: (Int?) -> Unit = {},
    ): DownloadResult = withContext(ioDispatcher) {
        if (entry.size <= 0 || entry.size > maxPackageBytes) {
            return@withContext DownloadResult.Rejected(
                UpdateRejection.PACKAGE_TOO_LARGE,
                "plugin '${entry.id}' declares ${entry.size} bytes, limit is $maxPackageBytes",
            )
        }
        val part = partFile(entry)
        val target = finalFile(entry)
        try {
            part.parentFile?.mkdirs()
            part.delete()
            fetcher.download(entry.url, part, maxPackageBytes, onProgress)
            val size = part.length()
            if (size != entry.size) {
                return@withContext DownloadResult.Rejected(
                    UpdateRejection.SIZE_MISMATCH,
                    "downloaded $size bytes but index declares ${entry.size}",
                )
            }
            val sha256 = DdpSigning.sha256Hex(part)
            if (!sha256.equals(entry.sha256, ignoreCase = true)) {
                return@withContext DownloadResult.Rejected(
                    UpdateRejection.SHA256_MISMATCH,
                    "downloaded package does not match index sha256",
                )
            }
            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            DownloadResult.Ready(target, sha256.lowercase(), size)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DownloadResult.Rejected(
                UpdateRejection.DOWNLOAD_FAILED,
                error.message ?: error.javaClass.simpleName,
            )
        } finally {
            if (part.exists()) part.delete()
        }
    }

    private fun partFile(entry: PluginIndexEntry): File = File(downloadsDir, "${prefix(entry)}.part")

    private fun finalFile(entry: PluginIndexEntry): File =
        File(downloadsDir, "${prefix(entry)}-${entry.sha256.take(8)}.ddp")

    private fun prefix(entry: PluginIndexEntry): String =
        "${safeSegment(entry.id)}-${safeSegment(entry.version)}"

    private fun safeSegment(value: String): String = value.map { char ->
        if (char.isLetterOrDigit() || char == '.' || char == '-' || char == '_') char else '_'
    }.joinToString("")
}
