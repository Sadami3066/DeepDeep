package io.github.whywood.deepdeep.data.session

import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.SessionHistory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

enum class SessionArchiveError {
    NotZip,
    MissingManifest,
    InvalidManifest,
    UnsupportedVersion,
    TooLarge,
    Io,
}

sealed interface ExportResult {
    data class Success(val sessions: Int, val markdown: Int, val skipped: Int) : ExportResult

    data class Failure(val error: SessionArchiveError) : ExportResult
}

sealed interface ImportResult {
    data class Success(val imported: Int, val skipped: Int) : ImportResult

    data class Failure(val error: SessionArchiveError) : ImportResult
}

object SessionArchive {
    const val SCHEMA_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val SESSIONS_DIR = "sessions"
    const val MARKDOWN_DIR = "markdown"
    const val SESSION_SUFFIX = ".jsonl"

    private const val MAX_MANIFEST_BYTES = 4L * 1024 * 1024
    private const val MAX_SESSION_BYTES = 64L * 1024 * 1024
    private const val BUFFER_SIZE = 64 * 1024

    private val json = Json { prettyPrint = true }

    suspend fun export(
        target: File,
        sessions: List<ConversationSummary>,
        includeMarkdown: Boolean,
        appVersion: String,
        markdownFor: suspend (ConversationSummary) -> String?,
    ): ExportResult {
        val exported = mutableListOf<ConversationSummary>()
        val seen = mutableSetOf<String>()
        var skipped = 0
        sessions.forEach { session ->
            val file = File(session.filePath)
            val usable = isSafeSessionId(session.id) && seen.add(session.id) &&
                session.filePath.isNotBlank() && file.isFile && file.canRead()
            if (usable) {
                exported += session
            } else {
                skipped++
            }
        }
        var markdownCount = 0
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
                val manifest = buildJsonObject {
                    put("schema", SCHEMA_VERSION)
                    put("createdAt", System.currentTimeMillis())
                    put("appVersion", appVersion)
                    put("markdown", includeMarkdown)
                    putJsonArray("sessions") {
                        exported.forEach { session ->
                            addJsonObject {
                                put("id", session.id)
                                put("title", session.title)
                                put("updatedAt", session.updatedAt)
                                put("cwd", session.cwd)
                                put("filePath", session.filePath)
                            }
                        }
                    }
                }
                zip.writeEntry(MANIFEST_ENTRY, json.encodeToString(JsonElement.serializer(), manifest).toByteArray(Charsets.UTF_8))
                exported.forEach { session ->
                    zip.writeEntry(sessionEntryName(session.id)) { output ->
                        FileInputStream(session.filePath).use { input -> input.copyTo(output) }
                    }
                }
                if (includeMarkdown) {
                    exported.forEach { session ->
                        val markdown = try {
                            markdownFor(session)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            null
                        }
                        if (markdown.isNullOrBlank()) return@forEach
                        zip.writeEntry(markdownEntryName(session), markdown.toByteArray(Charsets.UTF_8))
                        markdownCount++
                    }
                }
            }
            ExportResult.Success(exported.size, markdownCount, skipped)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            ExportResult.Failure(SessionArchiveError.Io)
        } catch (error: RuntimeException) {
            ExportResult.Failure(SessionArchiveError.Io)
        }
    }

    fun import(source: File, allowedRoot: File): ImportResult {
        if (!source.isFile || !hasZipSignature(source)) {
            return ImportResult.Failure(SessionArchiveError.NotZip)
        }
        val manifest = try {
            readManifest(source)
        } catch (error: ZipException) {
            return ImportResult.Failure(SessionArchiveError.NotZip)
        } catch (error: IOException) {
            return ImportResult.Failure(SessionArchiveError.Io)
        } catch (error: RuntimeException) {
            return ImportResult.Failure(SessionArchiveError.Io)
        }
        return when (manifest) {
            ManifestRead.Missing -> ImportResult.Failure(SessionArchiveError.MissingManifest)
            ManifestRead.TooLarge -> ImportResult.Failure(SessionArchiveError.TooLarge)
            is ManifestRead.Found -> importManifest(source, manifest.bytes, allowedRoot)
        }
    }

    private fun importManifest(source: File, manifestBytes: ByteArray, allowedRoot: File): ImportResult {
        val root = runCatching {
            json.parseToJsonElement(manifestBytes.toString(Charsets.UTF_8)) as? JsonObject
        }.getOrNull() ?: return ImportResult.Failure(SessionArchiveError.InvalidManifest)
        val schema = root.stringValue("schema")?.toIntOrNull()
            ?: return ImportResult.Failure(SessionArchiveError.InvalidManifest)
        if (schema != SCHEMA_VERSION) {
            return ImportResult.Failure(
                if (schema > SCHEMA_VERSION) SessionArchiveError.UnsupportedVersion else SessionArchiveError.InvalidManifest,
            )
        }
        val sessionElements = root["sessions"] as? JsonArray
            ?: return ImportResult.Failure(SessionArchiveError.InvalidManifest)
        var skipped = 0
        val sessions = linkedMapOf<String, ManifestSession>()
        sessionElements.forEach { element ->
            val session = parseManifestSession(element)
            if (session == null) {
                skipped++
            } else if (sessions.put(session.id, session) != null) {
                skipped++
            }
        }
        val allowedRootFile = runCatching { allowedRoot.canonicalFile }.getOrNull()
            ?: return ImportResult.Failure(SessionArchiveError.Io)
        val handled = mutableSetOf<String>()
        val seen = mutableSetOf<String>()
        var imported = 0
        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val id = sessionEntryId(entry.name)
                    if (id != null) {
                        seen += id
                        val session = sessions[id]
                        val written = session != null && handled.add(id) && writeSession(zip, session, allowedRootFile)
                        if (written) imported++ else skipped++
                    }
                    entry = zip.nextEntry
                }
            }
            skipped += sessions.keys.count { it !in seen }
            ImportResult.Success(imported, skipped)
        } catch (error: ZipException) {
            ImportResult.Failure(SessionArchiveError.NotZip)
        } catch (error: IOException) {
            ImportResult.Failure(SessionArchiveError.Io)
        } catch (error: RuntimeException) {
            ImportResult.Failure(SessionArchiveError.Io)
        }
    }

    private fun writeSession(zip: ZipInputStream, session: ManifestSession, allowedRoot: File): Boolean {
        val destination = destinationFor(session, allowedRoot) ?: return false
        if (hasSessionFile(allowedRoot, session.id)) return false
        val parent = destination.parentFile ?: return false
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) return false
        val temp = runCatching { File.createTempFile(".session-", ".tmp", parent) }.getOrNull() ?: return false
        return try {
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_SESSION_BYTES) return false
                    output.write(buffer, 0, read)
                }
            }
            matchesSessionHeader(temp, session) && moveIntoPlace(temp, destination)
        } finally {
            temp.delete()
        }
    }

    private fun moveIntoPlace(source: File, destination: File): Boolean = try {
        Files.move(source.toPath(), destination.toPath())
        true
    } catch (error: FileAlreadyExistsException) {
        false
    } catch (error: IOException) {
        false
    }

    private fun hasSessionFile(root: File, id: String): Boolean {
        val fileName = id + SESSION_SUFFIX
        val timestampedSuffix = "_$fileName"
        return root.walkTopDown().any { file ->
            file.isFile && (file.name == fileName || file.name.endsWith(timestampedSuffix))
        }
    }

    private fun destinationFor(session: ManifestSession, allowedRoot: File): File? =
        localFilePath(session, allowedRoot) ?: cwdDestination(session, allowedRoot)

    private fun localFilePath(session: ManifestSession, allowedRoot: File): File? {
        val filePath = session.filePath
        if (filePath.isBlank()) return null
        val file = File(filePath)
        if (!file.isAbsolute) return null
        if (file.name != session.id + SESSION_SUFFIX) return null
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val root = runCatching { allowedRoot.canonicalFile.toPath() }.getOrNull() ?: return null
        if (!canonical.toPath().startsWith(root)) return null
        if (canonical.parentFile?.isDirectory != true) return null
        return canonical
    }

    private fun cwdDestination(session: ManifestSession, allowedRoot: File): File? {
        val directory = SessionHistory.sessionsDirectory(session.cwd, allowedRoot) ?: return null
        return File(directory, session.id + SESSION_SUFFIX)
    }

    private fun matchesSessionHeader(file: File, session: ManifestSession): Boolean {
        val line = runCatching {
            file.inputStream().bufferedReader(Charsets.UTF_8).use { reader -> reader.readLine() }
        }.getOrNull() ?: return false
        val header = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return false
        if (header.stringValue("type") != "session") return false
        if (header.stringValue("id") != session.id) return false
        if (session.cwd.isNotBlank() && header.stringValue("cwd") != session.cwd) return false
        return true
    }

    private fun readManifest(source: File): ManifestRead {
        ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.name.endsWith("/") && entry.name == MANIFEST_ENTRY) {
                    val bytes = readLimited(zip, MAX_MANIFEST_BYTES)
                    return if (bytes == null) ManifestRead.TooLarge else ManifestRead.Found(bytes)
                }
                entry = zip.nextEntry
            }
        }
        return ManifestRead.Missing
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun hasZipSignature(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val signature = ByteArray(4)
            if (input.read(signature) < 4) return@use false
            val a = signature[0].toInt() and 0xFF
            val b = signature[1].toInt() and 0xFF
            val c = signature[2].toInt() and 0xFF
            val d = signature[3].toInt() and 0xFF
            a == 0x50 && b == 0x4B && ((c == 3 && d == 4) || (c == 5 && d == 6) || (c == 7 && d == 8))
        }
    }.getOrDefault(false)

    private fun parseManifestSession(element: JsonElement): ManifestSession? {
        val obj = element as? JsonObject ?: return null
        val id = obj.stringValue("id")?.takeIf { isSafeSessionId(it) } ?: return null
        val cwd = obj.stringValue("cwd").orEmpty()
        val filePath = obj.stringValue("filePath").orEmpty()
        if (cwd.isBlank() && filePath.isBlank()) return null
        return ManifestSession(id, cwd, filePath)
    }

    private fun isSafeSessionId(id: String): Boolean =
        id.isNotBlank() &&
            id.length <= 128 &&
            id != "." &&
            id != ".." &&
            !id.contains("..") &&
            id.none { character ->
                character == '/' || character == '\\' || character == ':' ||
                    character.code < 0x20 || character.code == 0x7F
            }

    private fun sessionEntryId(name: String): String? {
        val prefix = "$SESSIONS_DIR/"
        if (!name.startsWith(prefix) || !name.endsWith(SESSION_SUFFIX)) return null
        val id = name.substring(prefix.length, name.length - SESSION_SUFFIX.length)
        if (id.isEmpty() || id.contains('/')) return null
        return id.takeIf { isSafeSessionId(it) }
    }

    private fun sessionEntryName(id: String): String = "$SESSIONS_DIR/$id$SESSION_SUFFIX"

    private fun markdownEntryName(session: ConversationSummary): String {
        val name = sanitizeFileName(session.title).ifBlank { session.id }
        return "$MARKDOWN_DIR/$name-${session.id}.md"
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = buildString {
            raw.forEach { character ->
                val invalid = character == '/' || character == '\\' || character == ':' ||
                    character == '*' || character == '?' || character == '"' ||
                    character == '<' || character == '>' || character == '|' || character.code < 0x20
                append(if (invalid) '_' else character)
            }
        }
        return cleaned.trim().take(40).trimEnd('.', ' ')
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeEntry(name: String, fill: (zip: ZipOutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        fill(this)
        closeEntry()
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private sealed interface ManifestRead {
        object Missing : ManifestRead

        object TooLarge : ManifestRead

        class Found(val bytes: ByteArray) : ManifestRead
    }

    private class ManifestSession(
        val id: String,
        val cwd: String,
        val filePath: String,
    )
}
