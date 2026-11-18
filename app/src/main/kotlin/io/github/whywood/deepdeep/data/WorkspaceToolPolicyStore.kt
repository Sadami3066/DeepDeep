package io.github.whywood.deepdeep.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WorkspaceToolPolicy(
    val allCapabilities: Boolean = false,
    val skills: Set<String> = emptySet(),
    val plugins: Set<String> = emptySet(),
    val mcpServers: Set<String> = emptySet(),
    val screenTools: Boolean = false,
    val miniappTools: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !allCapabilities && skills.isEmpty() && plugins.isEmpty() &&
            mcpServers.isEmpty() && !screenTools && !miniappTools

    fun enabledSkills(installed: Set<String>): Set<String> = resolve(installed, skills)

    fun enabledPlugins(installed: Set<String>): Set<String> = resolve(installed, plugins)

    fun enabledMcpServers(installed: Set<String>): Set<String> = resolve(installed, mcpServers)

    private fun resolve(installed: Set<String>, selection: Set<String>): Set<String> =
        if (allCapabilities) installed - selection else installed.intersect(selection)
}

class WorkspaceToolPolicyStore(private val file: File) {
    private val mutex = Mutex()

    @Volatile
    private var cache: Map<String, WorkspaceToolPolicy> = readFromDisk()

    fun isConfigured(cwd: String): Boolean = cwd.isNotBlank() && cache.containsKey(key(cwd))

    fun policyForCwd(cwd: String): WorkspaceToolPolicy {
        if (cwd.isBlank()) return WorkspaceToolPolicy()
        return cache[key(cwd)] ?: WorkspaceToolPolicy()
    }

    fun defaultAllPolicy(): WorkspaceToolPolicy = WorkspaceToolPolicy(allCapabilities = true)

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
                            allCapabilities = item.optBoolean("allCapabilities", false),
                            skills = item.stringSet("skills"),
                            plugins = item.stringSet("plugins"),
                            mcpServers = item.stringSet("mcpServers"),
                            screenTools = item.optBoolean("screenTools", false),
                            miniappTools = item.optBoolean("miniappTools", false),
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
                    .put("allCapabilities", policy.allCapabilities)
                    .put("skills", org.json.JSONArray(policy.skills.toList()))
                    .put("plugins", org.json.JSONArray(policy.plugins.toList()))
                    .put("mcpServers", org.json.JSONArray(policy.mcpServers.toList()))
                    .put("screenTools", policy.screenTools)
                    .put("miniappTools", policy.miniappTools),
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
