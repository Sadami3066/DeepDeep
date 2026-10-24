package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.agent.AgentToolResult
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
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

private const val WORKSHOP_DATA_MAX_VALUE_CHARS = 256 * 1024
private val WORKSHOP_DATA_KEY_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")

private val workshopDataGetParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("app") {
            put("type", "string")
            put("description", "Workshop app id, from workshop_list")
        }
        putJsonObject("key") {
            put("type", "string")
            put("description", "Data key: 1-64 lowercase letters, digits, '-' or '_', starting with a letter or digit")
        }
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("app"))
        add(JsonPrimitive("key"))
    })
}

private val workshopDataSetParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("app") {
            put("type", "string")
            put("description", "Workshop app id, from workshop_list")
        }
        putJsonObject("key") {
            put("type", "string")
            put("description", "Data key: 1-64 lowercase letters, digits, '-' or '_', starting with a letter or digit")
        }
        putJsonObject("value") {}
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("app"))
        add(JsonPrimitive("key"))
        add(JsonPrimitive("value"))
    })
}

private val workshopDataDeleteParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("app") {
            put("type", "string")
            put("description", "Workshop app id, from workshop_list")
        }
        putJsonObject("key") {
            put("type", "string")
            put("description", "Data key to delete")
        }
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("app"))
        add(JsonPrimitive("key"))
    })
}

private val workshopDataKeysParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("app") {
            put("type", "string")
            put("description", "Workshop app id, from workshop_list")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("app")) })
}

private val workshopDataGuidelines = listOf(
    "Use the workshop_data tools to read or update the JSON data persisted for a Workshop app; this is the app's data layer, not its code.",
    "The app page reads the same data through window.app.data (get/set/remove/keys); changing data does not require rewriting page files.",
    "Keep app code stable and update data when the user wants new daily content, form entries or other content.",
    "Prefer one JSON document per feature, for example one key holding a todo list.",
    "Use workshop_list to find the app id before calling a workshop_data tool.",
    "Use lowercase keys: 1-64 letters, digits, '-' or '_', starting with a letter or digit; one value may be at most 256KB.",
    "Never edit the SQLite database file directly.",
)

fun workshopDataTools(store: WorkshopStore): List<CodingTool> =
    workshopDataTools(store) { file -> SqliteWorkshopData(file) }

internal fun workshopDataTools(store: WorkshopStore, dataFactory: (File) -> WorkshopData): List<CodingTool> = listOf(
    workshopDataGetTool(store, dataFactory),
    workshopDataSetTool(store, dataFactory),
    workshopDataDeleteTool(store, dataFactory),
    workshopDataKeysTool(store, dataFactory),
)

private fun workshopDataGetTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(
    name = "workshop_data_get",
    description = "Read a JSON value from a Workshop app's persisted data layer (not its code). Missing keys return null.",
    parameters = workshopDataGetParameters,
    label = "workshop_data_get",
    promptSnippet = "Read a Workshop app data value",
    promptGuidelines = workshopDataGuidelines,
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val creation = requireWorkshopApp(store, args.requiredWorkshopString("app"))
            val key = workshopDataKey(args.requiredWorkshopString("key"))
            withWorkshopData(store, creation, dataFactory) { data ->
                val value = data.get(key)
                val result = buildJsonObject {
                    put("ok", true)
                    put("app", creation.manifest.id)
                    put("key", key)
                    put("value", value ?: JsonNull)
                }
                textResult(result.toString())
            }
        }
    },
)

private fun workshopDataSetTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(
    name = "workshop_data_set",
    description = "Write or overwrite one JSON value in a Workshop app's persisted data layer (not its code); the page reads it through window.app.data.",
    parameters = workshopDataSetParameters,
    label = "workshop_data_set",
    promptSnippet = "Write a Workshop app data value",
    promptGuidelines = workshopDataGuidelines,
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val creation = requireWorkshopApp(store, args.requiredWorkshopString("app"))
            val key = workshopDataKey(args.requiredWorkshopString("key"))
            val value = args["value"] ?: throw IllegalArgumentException("missing argument value")
            val encoded = value.toString()
            if (encoded.length > WORKSHOP_DATA_MAX_VALUE_CHARS) {
                throw IllegalArgumentException("value must be at most $WORKSHOP_DATA_MAX_VALUE_CHARS characters")
            }
            withWorkshopData(store, creation, dataFactory) { data ->
                data.set(key, value)
                val result = buildJsonObject {
                    put("ok", true)
                    put("app", creation.manifest.id)
                    put("key", key)
                }
                textResult(result.toString())
            }
        }
    },
)

private fun workshopDataDeleteTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(
    name = "workshop_data_delete",
    description = "Delete one key from a Workshop app's persisted data layer (not its code).",
    parameters = workshopDataDeleteParameters,
    label = "workshop_data_delete",
    promptSnippet = "Delete a Workshop app data key",
    promptGuidelines = workshopDataGuidelines,
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val creation = requireWorkshopApp(store, args.requiredWorkshopString("app"))
            val key = workshopDataKey(args.requiredWorkshopString("key"))
            withWorkshopData(store, creation, dataFactory) { data ->
                val deleted = data.remove(key)
                val result = buildJsonObject {
                    put("ok", true)
                    put("app", creation.manifest.id)
                    put("key", key)
                    put("deleted", deleted)
                }
                textResult(result.toString())
            }
        }
    },
)

private fun workshopDataKeysTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(
    name = "workshop_data_keys",
    description = "List the keys stored in a Workshop app's persisted data layer (not its code).",
    parameters = workshopDataKeysParameters,
    label = "workshop_data_keys",
    promptSnippet = "List Workshop app data keys",
    promptGuidelines = workshopDataGuidelines,
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val creation = requireWorkshopApp(store, args.requiredWorkshopString("app"))
            withWorkshopData(store, creation, dataFactory) { data ->
                val keys = data.keys().sorted()
                val result = buildJsonObject {
                    put("ok", true)
                    put("app", creation.manifest.id)
                    putJsonArray("keys") {
                        keys.forEach { key -> add(JsonPrimitive(key)) }
                    }
                }
                textResult(result.toString())
            }
        }
    },
)

private suspend fun requireWorkshopApp(store: WorkshopStore, id: String): WorkshopCreation =
    when (val entry = store.load(id)) {
        is WorkshopEntry.Valid -> entry.creation
        is WorkshopEntry.Invalid -> throw IllegalArgumentException(
            "workshop app is invalid: $id (${entry.reason.name.lowercase()}" +
                (entry.detail?.let { ": $it" } ?: "") + ")",
        )
        WorkshopEntry.Missing -> throw IllegalArgumentException("workshop app not found: $id")
    }

private fun workshopDataKey(raw: String): String {
    if (!WORKSHOP_DATA_KEY_REGEX.matches(raw)) {
        throw IllegalArgumentException(
            "invalid key: $raw (use 1-64 lowercase letters, digits, '-' or '_', starting with a letter or digit)",
        )
    }
    return raw
}

private suspend fun withWorkshopData(
    store: WorkshopStore,
    creation: WorkshopCreation,
    dataFactory: (File) -> WorkshopData,
    block: suspend (WorkshopData) -> AgentToolResult,
): AgentToolResult {
    val data = dataFactory(store.dataFile(creation.manifest.id))
    try {
        return block(data)
    } finally {
        data.close()
    }
}

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
