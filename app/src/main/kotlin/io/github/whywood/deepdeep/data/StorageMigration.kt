package io.github.whywood.deepdeep.data

import java.io.File
import org.json.JSONObject

object StorageMigration {
    private const val LEGACY_WORKSPACE = "workspace"
    private const val HOME = "home"
    private const val LEGACY_PROJECTS = "projects"
    private const val WORKSPACES = "workspaces"
    private const val MARKER_FILE = ".storage-migrated"
    private const val MIGRATION_VERSION = "1"

    fun migrate(filesDir: File) {
        val marker = File(filesDir, MARKER_FILE)
        if (runCatching { marker.readText() }.getOrNull()?.trim() == MIGRATION_VERSION) return
        val legacyWorkspace = File(filesDir, LEGACY_WORKSPACE)
        val home = File(filesDir, HOME)
        if (legacyWorkspace.exists() && (!home.exists() || home.listFiles().isNullOrEmpty())) {
            if (home.exists()) home.deleteRecursively()
            legacyWorkspace.renameTo(home)
        }
        home.mkdirs()

        val legacyProjects = File(filesDir, LEGACY_PROJECTS)
        val workspaces = File(filesDir, WORKSPACES)
        if (legacyProjects.exists() && (!workspaces.exists() || workspaces.listFiles().isNullOrEmpty())) {
            if (workspaces.exists()) workspaces.deleteRecursively()
            legacyProjects.renameTo(workspaces)
        }
        workspaces.mkdirs()

        migrateSessions(File(filesDir, "agent/sessions"))
        runCatching {
            marker.parentFile?.mkdirs()
            marker.writeText(MIGRATION_VERSION)
        }
    }

    private fun migrateSessions(sessionRoot: File) {
        if (!sessionRoot.isDirectory) return
        val directories = sessionRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (directory in directories) {
            val files = directory.listFiles()?.filter { it.isFile && it.name.endsWith(".jsonl") }.orEmpty()
            for (file in files) {
                val lines = runCatching { file.readLines() }.getOrNull() ?: continue
                if (lines.isEmpty()) continue
                val header = runCatching { JSONObject(lines.first()) }.getOrNull() ?: continue
                val cwd = header.optString("cwd")
                if (cwd.isBlank()) continue
                val migratedCwd = StorageCwdMigration.migrate(cwd)
                if (migratedCwd != cwd) {
                    header.put("cwd", migratedCwd)
                    val content = buildString {
                        append(header.toString())
                        append('\n')
                        lines.drop(1).forEach { line ->
                            append(line)
                            append('\n')
                        }
                    }
                    runCatching { file.writeText(content) }
                }
                val expectedDirectory = File(sessionRoot, encodeCwdName(migratedCwd))
                if (expectedDirectory != file.parentFile) {
                    expectedDirectory.mkdirs()
                    runCatching { file.renameTo(File(expectedDirectory, file.name)) }
                }
            }
        }
        sessionRoot.listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.isEmpty() == true }
            ?.forEach { it.delete() }
    }

    private fun encodeCwdName(cwd: String): String {
        val resolved = File(cwd).absoluteFile.normalize().path
        val withoutLeadingSeparator = resolved.removePrefix("/").removePrefix("\\")
        val encoded = buildString {
            for (char in withoutLeadingSeparator) {
                append(if (char == '/' || char == '\\' || char == ':') '-' else char)
            }
        }
        return "--$encoded--"
    }
}

internal object StorageCwdMigration {
    private val legacyWorkspace = Regex("(/files/)workspace(?=/|$)")
    private val legacyProjects = Regex("(/files/)projects(?=/|$)")
    private val brokenWorkspaces = Regex("(/files/)homes(?=/|$)")

    fun migrate(cwd: String): String = cwd
        .replace(legacyWorkspace, "$1home")
        .replace(legacyProjects, "$1workspaces")
        .replace(brokenWorkspaces, "$1workspaces")
}
