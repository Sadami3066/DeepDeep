package io.github.whywood.deepdeep.purge

import android.content.res.AssetManager
import java.io.File

private const val ASSET_ROOT = "purge/skills"
private const val SKILL_FILE = "SKILL.md"
private const val PURGE_MARKER = ".deepdeep-purge"
private const val SKILLS_DIR = "skills"

class PurgeSkillLibrary(private val assets: AssetManager) {
    fun catalog(agentDir: File? = null): List<PurgeSkillInfo> {
        val installed = agentDir?.let { installedIds(it) }.orEmpty()
        return assetIds().mapNotNull { id ->
            val text = readAsset("$ASSET_ROOT/$id/$SKILL_FILE") ?: return@mapNotNull null
            val meta = parseFrontMatter(text)
            PurgeSkillInfo(
                id = id,
                name = meta["name"]?.takeIf { it.isNotBlank() } ?: id,
                description = meta["description"].orEmpty(),
                role = meta["role"].orEmpty(),
                installed = id in installed,
            )
        }.sortedWith(compareBy({ roleOrder(it.role) }, { it.id }))
    }

    fun promptInject(): String? = readAsset("purge/prompt-inject.md")

    fun content(id: String): String? {
        if (!isSafeId(id)) return null
        return readAsset("$ASSET_ROOT/$id/$SKILL_FILE")
    }

    fun install(agentDir: File): Int {
        val root = File(agentDir, SKILLS_DIR)
        root.mkdirs()
        var written = 0
        assetIds().forEach { id ->
            if (!isSafeId(id)) return@forEach
            val body = readAsset("$ASSET_ROOT/$id/$SKILL_FILE") ?: return@forEach
            val target = File(root, id)
            if (!target.exists()) target.mkdirs()
            val file = File(target, SKILL_FILE)
            if (!file.isFile || file.readText(Charsets.UTF_8) != body) {
                file.writeText(body, Charsets.UTF_8)
            }
            File(target, PURGE_MARKER).writeText(id, Charsets.UTF_8)
            written += 1
        }
        return written
    }

    fun remove(agentDir: File): Int {
        val root = File(agentDir, SKILLS_DIR)
        var removed = 0
        root.listFiles()?.forEach { directory ->
            if (!directory.isDirectory) return@forEach
            if (!File(directory, PURGE_MARKER).isFile) return@forEach
            if (directory.deleteRecursively()) removed += 1
        }
        return removed
    }

    fun installedIds(agentDir: File): Set<String> {
        val root = File(agentDir, SKILLS_DIR)
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, PURGE_MARKER).isFile && File(it, SKILL_FILE).isFile }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
    }

    private fun assetIds(): List<String> =
        runCatching { assets.list(ASSET_ROOT)?.filter { isSafeId(it) }?.sorted().orEmpty() }
            .getOrDefault(emptyList())

    fun readAsset(path: String): String? = runCatching {
        assets.open(path).use { String(it.readBytes(), Charsets.UTF_8) }
    }.getOrNull()

    private fun isSafeId(id: String): Boolean {
        if (id.isEmpty() || id.length > 64 || id == "." || id == "..") return false
        return id.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    companion object {
        fun parseFrontMatter(text: String): Map<String, String> {
            val lines = text.lineSequence().toList()
            if (lines.firstOrNull()?.trim() != "---") return emptyMap()
            val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (end < 0) return emptyMap()
            val result = LinkedHashMap<String, String>()
            lines.subList(1, end + 1).forEach { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) return@forEach
                val key = line.substring(0, separator).trim()
                val value = line.substring(separator + 1).trim().trim('"', '\'')
                if (key.isNotEmpty()) result[key] = value
            }
            return result
        }

        private fun roleOrder(role: String): Int = when (role.lowercase()) {
            "recon" -> 0
            "vuln-scan" -> 1
            "exploit" -> 2
            "internal" -> 3
            "plan" -> 4
            else -> 5
        }
    }
}
