package io.github.whywood.deepdeep.data

import dev.pi.coding.prompt.Frontmatter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

internal const val SKILL_MD_FILE = "SKILL.md"

private const val MAX_ARCHIVE_BYTES = 32L * 1024L * 1024L
private const val MAX_EXTRACTED_BYTES = 64L * 1024L * 1024L
private const val MAX_ENTRY_COUNT = 1000
private const val MAX_SKILL_ID_LENGTH = 64
private const val FALLBACK_SKILL_ID = "skill"
private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
private const val MACOS_METADATA_DIRECTORY = "__MACOSX"

data class ParsedSkillFile(
    val path: String,
    val bytes: ByteArray,
)

data class ParsedSkill(
    val id: String,
    val name: String,
    val description: String,
    val disableModelInvocation: Boolean,
    val content: String,
    val files: List<ParsedSkillFile>,
)

internal data class SkillMetadata(
    val name: String,
    val description: String,
    val disableModelInvocation: Boolean,
)

sealed interface SkillArchiveResult {
    data class Success(val skill: ParsedSkill) : SkillArchiveResult

    data class Failure(val error: SkillArchiveError) : SkillArchiveResult
}

enum class SkillArchiveError {
    NotZip,
    Empty,
    NoSkill,
    MultipleSkills,
    MissingDescription,
    UnsafePath,
    TooLarge,
    Io,
}

private data class ArchiveEntry(
    val name: String,
    val bytes: ByteArray,
)

object SkillArchive {
    fun parse(bytes: ByteArray, fallbackName: String = ""): SkillArchiveResult {
        if (bytes.size > MAX_ARCHIVE_BYTES) return failure(SkillArchiveError.TooLarge)
        if (!hasZipSignature(bytes)) return failure(SkillArchiveError.NotZip)
        if (hasEncryptedEntry(bytes)) return failure(SkillArchiveError.NotZip)
        val read = readEntries(bytes)
        if (read is EntryRead.Failed) return failure(read.error)
        val entries = (read as EntryRead.Entries).entries
        if (entries.isEmpty()) return failure(SkillArchiveError.NoSkill)
        val skillFiles = entries.filter { entry ->
            !isIgnoredEntry(entry.name) &&
                (entry.name == SKILL_MD_FILE || entry.name.endsWith("/$SKILL_MD_FILE"))
        }
        if (skillFiles.isEmpty()) return failure(SkillArchiveError.NoSkill)
        val candidates = skillFiles.filter { entry ->
            entry.name == SKILL_MD_FILE || entry.name.count { it == '/' } == 1
        }
        if (candidates.isEmpty()) return failure(SkillArchiveError.NoSkill)
        if (skillFiles.size > 1) return failure(SkillArchiveError.MultipleSkills)
        val skillEntry = candidates.single()
        val wrapperPrefix = if (skillEntry.name == SKILL_MD_FILE) {
            ""
        } else {
            skillEntry.name.substringBeforeLast('/') + "/"
        }
        val content = String(skillEntry.bytes, Charsets.UTF_8)
        val wrapperName = wrapperPrefix.removeSuffix("/").substringAfterLast('/')
        val metadata = parseSkillMetadata(content, fallbackName, wrapperName)
            ?: return failure(SkillArchiveError.MissingDescription)
        val id = sanitizeSkillId(metadata.name)
        val files = entries
            .filter { entry ->
                entry.name != skillEntry.name &&
                    !isIgnoredEntry(entry.name) &&
                    (wrapperPrefix.isEmpty() || entry.name.startsWith(wrapperPrefix))
            }
            .map { entry ->
                ParsedSkillFile(
                    path = entry.name.removePrefix(wrapperPrefix),
                    bytes = entry.bytes,
                )
            }
            .sortedBy { it.path }
        return SkillArchiveResult.Success(
            ParsedSkill(
                id = id,
                name = metadata.name,
                description = metadata.description,
                disableModelInvocation = metadata.disableModelInvocation,
                content = content,
                files = files,
            ),
        )
    }
}

internal fun parseSkillMetadata(
    content: String,
    fallbackName: String = "",
    wrapperName: String = "",
): SkillMetadata? {
    val parsed = runCatching { Frontmatter.parse(content) }.getOrNull() ?: return null
    val frontmatter = parsed.frontmatter
    val description = frontmatter["description"]?.trim().orEmpty()
    if (description.isEmpty()) return null
    val rawName = frontmatter["name"]?.trim().orEmpty()
    val name = rawName
        .ifBlank { fallbackName.trim() }
        .ifBlank { wrapperName }
        .ifBlank { FALLBACK_SKILL_ID }
    val disableModelInvocation =
        frontmatter["disable-model-invocation"]?.trim()?.equals("true", ignoreCase = true) == true
    return SkillMetadata(
        name = name,
        description = description,
        disableModelInvocation = disableModelInvocation,
    )
}

