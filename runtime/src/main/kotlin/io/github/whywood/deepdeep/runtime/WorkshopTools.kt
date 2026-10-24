package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.agent.AgentToolResult
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val WORKSHOP_TYPES = buildJsonArray {
    WorkshopType.entries.forEach { type -> add(JsonPrimitive(type.wire)) }
}

private val workshopCreateParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("name") {
            put("type", "string")
            put("description", "Display name of the app, 1-40 characters")
        }
        putJsonObject("type") {
            put("type", "string")
            put("enum", WORKSHOP_TYPES)
            put("description", "App kind: board (kanban/vault), workstation (Agent reacts to input) or workflow (Agent runs automatically)")
        }
        putJsonObject("description") {
            put("type", "string")
            put("description", "Short description shown in the app list, at most 300 characters")
        }
        putJsonObject("icon") {
            put("type", "string")
            put("description", "Optional emoji icon, at most 8 characters")
        }
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("name"))
        add(JsonPrimitive("type"))
    })
}

private val workshopRegisterParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Absolute path of the draft folder returned by workshop_create")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("path")) })
}

private val workshopListParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

fun workshopTools(store: WorkshopStore): List<CodingTool> = listOf(
    workshopCreateTool(store),
    workshopRegisterTool(store),
    workshopListTool(store),
)

private fun workshopCreateTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_create",
    description = "Create a Workshop app draft: a self-contained HTML/CSS/JS mini app the user can open on the phone. Returns the draft folder where you write the app files; call workshop_register afterwards to install it.",
    parameters = workshopCreateParameters,
    label = "workshop_create",
    promptSnippet = "Create a Workshop app draft folder",
    promptGuidelines = listOf(
        "Use workshop_create when the user wants an interactive mini app such as a kanban board, a workstation driven by an Agent, or an automated workflow. Do not use it for static reports or documents; present those instead.",
        "Read the AGENTS.md file inside the returned draft folder before writing any app file.",
        "The workshop folder is outside the bash workspace: write HTML/CSS/JS files with absolute paths under the draft folder using read/write/edit, never with bash.",
        "Call workshop_register with the draft path when the app is ready, then fix every error it reports.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val name = args.requiredWorkshopString("name")
            val type = WorkshopType.fromWire(args.workshopString("type"))
                ?: throw IllegalArgumentException("type must be board, workstation or workflow")
            val draft = store.createDraft(
                name = name,
                type = type,
                description = args.workshopString("description").orEmpty(),
                icon = args.workshopString("icon"),
            )
            val path = draft.directory.absolutePath
            val result = buildJsonObject {
                put("ok", true)
                putJsonObject("draft") {
                    put("id", draft.manifest.id)
                    put("name", draft.manifest.name)
                    put("type", draft.manifest.type.wire)
                    put("path", path)
                }
                put("next", "Read $path/AGENTS.md first. Write HTML/CSS/JS files with absolute paths under $path using read/write/edit, then call workshop_register with path=$path.")
            }
            textResult(result.toString())
        }
    },
)

private fun workshopRegisterTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_register",
    description = "Install a Workshop draft folder as an app so the user can open it. Call it with the draft path returned by workshop_create after writing all app files.",
    parameters = workshopRegisterParameters,
    label = "workshop_register",
    promptSnippet = "Install a Workshop draft as an app",
    promptGuidelines = listOf(
        "Call workshop_register with the exact absolute draft path returned by workshop_create.",
        "Fix the reported error and retry until registration succeeds; the app is not visible to the user before that.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val path = args.requiredWorkshopString("path")
            val creation = store.install(path)
            val result = buildJsonObject {
                put("ok", true)
                putJsonObject("app") {
                    put("id", creation.manifest.id)
                    put("name", creation.manifest.name)
                    put("type", creation.manifest.type.wire)
                    put("icon", creation.manifest.icon)
                    put("version", creation.manifest.version)
                }
                put("path", creation.directory.absolutePath)
            }
            textResult(result.toString())
        }
    },
)

private fun workshopListTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_list",
    description = "List installed Workshop apps and the current drafts with their absolute folder paths.",
    parameters = workshopListParameters,
    label = "workshop_list",
    promptSnippet = "List Workshop apps and drafts",
    promptGuidelines = listOf(
        "Use workshop_list to find an existing app when the user asks to change one; edit that app folder in place with absolute paths.",
        "Drafts are unfinished apps in the build folder; installed apps are under the apps folder.",
    ),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val scan = store.scan()
            val drafts = store.drafts()
            val result = buildJsonObject {
                putJsonArray("apps") {
                    scan.creations.forEach { creation ->
                        add(
                            buildJsonObject {
                                put("id", creation.manifest.id)
                                put("name", creation.manifest.name)
                                put("type", creation.manifest.type.wire)
                                put("icon", creation.manifest.icon)
                                put("version", creation.manifest.version)
                                put("path", creation.directory.absolutePath)
                                put("description", creation.manifest.description)
                                putJsonArray("permissions") {
                                    creation.manifest.permissions.sorted().forEach { permission ->
                                        add(JsonPrimitive(permission))
                                    }
                                }
                            },
                        )
                    }
                }
                putJsonArray("drafts") {
                    drafts.forEach { draft ->
                        add(
                            buildJsonObject {
                                put("path", draft.directory.absolutePath)
                                draft.manifest?.let { manifest ->
                                    put("id", manifest.id)
                                    put("name", manifest.name)
                                    put("type", manifest.type.wire)
                                }
                            },
                        )
                    }
                }
            }
            textResult(result.toString())
        }
    },
)

private suspend fun workshopToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "workshop operation failed", isError = true)
}

private fun JsonObject.workshopString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.requiredWorkshopString(name: String): String =
    workshopString(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("missing argument $name")
