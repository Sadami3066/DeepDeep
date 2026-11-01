package io.github.whywood.deepdeep.runtime

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CreatedDraft(
    val manifest: WorkshopManifest,
    val directory: File,
)

class WorkshopStore(
    root: File,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    val rootDir: File = root.absoluteFile
    val appsDir: File = File(rootDir, APPS_DIR)
    val buildDir: File = File(rootDir, BUILD_DIR)
    val userdataDir: File = File(rootDir, USERDATA_DIR)

    suspend fun scan(): WorkshopScan = withContext(Dispatchers.IO) {
        if (!appsDir.isDirectory) return@withContext WorkshopScan(emptyList(), emptyList())
        val creations = mutableListOf<WorkshopCreation>()
        val invalid = mutableListOf<WorkshopInvalid>()
        appDirectories().forEach { directory ->
            when (val inspection = inspect(directory)) {
                is Inspection.Valid -> creations.add(inspection.creation)
                is Inspection.Invalid -> invalid.add(inspection.invalid)
                Inspection.Ignored -> Unit
            }
        }
        WorkshopScan(creations, invalid)
    }

    suspend fun load(id: String): WorkshopEntry = withContext(Dispatchers.IO) {
        val directory = guardedAppDirectory(id) ?: return@withContext WorkshopEntry.Missing
        if (!directory.isDirectory) return@withContext WorkshopEntry.Missing
        when (val inspection = inspect(directory)) {
            is Inspection.Valid -> WorkshopEntry.Valid(inspection.creation)
            is Inspection.Invalid -> WorkshopEntry.Invalid(
                id = inspection.invalid.id,
                reason = inspection.invalid.reason,
                detail = inspection.invalid.detail,
            )
            Inspection.Ignored -> WorkshopEntry.Missing
        }
    }

    suspend fun drafts(): List<WorkshopDraft> = withContext(Dispatchers.IO) {
        draftDirectories().map { directory ->
            WorkshopDraft(directory = directory, manifest = readManifest(directory))
        }
    }

    suspend fun createDraft(
        name: String,
        description: String = "",
        icon: String? = null,
    ): CreatedDraft = withContext(Dispatchers.IO) {
        ensureRoot()
        val manifest = newManifest(
            id = uniqueId(slug(name)),
            name = name,
            description = description,
            icon = icon,
        )
        val directory = newDraftDirectory(manifest.id)
        if (!directory.mkdirs()) {
            throw WorkshopManifestException("cannot create draft directory: ${directory.absolutePath}")
        }
        try {
            writeManifest(directory, manifest)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
        CreatedDraft(manifest = manifest, directory = directory)
    }

    suspend fun loadDraft(draftPath: String): WorkshopCreation = withContext(Dispatchers.IO) {
        ensureRoot()
        val raw = draftPath.trim()
        if (!File(raw).isAbsolute) {
            throw WorkshopLoadException(
                "draft path must be absolute, such as ${File(buildDir, "my-app-abcde").absolutePath}; " +
                    "workshop_create returns the exact draft path to use. Got relative path: $raw",
            )
        }
        val draft = File(raw).canonicalFile
        if (!draft.toPath().startsWith(buildDir.canonicalFile.toPath()) || draft == buildDir.canonicalFile) {
            throw WorkshopLoadException(
                "draft path is not inside the workshop build folder: $draftPath; " +
                    "use the absolute draft path returned by workshop_create, such as ${File(buildDir, "my-app-abcde").absolutePath}",
            )
        }
        if (!draft.isDirectory) {
            throw WorkshopLoadException("draft folder does not exist: $draftPath")
        }
        if (Files.isSymbolicLink(draft.toPath())) {
            throw WorkshopLoadException("draft folder is a symbolic link: $draftPath")
        }
        val manifestFile = File(draft, MANIFEST_FILE)
        if (!manifestFile.isFile) {
            throw WorkshopLoadException("draft is missing manifest.json: $draftPath")
        }
        val text = try {
            manifestFile.readText()
        } catch (error: Exception) {
            throw WorkshopLoadException("draft manifest.json cannot be read: ${error.message}")
        }
        val manifest = try {
            WorkshopManifest.parse(text)
        } catch (error: WorkshopManifestException) {
            throw WorkshopLoadException("draft manifest.json is invalid: ${error.message}")
        }
        val entry = File(draft, manifest.entry)
        val entryCanonical = entry.canonicalFile
        if (!entryCanonical.toPath().startsWith(draft.toPath()) || !entryCanonical.isFile) {
            val files = draft.listFiles()
                ?.sortedBy { file -> file.name }
                ?.joinToString(", ") { file -> file.name }
                .orEmpty()
            throw WorkshopLoadException(
                "draft entry is missing: ${manifest.entry} in ${draft.absolutePath}; " +
                    "existing files: [${files.ifEmpty { "none" }}]. " +
                    "Write ${manifest.entry} into that folder with an absolute path, then call workshop_load with the same draft path.",
            )
        }
        validateDraftContents(draft)
        if (!appsDir.exists() && !appsDir.mkdirs()) {
            throw WorkshopLoadException("cannot create workshop apps folder: ${appsDir.absolutePath}")
        }
        val target = File(appsDir, manifest.id)
        if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw WorkshopLoadException("an app with id '${manifest.id}' already exists")
        }
        try {
            try {
                Files.move(draft.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(draft.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: FileAlreadyExistsException) {
            throw WorkshopLoadException("an app with id '${manifest.id}' already exists")
        } catch (error: IOException) {
            throw WorkshopLoadException("cannot load app '${manifest.id}': ${error.message}")
        }
        if (!userdataDir.exists()) userdataDir.mkdirs()
        WorkshopCreation(manifest = manifest, directory = target)
    }

    suspend fun rename(id: String, name: String): WorkshopCreation? = withContext(Dispatchers.IO) {
        val directory = guardedAppDirectory(id) ?: return@withContext null
        if (!directory.isDirectory) return@withContext null
        val manifest = readManifest(directory) ?: return@withContext null
        val updated = manifest.withName(name)
        writeManifest(directory, updated)
        WorkshopCreation(manifest = updated, directory = directory)
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        val directory = guardedAppDirectory(id) ?: return@withContext false
        val removed = directory.isDirectory && directory.deleteRecursively()
        deleteDataFiles(id)
        removed
    }

    fun appDirectory(id: String): File = File(appsDir, id)

    fun dataFile(id: String): File = File(userdataDir, "$id.db")

    private fun ensureRoot() {
        listOf(rootDir, appsDir, buildDir, userdataDir).forEach { directory ->
            if (!directory.exists() && !directory.mkdirs()) {
                throw WorkshopManifestException("cannot create workshop directory: ${directory.absolutePath}")
            }
        }
    }

    private fun newManifest(
        id: String,
        name: String,
        description: String,
        icon: String?,
    ): WorkshopManifest {
        val resolvedIcon = icon?.trim().orEmpty()
        if (resolvedIcon.length > 8) {
            throw WorkshopManifestException("icon must be at most 8 characters")
        }
        val resolvedDescription = description.trim()
        if (resolvedDescription.length > 300) {
            throw WorkshopManifestException("description must be at most 300 characters")
        }
        return WorkshopManifest(
            id = id,
            name = WorkshopManifest.normalizedName(name),
            icon = resolvedIcon,
            version = WorkshopManifest.DEFAULT_VERSION,
            entry = WorkshopManifest.DEFAULT_ENTRY,
            description = resolvedDescription,
        )
    }

    private fun slug(raw: String): String {
        val builder = StringBuilder()
        raw.lowercase().forEach { character ->
            if (character in 'a'..'z' || character in '0'..'9') {
                builder.append(character)
            } else {
                builder.append('-')
            }
        }
        var slug = builder.toString().replace(Regex("-+"), "-").trim('-')
        if (slug.length > 40) slug = slug.take(40).trimEnd('-')
        if (slug.isEmpty()) return "app-" + clock.millis().toString(36)
        if (!WorkshopManifest.ID_REGEX.matches(slug)) return "app-" + clock.millis().toString(36)
        return slug
    }

    private fun uniqueId(base: String): String {
        val taken = takenIds()
        if (base !in taken) return base
        var index = 2
        while (true) {
            val candidate = "$base-$index"
            if (candidate.length <= 48 && candidate !in taken) return candidate
            index += 1
        }
    }

    private fun takenIds(): Set<String> {
        val ids = mutableSetOf<String>()
        appDirectories().forEach { directory -> ids.add(directory.name) }
        draftDirectories().forEach { directory ->
            val draftId = DRAFT_DIR_REGEX.matchEntire(directory.name)?.groupValues?.get(1) ?: directory.name
            ids.add(draftId)
        }
        return ids
    }

    private fun newDraftDirectory(id: String): File {
        while (true) {
            val suffix = (1..5).joinToString("") { ALPHABET.random().toString() }
            val directory = File(buildDir, "$id-$suffix")
            if (!directory.exists()) return directory
        }
    }

    private fun appDirectories(): List<File> = childDirectories(appsDir)

    private fun draftDirectories(): List<File> = childDirectories(buildDir)

    private fun childDirectories(parent: File): List<File> {
        if (!parent.isDirectory) return emptyList()
        return parent.listFiles()
            ?.filter { directory ->
                directory.isDirectory &&
                    !directory.name.startsWith(".") &&
                    !Files.isSymbolicLink(directory.toPath())
            }
            ?.sortedByDescending { directory -> directory.lastModified() }
            ?: emptyList()
    }

    private fun inspect(directory: File): Inspection {
        val manifestFile = File(directory, MANIFEST_FILE)
        if (!manifestFile.isFile) {
            return if (File(directory, WorkshopManifest.DEFAULT_ENTRY).isFile) {
                Inspection.Invalid(
                    WorkshopInvalid(
                        id = directory.name,
                        directory = directory,
                        reason = WorkshopInvalidReason.MANIFEST_MISSING,
                        detail = null,
                    ),
                )
            } else {
                Inspection.Ignored
            }
        }
        val text = try {
            manifestFile.readText()
        } catch (error: Exception) {
            return Inspection.Invalid(
                WorkshopInvalid(
                    id = directory.name,
                    directory = directory,
                    reason = WorkshopInvalidReason.MANIFEST_UNREADABLE,
                    detail = error.message,
                ),
            )
        }
        val manifest = try {
            WorkshopManifest.parse(text)
        } catch (error: WorkshopManifestException) {
            return Inspection.Invalid(
                WorkshopInvalid(
                    id = directory.name,
                    directory = directory,
                    reason = WorkshopInvalidReason.MANIFEST_INVALID,
                    detail = error.message,
                ),
            )
        }
        if (manifest.id != directory.name) {
            return Inspection.Invalid(
                WorkshopInvalid(
                    id = directory.name,
                    directory = directory,
                    reason = WorkshopInvalidReason.ID_MISMATCH,
                    detail = "manifest id is ${manifest.id}",
                ),
            )
        }
        val entry = File(directory, manifest.entry).canonicalFile
        if (!entry.toPath().startsWith(directory.canonicalFile.toPath()) || !entry.isFile) {
            return Inspection.Invalid(
                WorkshopInvalid(
                    id = directory.name,
                    directory = directory,
                    reason = WorkshopInvalidReason.ENTRY_MISSING,
                    detail = manifest.entry,
                ),
            )
        }
        return Inspection.Valid(WorkshopCreation(manifest, directory))
    }

    private fun readManifest(directory: File): WorkshopManifest? = try {
        val file = File(directory, MANIFEST_FILE)
        if (!file.isFile) null else WorkshopManifest.parse(file.readText())
    } catch (_: Exception) {
        null
    }

    private fun guardedAppDirectory(id: String): File? {
        if (id.isEmpty() || id.contains('/') || id.contains('\\') || id == "." || id == "..") return null
        val directory = File(appsDir, id).canonicalFile
        val appsRoot = appsDir.canonicalFile
        if (directory.parentFile != appsRoot) return null
        return directory
    }

    private fun deleteDataFiles(id: String) {
        if (!WorkshopManifest.ID_REGEX.matches(id)) return
        listOf("$id.db", "$id.db-wal", "$id.db-shm", "$id.db-journal").forEach { name ->
            val file = File(userdataDir, name)
            if (file.isFile) file.delete()
        }
    }

    private fun writeManifest(directory: File, manifest: WorkshopManifest) {
        writeTextAtomic(File(directory, MANIFEST_FILE), manifest.toJson())
    }

    private fun writeTextAtomic(file: File, text: String) {
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw WorkshopManifestException("cannot create folder: ${parent.absolutePath}")
        }
        val temp = File(parent, file.name + ".tmp")
        temp.writeText(text)
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun validateDraftContents(draft: File) {
        var fileCount = 0
        var totalBytes = 0L
        Files.walkFileTree(
            draft.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isSymbolicLink) {
                        throw WorkshopLoadException("draft contains a symbolic link: ${dir.fileName}")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isSymbolicLink) {
                        throw WorkshopLoadException("draft contains a symbolic link: ${file.fileName}")
                    }
                    fileCount += 1
                    totalBytes += attrs.size()
                    if (fileCount > MAX_FILES) {
                        throw WorkshopLoadException("draft has more than $MAX_FILES files")
                    }
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw WorkshopLoadException("draft is larger than ${MAX_TOTAL_BYTES / (1024 * 1024)} MB")
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                    throw WorkshopLoadException("cannot read draft file ${file.fileName}: ${error.message}")
                }
            },
        )
    }

    private sealed interface Inspection {
        data class Valid(val creation: WorkshopCreation) : Inspection

        data class Invalid(val invalid: WorkshopInvalid) : Inspection

        data object Ignored : Inspection
    }

    private companion object {
        const val APPS_DIR = "apps"
        const val BUILD_DIR = "build"
        const val USERDATA_DIR = "userdata"
        const val MANIFEST_FILE = "manifest.json"
        const val MAX_FILES = 200
        const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
        const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        val DRAFT_DIR_REGEX = Regex("(.+)-([a-z0-9]{5})")
    }
}
