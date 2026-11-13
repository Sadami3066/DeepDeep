package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.HostFiles
import io.github.whywood.deepdeep.data.ImageNormalizer
import io.github.whywood.deepdeep.data.saveStreamToDownloads
import io.github.whywood.deepdeep.design.DdImageScaling
import io.github.whywood.deepdeep.plugin.api.MediaService
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteraction
import io.github.whywood.deepdeep.ui.common.shareChooser
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MediaPluginServiceImpl(
    private val context: Context,
    private val pluginDataDir: File,
    private val hostFiles: HostFiles?,
    private val interaction: PluginInteraction? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaService {
    override suspend fun saveToDownloads(fileName: String, mimeType: String, bytes: ByteArray): String? =
        withContext(ioDispatcher) {
            val name = safeFileName(fileName) ?: return@withContext null
            saveStreamToDownloads(context, name, mimeType) { bytes.inputStream() }?.toString()
        }

    override suspend fun saveFileToDownloads(path: String, fileName: String?, mimeType: String?): String? =
        withContext(ioDispatcher) {
            val file = existingFile(path) ?: return@withContext null
            val name = safeFileName(fileName) ?: file.name
            saveStreamToDownloads(context, name, mimeType) {
                hostFiles?.openStream(file) ?: runCatching { file.inputStream() }.getOrNull()
            }?.toString()
        }

    override suspend fun saveImageToGallery(fileName: String, mimeType: String, bytes: ByteArray): String? =
        withContext(ioDispatcher) {
            val name = safeFileName(fileName) ?: return@withContext null
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType.takeIf { it.isNotBlank() } ?: "image/jpeg")
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DeepDeep")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            val copied = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> output.write(bytes) } != null
            }.getOrDefault(false)
            if (!copied) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                return@withContext null
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri.toString()
        }

    override suspend fun scanFile(path: String): Boolean = withContext(ioDispatcher) {
        val file = existingFile(path) ?: return@withContext false
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        true
    }

    override suspend fun open(path: String): Boolean = withContext(ioDispatcher) {
        val file = existingFile(path) ?: return@withContext false
        val uri = contentUri(file) ?: return@withContext false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeTypeOf(file))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(intent)
    }

    override suspend fun share(path: String, mimeType: String?): Boolean = withContext(ioDispatcher) {
        val file = existingFile(path) ?: return@withContext false
        val uri = contentUri(file) ?: return@withContext false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType?.takeIf { it.isNotBlank() } ?: mimeTypeOf(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(shareChooser(context, send, context.getString(R.string.files_share)))
    }

    override suspend fun preview(path: String): Boolean = open(path)

    override suspend fun readImage(path: String, maxSizePx: Int): ByteArray? = withContext(ioDispatcher) {
        val file = existingFile(path) ?: return@withContext null
        val limit = maxSizePx.coerceIn(1, MAX_IMAGE_SIZE)
        if (limit >= ImageNormalizer.MAX_EDGE) {
            val normalized = ImageNormalizer.normalize(file, mimeTypeOf(file)) ?: return@withContext null
            runCatching { Base64.getDecoder().decode(normalized.data) }.getOrNull()
        } else {
            decodeScaled(file, limit)
        }
    }

    override suspend fun thumbnail(path: String, maxSizePx: Int): ByteArray? = withContext(ioDispatcher) {
        val file = existingFile(path) ?: return@withContext null
        decodeScaled(file, maxSizePx.coerceIn(1, MAX_IMAGE_SIZE))
    }

    override suspend fun importUri(uri: String, fileName: String?): String? = withContext(ioDispatcher) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
        interaction?.importUri(parsed, fileName)?.path?.let { path ->
            return@withContext path.takeIf { File(it).isFile }
        }
        val directory = File(pluginDataDir, IMPORT_DIRECTORY).apply { mkdirs() }
        val name = safeFileName(fileName) ?: guessedName(parsed) ?: "import-${UUID.randomUUID()}"
        val target = File(directory, name)
        val copied = runCatching {
            context.contentResolver.openInputStream(parsed)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            runCatching { target.delete() }
            return@withContext null
        }
        target.absolutePath
    }

    private fun existingFile(path: String): File? {
        val file = File(path)
        val readable = hostFiles?.isFile(file) ?: file.isFile
        return file.takeIf { readable }
    }

    private fun contentUri(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun mimeTypeOf(file: File): String =
        runCatching { context.contentResolver.getType(Uri.fromFile(file)) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "pdf" -> "application/pdf"
                "txt", "md" -> "text/plain"
                "json" -> "application/json"
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }

    private fun decodeScaled(file: File, maxSizePx: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = DdImageScaling.sampleSizeFor(width, height, DdImageScaling.MAX_DECODE_PIXELS)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val longEdge = maxOf(decoded.width, decoded.height)
        val scaled = if (longEdge > maxSizePx) {
            val ratio = maxSizePx.toDouble() / longEdge
            val targetWidth = (decoded.width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (decoded.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        return try {
            ByteArrayOutputStream().use { output ->
                val format = if (scaled.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                scaled.compress(format, if (format == Bitmap.CompressFormat.JPEG) 85 else 100, output)
                output.toByteArray()
            }
        } finally {
            scaled.recycle()
        }
    }

    private fun safeFileName(raw: String?): String? {
        val name = raw?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotEmpty() }
            ?: return null
        return name.take(MAX_FILE_NAME).replace(Regex("[^A-Za-z0-9._-]"), "_").takeIf { it.isNotEmpty() }
    }

    private fun guessedName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()?.let(::safeFileName)

    private companion object {
        const val IMPORT_DIRECTORY = "imports"

        const val MAX_IMAGE_SIZE = 4_096

        const val MAX_FILE_NAME = 200
    }
}
