package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WorkspaceToolPolicy(
    val skills: Set<String> = emptySet(),
    val plugins: Set<String> = emptySet(),
    val mcpServers: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = skills.isEmpty() && plugins.isEmpty() && mcpServers.isEmpty()
}

class WorkspaceToolPolicyStore(private val file: File) {
    private val mutex = Mutex()

    @Volatile
    private var cache: Map<String, WorkspaceToolPolicy> = readFromDisk()

    fun policyForCwd(cwd: String): WorkspaceToolPolicy {
        if (cwd.isBlank()) return WorkspaceToolPolicy()
        return cache[key(cwd)] ?: WorkspaceToolPolicy()
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        mutex.withLock { cache = readFromDisk() }
    }

    suspend fun update(cwd: String, policy: WorkspaceToolPolicy) = withContext(Dispatchers.IO) {
        if (cwd.isBlank()) return@withContext
        mutex.withLock {
            val next = if (policy.isEmpty) cache - key(cwd) else cache + (key(cwd) to policy)
            writeToDisk(next)
            cache = next
        }
    }

    private fun key(cwd: String): String =
        runCatching { File(cwd).canonicalPath }.getOrDefault(File(cwd).absolutePath)

    private fun readFromDisk(): Map<String, WorkspaceToolPolicy> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(file.readText())
            buildMap {
                root.keys().forEach { cwd ->
                    val item = root.optJSONObject(cwd) ?: return@forEach
                    put(
                            cwd,
                            WorkspaceToolPolicy(
                                skills = item.stringSet("skills"),
                                plugins = item.stringSet("plugins"),
                                mcpServers = item.stringSet("mcpServers"),
                            ),
                        )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeToDisk(policies: Map<String, WorkspaceToolPolicy>) {
        val root = JSONObject()
        policies.forEach { (cwd, policy) ->
            root.put(
                cwd,
                JSONObject()
                    .put("skills", org.json.JSONArray(policy.skills.toList()))
                    .put("plugins", org.json.JSONArray(policy.plugins.toList()))
                    .put("mcpServers", org.json.JSONArray(policy.mcpServers.toList())),
            )
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(root.toString())
        if (!temp.renameTo(file)) {
            file.writeText(root.toString())
            temp.delete()
        }
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val array = optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}
