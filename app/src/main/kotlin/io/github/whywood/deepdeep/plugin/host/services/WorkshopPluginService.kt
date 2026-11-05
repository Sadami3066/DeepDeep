package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.WorkshopApp
import io.github.whywood.deepdeep.plugin.api.WorkshopCreateRequest
import io.github.whywood.deepdeep.plugin.api.WorkshopEntry
import io.github.whywood.deepdeep.plugin.api.WorkshopInvalid
import io.github.whywood.deepdeep.plugin.api.WorkshopRun
import io.github.whywood.deepdeep.plugin.api.WorkshopScan
import io.github.whywood.deepdeep.plugin.api.WorkshopService
import io.github.whywood.deepdeep.runtime.CreatedDraft
import io.github.whywood.deepdeep.runtime.WorkshopAgentRequest
import io.github.whywood.deepdeep.runtime.WorkshopAgentService
import io.github.whywood.deepdeep.runtime.WorkshopEntry as RuntimeWorkshopEntry
import io.github.whywood.deepdeep.runtime.WorkshopInvalid as RuntimeWorkshopInvalid
import io.github.whywood.deepdeep.runtime.WorkshopManifest
import io.github.whywood.deepdeep.runtime.WorkshopStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class WorkshopPluginService(
    private val store: WorkshopStore,
    private val agent: WorkshopAgentService?,
    private val scope: CoroutineScope,
    private val maxTurns: () -> Int,
) : WorkshopService {
    override suspend fun scan(): WorkshopScan {
        val scan = store.scan()
        return WorkshopScan(
            apps = scan.creations.map { it.manifest.toPluginApp(it.directory) },
            invalid = scan.invalid.map { it.toPluginInvalid() },
        )
    }

    override suspend fun load(id: String): WorkshopEntry = when (val entry = store.load(id)) {
        is RuntimeWorkshopEntry.Valid -> WorkshopEntry.Valid(
            app = entry.creation.manifest.toPluginApp(entry.creation.directory),
            manifest = entry.creation.manifest.toJsonObject(),
        )

        is RuntimeWorkshopEntry.Invalid -> WorkshopEntry.Invalid(
            id = entry.id,
            reason = entry.reason.name + (entry.detail?.let { ": $it" } ?: ""),
        )

        RuntimeWorkshopEntry.Missing -> WorkshopEntry.Missing
    }

    override suspend fun create(request: WorkshopCreateRequest): WorkshopApp? {
        val draft = store.createDraft(name = request.name, description = request.description)
        if (agent == null) return draft.toPluginApp()
        launchAgent(
            buildString {
                appendLine("创建一个 Workshop 迷你应用：${draft.manifest.name}")
                request.description.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                request.prompt.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                append(
                    "把应用文件写到绝对路径 ${draft.directory.absolutePath}（入口默认 index.html），" +
                        "完成后调用 workshop_load，path 传该绝对路径。",
                )
            },
        )
        return draft.toPluginApp()
    }

    override suspend fun edit(id: String, instruction: String): WorkshopRun {
        val creation = (store.load(id) as? RuntimeWorkshopEntry.Valid)?.creation
            ?: return WorkshopRun(appId = id, accepted = false, error = "workshop app not found: $id")
        if (agent == null) return WorkshopRun(appId = id, accepted = false, error = WORKSHOP_AGENT_UNAVAILABLE)
        launchAgent(
            "修改已安装的 Workshop 应用 ${creation.manifest.id}（目录 ${creation.directory.absolutePath}）：$instruction",
        )
        return WorkshopRun(appId = creation.manifest.id, accepted = true)
    }

    override suspend fun delete(id: String): Boolean = store.delete(id)

    override suspend fun run(id: String, instruction: String): WorkshopRun {
        val creation = (store.load(id) as? RuntimeWorkshopEntry.Valid)?.creation
            ?: return WorkshopRun(appId = id, accepted = false, error = "workshop app not found: $id")
        if (agent == null) return WorkshopRun(appId = id, accepted = false, error = WORKSHOP_AGENT_UNAVAILABLE)
        launchAgent(
            "在 Workshop 应用 ${creation.manifest.id}（目录 ${creation.directory.absolutePath}）中执行：$instruction",
        )
        return WorkshopRun(appId = creation.manifest.id, accepted = true)
    }

    private fun launchAgent(prompt: String) {
        val runner = agent ?: return
        scope.launch {
            runCatching {
                runner.run(WorkshopAgentRequest(prompt = prompt, maxTurns = maxTurns()), onEvent = {})
            }
        }
    }

    private fun CreatedDraft.toPluginApp(): WorkshopApp = WorkshopApp(
        id = manifest.id,
        name = manifest.name,
        description = manifest.description.takeIf { it.isNotBlank() },
        updatedAt = directory.lastModified(),
    )

    private fun WorkshopManifest.toPluginApp(directory: File): WorkshopApp = WorkshopApp(
        id = id,
        name = name,
        description = description.takeIf { it.isNotBlank() },
        updatedAt = directory.lastModified(),
    )

    private fun WorkshopManifest.toJsonObject(): JsonObject =
        Json.parseToJsonElement(toJson()).jsonObject

    private fun RuntimeWorkshopInvalid.toPluginInvalid(): WorkshopInvalid = WorkshopInvalid(
        id = id,
        reason = reason.name + (detail?.let { ": $it" } ?: ""),
    )

    private companion object {
        const val WORKSHOP_AGENT_UNAVAILABLE = "workshop agent is not available to plugins"
    }
}
