package io.github.whywood.deepdeep.data

import java.io.File
import org.json.JSONObject

object StorageMigration {
    private const val LEGACY_WORKSPACE = "workspace"
    private const val HOME = "home"
    private const val LEGACY_PROJECTS = "projects"
    private const val WORKSPACES = "workspaces"

    fun migrate(filesDir: File) {
        val legacyWorkspace = File(filesDir, LEGACY_WORKSPACE)
        val home = File(filesDir, HOME)
        if (!home.exists() && legacyWorkspace.exists()) {
            legacyWorkspace.renameTo(home)
        }
        home.mkdirs()

        val legacyProjects = File(filesDir, LEGACY_PROJECTS)
        val workspaces = File(filesDir, WORKSPACES)
        if (!workspaces.exists() && legacyProjects.exists()) {
            legacyProjects.renameTo(workspaces)
        }
        workspaces.mkdirs()

        val rewrites = listOf(
            "/files/$LEGACY_WORKSPACE" to "/files/$HOME",
            "/files/$LEGACY_PROJECTS/" to "/files/$WORKSPACES/",
        )
        migrateSessions(File(filesDir, "agent/sessions"), rewrites)
    }

    private fun migrateSessions(sessionRoot: File, rewrites: List<Pair<String, String>>) {
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
                val migratedCwd = rewrites.fold(cwd) { value, (from, to) -> value.replace(from, to) }
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
