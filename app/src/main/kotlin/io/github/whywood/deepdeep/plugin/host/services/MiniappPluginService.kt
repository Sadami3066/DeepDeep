package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.MiniappApp
import io.github.whywood.deepdeep.plugin.api.MiniappCreateRequest
import io.github.whywood.deepdeep.plugin.api.MiniappEntry
import io.github.whywood.deepdeep.plugin.api.MiniappInvalid
import io.github.whywood.deepdeep.plugin.api.MiniappRun
import io.github.whywood.deepdeep.plugin.api.MiniappScan
import io.github.whywood.deepdeep.plugin.api.MiniappService
import io.github.whywood.deepdeep.runtime.CreatedDraft
import io.github.whywood.deepdeep.runtime.MiniappAgentRequest
import io.github.whywood.deepdeep.runtime.MiniappAgentService
import io.github.whywood.deepdeep.runtime.MiniappEntry as RuntimeMiniappEntry
import io.github.whywood.deepdeep.runtime.MiniappInvalid as RuntimeMiniappInvalid
import io.github.whywood.deepdeep.runtime.MiniappManifest
import io.github.whywood.deepdeep.runtime.MiniappStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class MiniappPluginService(
    private val store: MiniappStore,
    private val agent: MiniappAgentService?,
    private val scope: CoroutineScope,
    private val maxTurns: () -> Int,
) : MiniappService {
    override suspend fun scan(): MiniappScan {
        val scan = store.scan()
        return MiniappScan(
            apps = scan.creations.map { it.manifest.toPluginApp(it.directory) },
            invalid = scan.invalid.map { it.toPluginInvalid() },
        )
    }

    override suspend fun load(id: String): MiniappEntry = when (val entry = store.load(id)) {
        is RuntimeMiniappEntry.Valid -> MiniappEntry.Valid(
            app = entry.creation.manifest.toPluginApp(entry.creation.directory),
            manifest = entry.creation.manifest.toJsonObject(),
        )

        is RuntimeMiniappEntry.Invalid -> MiniappEntry.Invalid(
            id = entry.id,
            reason = entry.reason.name + (entry.detail?.let { ": $it" } ?: ""),
        )

        RuntimeMiniappEntry.Missing -> MiniappEntry.Missing
    }

    override suspend fun create(request: MiniappCreateRequest): MiniappApp? {
        val draft = store.createDraft(name = request.name, description = request.description)
        if (agent == null) return draft.toPluginApp()
        launchAgent(
            buildString {
                appendLine("创建一个小程序：${draft.manifest.name}")
                request.description.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                request.prompt.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                append(
                    "把应用文件写到绝对路径 ${draft.directory.absolutePath}（入口默认 index.html），" +
                        "完成后调用 miniapp_load，path 传该绝对路径。",
                )
            },
        )
        return draft.toPluginApp()
    }

    override suspend fun edit(id: String, instruction: String): MiniappRun {
        val creation = (store.load(id) as? RuntimeMiniappEntry.Valid)?.creation
            ?: return MiniappRun(appId = id, accepted = false, error = "miniapp not found: $id")
        if (agent == null) return MiniappRun(appId = id, accepted = false, error = MINIAPP_AGENT_UNAVAILABLE)
        launchAgent(
            "修改已安装的小程序 ${creation.manifest.id}（目录 ${creation.directory.absolutePath}）：$instruction",
        )
        return MiniappRun(appId = creation.manifest.id, accepted = true)
    }

    override suspend fun delete(id: String): Boolean = store.delete(id)

    override suspend fun run(id: String, instruction: String): MiniappRun {
        val creation = (store.load(id) as? RuntimeMiniappEntry.Valid)?.creation
            ?: return MiniappRun(appId = id, accepted = false, error = "miniapp not found: $id")
        if (agent == null) return MiniappRun(appId = id, accepted = false, error = MINIAPP_AGENT_UNAVAILABLE)
        launchAgent(
            "在小程序 ${creation.manifest.id}（目录 ${creation.directory.absolutePath}）中执行：$instruction",
        )
        return MiniappRun(appId = creation.manifest.id, accepted = true)
    }

    private fun launchAgent(prompt: String) {
        val runner = agent ?: return
        scope.launch {
            runCatching {
                runner.run(MiniappAgentRequest(prompt = prompt, maxTurns = maxTurns()), onEvent = {})
            }
        }
    }

    private fun CreatedDraft.toPluginApp(): MiniappApp = MiniappApp(
        id = manifest.id,
        name = manifest.name,
        description = manifest.description.takeIf { it.isNotBlank() },
        updatedAt = directory.lastModified(),
    )

    private fun MiniappManifest.toPluginApp(directory: File): MiniappApp = MiniappApp(
        id = id,
        name = name,
        description = description.takeIf { it.isNotBlank() },
        updatedAt = directory.lastModified(),
    )

    private fun MiniappManifest.toJsonObject(): JsonObject =
        Json.parseToJsonElement(toJson()).jsonObject

    private fun RuntimeMiniappInvalid.toPluginInvalid(): MiniappInvalid = MiniappInvalid(
        id = id,
        reason = reason.name + (detail?.let { ": $it" } ?: ""),
    )

    private companion object {
        const val MINIAPP_AGENT_UNAVAILABLE = "miniapp agent is not available to plugins"
    }
}