internal fun sanitizeSkillId(raw: String): String {
    val builder = StringBuilder()
    var pendingDash = false
    for (character in raw) {
        val allowed = character.isLetterOrDigit() ||
            character == '-' ||
            character == '_' ||
            character == '.'
        if (allowed) {
            if (pendingDash && builder.isNotEmpty()) builder.append('-')
            pendingDash = false
            builder.append(character)
        } else if (builder.isNotEmpty()) {
            pendingDash = true
        }
    }
    var id = builder.toString()
    while (id.contains("--")) id = id.replace("--", "-")
    id = id.trim('-')
    if (id.length > MAX_SKILL_ID_LENGTH) id = id.take(MAX_SKILL_ID_LENGTH).trimEnd('-')
    if (id.isEmpty() || id == "." || id == "..") return FALLBACK_SKILL_ID
    return id
}

private sealed interface EntryRead {
    data class Entries(val entries: List<ArchiveEntry>) : EntryRead

    data class Failed(val error: SkillArchiveError) : EntryRead
}

private fun readEntries(bytes: ByteArray): EntryRead {
    val entries = mutableListOf<ArchiveEntry>()
    var entryCount = 0
    var extractedBytes = 0L
    return try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount += 1
                if (entryCount > MAX_ENTRY_COUNT) return EntryRead.Failed(SkillArchiveError.TooLarge)
                val name = entry.name
                if (!isSafeEntryName(name)) return EntryRead.Failed(SkillArchiveError.UnsafePath)
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val ignored = isIgnoredEntry(name)
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    extractedBytes += read
                    if (extractedBytes > MAX_EXTRACTED_BYTES) {
                        return EntryRead.Failed(SkillArchiveError.TooLarge)
                    }
                    if (!ignored) output.write(buffer, 0, read)
                }
                zip.closeEntry()
                if (!ignored) entries += ArchiveEntry(name = name, bytes = output.toByteArray())
            }
        }
        if (entryCount == 0) {
            EntryRead.Failed(SkillArchiveError.Empty)
        } else {
            EntryRead.Entries(entries)
        }
    } catch (error: ZipException) {
        EntryRead.Failed(SkillArchiveError.NotZip)
    } catch (error: IOException) {
        EntryRead.Failed(SkillArchiveError.Io)
    } catch (error: RuntimeException) {
        EntryRead.Failed(SkillArchiveError.NotZip)
    }
}

private fun isSafeEntryName(name: String): Boolean {
    if (name.isEmpty()) return false
    if (name.contains('\\')) return false
    if (name.startsWith('/')) return false
    if (name.length >= 2 && name[1] == ':') return false
    return name.split('/').none { it == ".." }
}

private fun isIgnoredEntry(name: String): Boolean = name.split('/').any { segment ->
    segment == MACOS_METADATA_DIRECTORY || segment.startsWith('.')
}

private fun hasZipSignature(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    if (bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return false
    val third = bytes[2].toInt() and 0xFF
    val fourth = bytes[3].toInt() and 0xFF
    return (third == 3 && fourth == 4) ||
        (third == 5 && fourth == 6) ||
        (third == 7 && fourth == 8)
}

private fun hasEncryptedEntry(bytes: ByteArray): Boolean {
    val end = findEndOfCentralDirectory(bytes) ?: return false
    if (end + 22 > bytes.size) return false
    val totalEntries = readUShort(bytes, end + 10)
    var offset = readUInt(bytes, end + 16)
    if (offset <= 0L || offset >= bytes.size.toLong()) return false
    repeat(totalEntries) {
        val position = offset.toInt()
        if (position + 46 > bytes.size) return false
        if (readUInt(bytes, position) != CENTRAL_DIRECTORY_SIGNATURE) return false
        val flags = readUShort(bytes, position + 8)
        if (flags and 0x1 != 0) return true
        val nameLength = readUShort(bytes, position + 28)
        val extraLength = readUShort(bytes, position + 30)
        val commentLength = readUShort(bytes, position + 32)
        offset = position + 46L + nameLength + extraLength + commentLength
    }
    return false
}

private fun findEndOfCentralDirectory(bytes: ByteArray): Int? {
    val minimum = (bytes.size - 22).coerceAtLeast(0)
    val maximum = (bytes.size - 22 - 0xFFFF).coerceAtLeast(0)
    var position = minimum
    while (position >= maximum) {
        if (readUInt(bytes, position) == END_OF_CENTRAL_DIRECTORY_SIGNATURE) return position
        position -= 1
    }
    return null
}

private fun readUInt(bytes: ByteArray, offset: Int): Long {
    if (offset < 0 || offset + 4 > bytes.size) return -1L
    return (bytes[offset].toLong() and 0xFF) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}

private fun readUShort(bytes: ByteArray, offset: Int): Int {
    if (offset < 0 || offset + 2 > bytes.size) return 0
    return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
}

private fun failure(error: SkillArchiveError): SkillArchiveResult = SkillArchiveResult.Failure(error)

private const val BUFFER_SIZE = 8 * 1024
