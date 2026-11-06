package io.github.whywood.deepdeep.data

import android.content.res.AssetManager
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ASSET_ROOT = "skills"
private const val SKILL_FILE = "SKILL.md"
private const val PLUGIN_MARKER = ".deepdeep-plugin"
private const val BUNDLED_MARKER = ".deepdeep-bundled"

class BundledSkills(
    private val repoDir: File,
    private val activeDir: File,
    private val list: (String) -> List<String>?,
    private val read: (String) -> ByteArray?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun sync(): Int = withContext(ioDispatcher) {
        val failures = mutableListOf<Throwable>()
        val synced = readBundledSkills().entries.count { (id, files) ->
            try {
                syncSkill(id, files)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failures += error
                false
            }
        }
        if (failures.isNotEmpty()) {
            throw IOException("bundled skill sync failed for ${failures.size} skill(s)", failures.first())
        }
        synced
    }

    private fun readBundledSkills(): Map<String, Map<String, ByteArray>> = buildMap {
        list(ASSET_ROOT).orEmpty().forEach { id ->
            val files = LinkedHashMap<String, ByteArray>()
            collect("$ASSET_ROOT/$id", "", files)
            if (files.containsKey(SKILL_FILE)) put(id, files)
        }
    }

    private fun collect(path: String, relative: String, files: MutableMap<String, ByteArray>) {
        val children = list(path)
        if (children.isNullOrEmpty()) {
            if (relative.isEmpty()) return
            read(path)?.let { files[relative] = it }
            return
        }
        children.forEach { child ->
            collect("$path/$child", if (relative.isEmpty()) child else "$relative/$child", files)
        }
    }

    private fun syncSkill(id: String, files: Map<String, ByteArray>): Boolean {
        if (!isSafeId(id)) return false
        val repo = File(repoDir, id)
        val active = File(activeDir, id)
        return when (ownership(repo)) {
            Ownership.FOREIGN -> false
            Ownership.MISSING -> {
                publish(repo, id, files)
                if (ownership(active) == Ownership.MISSING) publish(active, id, files)
                true
            }

            Ownership.BUNDLED -> {
                publish(repo, id, files)
                if (ownership(active) == Ownership.BUNDLED) publish(active, id, files)
                true
            }
        }
    }

    private fun ownership(directory: File): Ownership = when {
        !directory.isDirectory -> Ownership.MISSING
        File(directory, PLUGIN_MARKER).isFile -> Ownership.FOREIGN
        File(directory, BUNDLED_MARKER).isFile -> Ownership.BUNDLED
        else -> Ownership.FOREIGN
    }

    private fun publish(target: File, id: String, files: Map<String, ByteArray>) {
        if (contentMatches(target, files)) return
        val parent = target.parentFile ?: return
        parent.mkdirs()
        val temp = File(parent, ".tmp-${target.name}-${UUID.randomUUID()}")
        try {
            temp.mkdirs()
            files.forEach { (path, bytes) ->
                val destination = File(temp, path)
                destination.parentFile?.mkdirs()
                destination.writeBytes(bytes)
            }
            File(temp, BUNDLED_MARKER).writeText(id, Charsets.UTF_8)
            target.deleteRecursively()
            if (!temp.renameTo(target)) {
                temp.copyRecursively(target, overwrite = true)
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun contentMatches(target: File, files: Map<String, ByteArray>): Boolean {
        if (!target.isDirectory) return false
        val present = target.walkTopDown()
            .filter { it.isFile && it.name != BUNDLED_MARKER && it.name != PLUGIN_MARKER }
            .map { it.relativeTo(target).path.replace('\\', '/') }
            .toSet()
        if (present != files.keys) return false
        return files.all { (path, bytes) ->
            runCatching { File(target, path).readBytes().contentEquals(bytes) }.getOrDefault(false)
        }
    }

    private fun isSafeId(id: String): Boolean {
        if (id.isEmpty() || id.length > 64 || id == "." || id == "..") return false
        return id.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    private enum class Ownership {
        MISSING,
        BUNDLED,
        FOREIGN,
    }

    companion object {
        fun fromAssets(assets: AssetManager, repoDir: File, activeDir: File): BundledSkills = BundledSkills(
            repoDir = repoDir,
            activeDir = activeDir,
            list = { path -> runCatching { assets.list(path)?.toList() }.getOrNull() },
            read = { path -> runCatching { assets.open(path).use { it.readBytes() } }.getOrNull() },
        )
    }
}
