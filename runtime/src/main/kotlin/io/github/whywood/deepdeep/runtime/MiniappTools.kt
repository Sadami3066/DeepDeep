package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
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

private val miniappCreateParameters: JsonObject = buildJsonObject {
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

private val miniappLoadParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("path") {
            put("type", "string")
            put("description", "Absolute path of the draft folder returned by miniapp_create; relative paths are rejected")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("path")) })
}

private val miniappListParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private val miniappDeleteParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("app") {
            put("type", "string")
            put("description", "miniapp id, from miniapp_list")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("app")) })
}

fun miniappTools(store: MiniappStore, supportsChatDelivery: Boolean = true): List<CodingTool> = listOf(
    miniappCreateTool(store),
    miniappLoadTool(store, supportsChatDelivery),
    miniappListTool(store),
    miniappDeleteTool(store),
)

private fun miniappCreateTool(store: MiniappStore): CodingTool = CodingTool(
    name = "miniapp_create",
    description = "Create a miniapp draft: a self-contained HTML/CSS/JS folder the user can open on the phone. Returns the draft folder where you write the app files; call miniapp_load afterwards to install it.",
    parameters = miniappCreateParameters,
    label = "miniapp_create",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miniappToolResult {
            val draft = store.createDraft(
                name = args.requiredMiniappString("name"),
                description = args.miniappString("description").orEmpty(),
                icon = args.miniappString("icon"),
            )
            val path = draft.directory.absolutePath
            val result = buildJsonObject {
                put("ok", true)
                putJsonObject("draft") {
                    put("id", draft.manifest.id)
                    put("name", draft.manifest.name)
                    put("path", path)
                }
                put("next", "Write the app files under $path with absolute paths using read/write/edit (entry defaults to index.html). Follow the miniapp skill. Then call miniapp_load with path=$path.")
            }
            textResult(result.toString())
        }
    },
)

private fun miniappLoadTool(store: MiniappStore, supportsChatDelivery: Boolean): CodingTool = CodingTool(
    name = "miniapp_load",
    description = "Install a miniapp draft so the user can open it. Call it with the absolute draft path returned by miniapp_create after writing all app files; relative paths are rejected.",
    parameters = miniappLoadParameters,
    label = "miniapp_load",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miniappToolResult {
            val creation = store.loadDraft(args.requiredMiniappString("path"))
            val result = buildJsonObject {
                put("ok", true)
                if (supportsChatDelivery) put("delivery", "present")
                putJsonObject("app") {
                    put("id", creation.manifest.id)
                    put("name", creation.manifest.name)
                    put("icon", creation.manifest.icon)
                    put("version", creation.manifest.version)
                }
                put("path", creation.directory.absolutePath)
                put(
                    "next",
                    if (supportsChatDelivery) "Deliver this app with present: {items:[{type:miniapp, id:${creation.manifest.id}}]}. Do not restate the app details in your reply."
                    else "Return the installed app id, absolute path and final text to the calling page.",
                )
            }
            textResult(result.toString())
        }
    },
)

