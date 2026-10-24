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

private val workshopCreateParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("name") {
            put("type", "string")
            put("description", "Display name of the mini app, 1-40 characters")
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
    put("required", buildJsonArray { add(JsonPrimitive("name")) })
}

private val workshopLoadParameters: JsonObject = buildJsonObject {
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
    workshopLoadTool(store),
    workshopListTool(store),
)

private fun workshopCreateTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_create",
    description = "Create a Workshop mini app draft: a self-contained HTML/CSS/JS folder the user can open on the phone. Returns the draft folder where you write the app files; call workshop_load afterwards to install it.",
    parameters = workshopCreateParameters,
    label = "workshop_create",
    promptSnippet = "Create a Workshop mini app draft folder",
    promptGuidelines = listOf(
        "Use the workshop tools when the user wants an interactive mini app (创意) in DeepDeep, not a static report or document.",
        "The workshop skill in the current context defines the folder format; read it before writing any app file.",
        "Write app files with absolute paths under the returned draft folder using read/write/edit; the workshop folder is outside the bash workspace, so never use bash for it.",
        "Call workshop_load with the draft path when the app is ready, then fix every error it reports.",
        "Use workshop_list to locate an existing app when the user wants changes, then edit that app path in place; no reload or register step is needed.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val draft = store.createDraft(
                name = args.requiredWorkshopString("name"),
                description = args.workshopString("description").orEmpty(),
                icon = args.workshopString("icon"),
            )
            val path = draft.directory.absolutePath
            val result = buildJsonObject {
                put("ok", true)
                putJsonObject("draft") {
                    put("id", draft.manifest.id)
                    put("name", draft.manifest.name)
                    put("path", path)
                }
                put("next", "Write the app files under $path with absolute paths using read/write/edit (entry defaults to index.html). Follow the workshop skill. Then call workshop_load with path=$path.")
            }
            textResult(result.toString())
        }
    },
)

private fun workshopLoadTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_load",
    description = "Load a Workshop draft folder from the build folder as a mini app so the user can open it. Call it with the draft path returned by workshop_create after writing all app files.",
    parameters = workshopLoadParameters,
    label = "workshop_load",
    promptSnippet = "Load a Workshop draft as a mini app",
    promptGuidelines = listOf(
        "Call workshop_load with the exact absolute draft path returned by workshop_create.",
        "Fix the reported error and retry until loading succeeds; the app is not visible to the user before that.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val creation = store.loadDraft(args.requiredWorkshopString("path"))
            val result = buildJsonObject {
                put("ok", true)
                putJsonObject("app") {
                    put("id", creation.manifest.id)
                    put("name", creation.manifest.name)
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
    description = "List loaded Workshop mini apps and the current drafts with their absolute folder paths.",
    parameters = workshopListParameters,
    label = "workshop_list",
    promptSnippet = "List Workshop mini apps and drafts",
    promptGuidelines = listOf(
        "Use workshop_list to find an existing app when the user asks to change one, then edit that app folder in place with absolute paths.",
        "Drafts are unfinished apps in the build folder; loaded apps are under the apps folder.",
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
                                put("icon", creation.manifest.icon)
                                put("version", creation.manifest.version)
                                put("path", creation.directory.absolutePath)
                                put("description", creation.manifest.description)
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
