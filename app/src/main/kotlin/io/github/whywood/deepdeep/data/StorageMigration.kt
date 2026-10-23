package io.github.whywood.deepdeep.data

import java.io.File
import org.json.JSONObject

object StorageMigration {
    fun migrate(filesDir: File) {
        val workspace = File(filesDir, "workspace")
        val home = File(filesDir, "home")
        if (!home.exists() && workspace.exists()) {
            workspace.renameTo(home)
        }
        home.mkdirs()

        val sessions = File(filesDir, "agent/sessions")
        if (!sessions.isDirectory) return
        val oldDir = File(sessions, encodeCwdName(workspace.absolutePath))
        val newDir = File(sessions, encodeCwdName(home.absolutePath))
        if (!newDir.exists() && oldDir.exists()) {
            oldDir.renameTo(newDir)
        }
        val target = if (newDir.isDirectory) newDir else return
        target.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jsonl") }
            ?.forEach { file -> rewriteCwd(file, workspace.absolutePath, home.absolutePath) }
    }

    private fun rewriteCwd(file: File, oldPath: String, newPath: String) {
        val lines = runCatching { file.readLines() }.getOrNull() ?: return
        if (lines.isEmpty()) return
        val header = runCatching { JSONObject(lines.first()) }.getOrNull() ?: return
        if (header.optString("cwd") != oldPath) return
        header.put("cwd", newPath)
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