private fun miniappListTool(store: MiniappStore): CodingTool = CodingTool(
    name = "miniapp_list",
    description = "List installed miniapps and the current drafts with their absolute folder paths.",
    parameters = miniappListParameters,
    label = "miniapp_list",
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        miniappToolResult {
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

private fun miniappDeleteTool(store: MiniappStore): CodingTool = CodingTool(
    name = "miniapp_delete",
    description = "Delete an installed miniapp together with all of its saved data. Use it when the user asks to remove a mini app; this cannot be undone.",
    parameters = miniappDeleteParameters,
    label = "miniapp_delete",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miniappToolResult {
            val app = args.requiredMiniappString("app")
            if (!store.delete(app)) {
                throw IllegalArgumentException("miniapp not found: $app (use miniapp_list to see installed apps)")
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

private const val MINIAPP_DATA_MAX_VALUE_CHARS = 256 * 1024
private val MINIAPP_DATA_KEY_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")
private val MINIAPP_EDIT_ACTIONS = buildJsonArray {
    add(JsonPrimitive("get"))
    add(JsonPrimitive("set"))
    add(JsonPrimitive("delete"))
    add(JsonPrimitive("keys"))
}

private val miniappEditParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put("enum", MINIAPP_EDIT_ACTIONS)
            put("description", "Operation: get reads one key, set writes it, delete removes it, keys lists all keys")
        }
        putJsonObject("app") {
            put("type", "string")
            put("description", "miniapp id, from miniapp_list")
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

fun miniappEditTool(store: MiniappStore): CodingTool =
    miniappEditTool(store) { file -> SqliteMiniappData(file) }

internal fun miniappEditTool(store: MiniappStore, dataFactory: (File) -> MiniappData): CodingTool = CodingTool(
    name = "miniapp_edit",
    description = "Read or edit one JSON value in a miniapp's persisted data layer (not its code): action get reads a key, set writes or overwrites it, delete removes it, keys lists all keys.",
    parameters = miniappEditParameters,
    label = "miniapp_edit",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        miniappToolResult {
            val action = args.requiredMiniappString("action").lowercase()
            val app = args.requiredMiniappString("app")
            when (action) {
                "get" -> miniappEditGet(store, app, args, dataFactory)
                "set" -> miniappEditSet(store, app, args, dataFactory)
                "delete" -> miniappEditDelete(store, app, args, dataFactory)
                "keys" -> miniappEditKeys(store, app, dataFactory)
                else -> throw IllegalArgumentException("action must be get, set, delete or keys")
            }
        }
    },
)

private suspend fun miniappEditGet(
    store: MiniappStore,
    app: String,
    args: JsonObject,
    dataFactory: (File) -> MiniappData,
): AgentToolResult {
    val creation = requireMiniappApp(store, app)
    val key = miniappDataKey(args.requiredMiniappString("key"))
    return withMiniappData(store, creation, dataFactory) { data ->
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

private suspend fun miniappEditSet(
    store: MiniappStore,
    app: String,
    args: JsonObject,
    dataFactory: (File) -> MiniappData,
): AgentToolResult {
    val creation = requireMiniappApp(store, app)
    val key = miniappDataKey(args.requiredMiniappString("key"))
    val value = args["value"] ?: throw IllegalArgumentException("missing argument value")
    val encoded = value.toString()
    if (encoded.length > MINIAPP_DATA_MAX_VALUE_CHARS) {
        throw IllegalArgumentException("value must be at most $MINIAPP_DATA_MAX_VALUE_CHARS characters")
    }
    return withMiniappData(store, creation, dataFactory) { data ->
        data.set(key, value)
        val result = buildJsonObject {
            put("ok", true)
            put("app", creation.manifest.id)
            put("key", key)
        }
        textResult(result.toString())
    }
}

private suspend fun miniappEditDelete(
    store: MiniappStore,
    app: String,
    args: JsonObject,
    dataFactory: (File) -> MiniappData,
): AgentToolResult {
    val creation = requireMiniappApp(store, app)
    val key = miniappDataKey(args.requiredMiniappString("key"))
    return withMiniappData(store, creation, dataFactory) { data ->
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

private suspend fun miniappEditKeys(
    store: MiniappStore,
    app: String,
    dataFactory: (File) -> MiniappData,
): AgentToolResult {
    val creation = requireMiniappApp(store, app)
    return withMiniappData(store, creation, dataFactory) { data ->
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

private suspend fun requireMiniappApp(store: MiniappStore, id: String): MiniappCreation =
    when (val entry = store.load(id)) {
        is MiniappEntry.Valid -> entry.creation
        is MiniappEntry.Invalid -> throw IllegalArgumentException(
            "miniapp is invalid: $id (${entry.reason.name.lowercase()}" +
                (entry.detail?.let { ": $it" } ?: "") + ")",
        )
        MiniappEntry.Missing -> throw IllegalArgumentException("miniapp not found: $id")
    }

private fun miniappDataKey(raw: String): String {
    if (!MINIAPP_DATA_KEY_REGEX.matches(raw)) {
        throw IllegalArgumentException(
            "invalid key: $raw (use 1-64 lowercase letters, digits, '-' or '_', starting with a letter or digit)",
        )
    }
    return raw
}

private suspend fun withMiniappData(
    store: MiniappStore,
    creation: MiniappCreation,
    dataFactory: (File) -> MiniappData,
    block: suspend (MiniappData) -> AgentToolResult,
): AgentToolResult {
    val data = dataFactory(store.dataFile(creation.manifest.id))
    try {
        return block(data)
    } finally {
        data.close()
    }
}

private suspend fun miniappToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "miniapp operation failed", isError = true)
}

private fun JsonObject.miniappString(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.requiredMiniappString(name: String): String =
    miniappString(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("missing argument $name")
