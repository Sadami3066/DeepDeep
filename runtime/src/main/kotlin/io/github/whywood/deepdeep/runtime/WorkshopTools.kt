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
            put("description", "Absolute path of the draft folder returned by workshop_create; relative paths are rejected")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("path")) })
}

private val workshopListParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private val workshopDeleteParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("app") {
            put("type", "string")
            put("description", "Workshop app id, from workshop_list")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("app")) })
}

fun workshopTools(store: WorkshopStore): List<CodingTool> = listOf(
    workshopCreateTool(store),
    workshopLoadTool(store),
    workshopListTool(store),
    workshopDeleteTool(store),
)

private fun workshopCreateTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_create",
    description = "Create a Workshop mini app draft: a self-contained HTML/CSS/JS folder the user can open on the phone. Returns the draft folder where you write the app files; call workshop_load afterwards to install it.",
    parameters = workshopCreateParameters,
    label = "workshop_create",
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
    description = "Install a Workshop draft as a mini app so the user can open it. Call it with the absolute draft path returned by workshop_create after writing all app files; relative paths are rejected.",
    parameters = workshopLoadParameters,
    label = "workshop_load",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val creation = store.loadDraft(args.requiredWorkshopString("path"))
            val result = buildJsonObject {
                put("ok", true)
                put("delivery", "present")
                putJsonObject("app") {
                    put("id", creation.manifest.id)
                    put("name", creation.manifest.name)
                    put("icon", creation.manifest.icon)
                    put("version", creation.manifest.version)
                }
                put("path", creation.directory.absolutePath)
                put(
                    "next",
                    "Deliver this app with present: {items:[{type:creative, id:${creation.manifest.id}}]}. Do not restate the app details in your reply.",
                )
            }
            textResult(result.toString())
        }
    },
)

private fun workshopListTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_list",
    description = "List installed Workshop mini apps and the current drafts with their absolute folder paths.",
    parameters = workshopListParameters,
    label = "workshop_list",
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

private fun workshopDeleteTool(store: WorkshopStore): CodingTool = CodingTool(
    name = "workshop_delete",
    description = "Delete an installed Workshop mini app together with all of its saved data. Use it when the user asks to remove a mini app; this cannot be undone.",
    parameters = workshopDeleteParameters,
    label = "workshop_delete",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val app = args.requiredWorkshopString("app")
            if (!store.delete(app)) {
                throw IllegalArgumentException("workshop app not found: $app (use workshop_list to see installed apps)")
            }
            val result = buildJsonObject {
                put("ok", true)
                put("deleted", true)
                put("app", app)
            }
            textResult(result.toString())
        }
    },
)

private const val WORKSHOP_DATA_MAX_VALUE_CHARS = 256 * 1024
private val WORKSHOP_DATA_KEY_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")
private val WORKSHOP_EDIT_ACTIONS = buildJsonArray {
    add(JsonPrimitive("get"))
    add(JsonPrimitive("set"))
    add(JsonPrimitive("delete"))
    add(JsonPrimitive("keys"))
}

private val workshopEditParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put("enum", WORKSHOP_EDIT_ACTIONS)
            put("description", "Operation: get reads one key, set writes it, delete removes it, keys lists all keys")
        }
        putJsonObject("app") {
            put("type", "string")
            put("description", "Workshop app id, from workshop_list")
        }
        putJsonObject("key") {
            put("type", "string")
            put("description", "Data key for get, set and delete: 1-64 lowercase letters, digits, '-' or '_', starting with a letter or digit")
        }
        putJsonObject("value") {
            put("description", "JSON value to store for action set, at most 256KB")
        }
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("action"))
        add(JsonPrimitive("app"))
    })
}

fun workshopEditTool(store: WorkshopStore): CodingTool =
    workshopEditTool(store) { file -> SqliteWorkshopData(file) }

internal fun workshopEditTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(
    name = "workshop_edit",
    description = "Read or edit one JSON value in a Workshop app's persisted data layer (not its code): action get reads a key, set writes or overwrites it, delete removes it, keys lists all keys.",
    parameters = workshopEditParameters,
    label = "workshop_edit",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        workshopToolResult {
            val action = args.requiredWorkshopString("action").lowercase()
            val app = args.requiredWorkshopString("app")
            when (action) {
                "get" -> workshopEditGet(store, app, args, dataFactory)
                "set" -> workshopEditSet(store, app, args, dataFactory)
                "delete" -> workshopEditDelete(store, app, args, dataFactory)
                "keys" -> workshopEditKeys(store, app, dataFactory)
                else -> throw IllegalArgumentException("action must be get, set, delete or keys")
            }
        }
    },
)

private suspend fun workshopEditGet(
    store: WorkshopStore,
    app: String,
    args: JsonObject,
    dataFactory: (File) -> WorkshopData,
): AgentToolResult {
    val creation = requireWorkshopApp(store, app)
    val key = workshopDataKey(args.requiredWorkshopString("key"))
    return withWorkshopData(store, creation, dataFactory) { data ->
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

private suspend fun workshopEditSet(
    store: WorkshopStore,
    app: String,
    args: JsonObject,
    dataFactory: (File) -> WorkshopData,
): AgentToolResult {
    val creation = requireWorkshopApp(store, app)
    val key = workshopDataKey(args.requiredWorkshopString("key"))
    val value = args["value"] ?: throw IllegalArgumentException("missing argument value")
    val encoded = value.toString()
    if (encoded.length > WORKSHOP_DATA_MAX_VALUE_CHARS) {
        throw IllegalArgumentException("value must be at most $WORKSHOP_DATA_MAX_VALUE_CHARS characters")
    }
    return withWorkshopData(store, creation, dataFactory) { data ->
        data.set(key, value)
        val result = buildJsonObject {
            put("ok", true)
            put("app", creation.manifest.id)
            put("key", key)
        }
        textResult(result.toString())
    }
}

private suspend fun workshopEditDelete(
    store: WorkshopStore,
    app: String,
    args: JsonObject,
    dataFactory: (File) -> WorkshopData,
): AgentToolResult {
    val creation = requireWorkshopApp(store, app)
    val key = workshopDataKey(args.requiredWorkshopString("key"))
    return withWorkshopData(store, creation, dataFactory) { data ->
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

private suspend fun workshopEditKeys(
    store: WorkshopStore,
    app: String,
    dataFactory: (File) -> WorkshopData,
): AgentToolResult {
    val creation = requireWorkshopApp(store, app)
    return withWorkshopData(store, creation, dataFactory) { data ->
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
