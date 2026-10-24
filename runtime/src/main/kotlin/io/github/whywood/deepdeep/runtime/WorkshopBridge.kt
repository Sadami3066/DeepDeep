package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class WorkshopRunMode {
    SANDBOX,
    FULL,
}

sealed interface WorkshopAgentEvent {
    data object Started : WorkshopAgentEvent

    data class Text(val delta: String) : WorkshopAgentEvent

    data class Tool(val name: String) : WorkshopAgentEvent

    data object Finished : WorkshopAgentEvent
}

class WorkshopAgentException(val code: String, message: String) : Exception(message)

interface WorkshopAgentRunner {
    suspend fun run(instruction: String, context: JsonElement?, onEvent: (WorkshopAgentEvent) -> Unit): String

    suspend fun cancel()
}

data class WorkshopPageError(
    val message: String,
    val source: String?,
    val line: Int?,
    val column: Int?,
    val stack: String?,
    val timestamp: Long,
)

object WorkshopBridgeErrors {
    const val INVALID_REQUEST = "invalid_request"
    const val UNKNOWN_METHOD = "unknown_method"
    const val INVALID_PARAMS = "invalid_params"
    const val FORBIDDEN = "forbidden"
    const val BUSY = "busy"
    const val DATA_INVALID_KEY = "data_invalid_key"
    const val DATA_TOO_LARGE = "data_too_large"
    const val DATA_CORRUPT = "data_corrupt"
    const val AGENT_FAILED = "agent_failed"
    const val AGENT_TIMEOUT = "agent_timeout"
    const val AGENT_CANCELLED = "agent_cancelled"
    const val MODEL_MISSING = "model_missing"
}

private const val AGENT_EVENT = "agent"
private const val MAX_DATA_VALUE_CHARS = 256 * 1024
private const val MAX_AGENT_PROMPT_CHARS = 8_000
private const val MAX_AGENT_CONTEXT_CHARS = 32 * 1024
private const val MAX_TOAST_CHARS = 200
private val WORKSHOP_KEY_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")

class WorkshopBridgeRouter(
    private val manifest: WorkshopManifest,
    private val data: WorkshopData,
    private val agent: WorkshopAgentRunner?,
    private val toast: (String) -> Unit,
) {
    suspend fun handle(rawMessage: String, emit: suspend (JsonObject) -> Unit): String {
        val request = runCatching { Json.parseToJsonElement(rawMessage) }.getOrNull() as? JsonObject
            ?: return errorResponse(null, WorkshopBridgeErrors.INVALID_REQUEST, "message must be a JSON object")
        val id = (request["id"] as? JsonPrimitive)
            ?.takeIf { !it.isString && it.contentOrNull != null }
            ?.contentOrNull
            ?.toLongOrNull()
            ?: return errorResponse(null, WorkshopBridgeErrors.INVALID_REQUEST, "id must be a number")
        val method = (request["method"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: return errorResponse(id, WorkshopBridgeErrors.INVALID_REQUEST, "method must be a string")
        val paramsElement = request["params"]
        if (paramsElement != null && paramsElement !is JsonObject) {
            return errorResponse(id, WorkshopBridgeErrors.INVALID_PARAMS, "params must be an object")
        }
        val params = paramsElement as? JsonObject ?: JsonObject(emptyMap())
        return try {
            successResponse(id, dispatch(method, params, emit))
        } catch (error: WorkshopBridgeException) {
            errorResponse(id, error.code, error.message ?: error.code)
        } catch (error: WorkshopAgentException) {
            errorResponse(id, mapAgentErrorCode(error.code), error.message ?: "Agent run failed")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorResponse(id, WorkshopBridgeErrors.AGENT_FAILED, error.message ?: "Workshop bridge error")
        }
    }

    private suspend fun dispatch(
        method: String,
        params: JsonObject,
        emit: suspend (JsonObject) -> Unit,
    ): JsonElement = when (method) {
        "info" -> info()
        "data.get" -> dataGet(params)
        "data.set" -> dataSet(params)
        "data.remove" -> dataRemove(params)
        "data.keys" -> dataKeys()
        "agent.run" -> agentRun(params, emit)
        "agent.cancel" -> agentCancel()
        "ui.toast" -> showToast(params)
        else -> throw WorkshopBridgeException(WorkshopBridgeErrors.UNKNOWN_METHOD, "Unknown method: $method")
    }

    private fun info(): JsonObject = buildJsonObject {
        putJsonObject("app") {
            put("id", manifest.id)
            put("name", manifest.name)
            put("type", manifest.type.wire)
            put("icon", manifest.icon)
            put("version", manifest.version)
        }
        putJsonArray("capabilities") {
            if (manifest.permissions.contains(WorkshopPermissions.DATA_KV)) {
                add(JsonPrimitive(WorkshopPermissions.DATA_KV))
            }
            if (manifest.permissions.contains(WorkshopPermissions.AGENT_RUN)) {
                add(JsonPrimitive(WorkshopPermissions.AGENT_RUN))
            }
        }
    }

    private suspend fun dataGet(params: JsonObject): JsonElement {
        requirePermission(WorkshopPermissions.DATA_KV)
        val key = keyOf(params)
        return dataCall { data.get(key) } ?: JsonNull
    }

    private suspend fun dataSet(params: JsonObject): JsonElement {
        requirePermission(WorkshopPermissions.DATA_KV)
        val key = keyOf(params)
        val value = params["value"] ?: throw WorkshopBridgeException(
            WorkshopBridgeErrors.INVALID_PARAMS,
            "value is required",
        )
        if (value.toString().length > MAX_DATA_VALUE_CHARS) {
            throw WorkshopBridgeException(
                WorkshopBridgeErrors.DATA_TOO_LARGE,
                "value must be at most $MAX_DATA_VALUE_CHARS characters",
            )
        }
        dataCall { data.set(key, value) }
        return JsonPrimitive(true)
    }

    private suspend fun dataRemove(params: JsonObject): JsonElement {
        requirePermission(WorkshopPermissions.DATA_KV)
        val key = keyOf(params)
        return JsonPrimitive(dataCall { data.remove(key) })
    }

    private suspend fun dataKeys(): JsonElement {
        requirePermission(WorkshopPermissions.DATA_KV)
        val keys = dataCall { data.keys() }.sorted()
        return buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }
    }

    private suspend fun agentRun(params: JsonObject, emit: suspend (JsonObject) -> Unit): JsonObject {
        requirePermission(WorkshopPermissions.AGENT_RUN)
        val runner = agent ?: throw WorkshopBridgeException(
            WorkshopBridgeErrors.FORBIDDEN,
            "Agent runs are not available for this app",
        )
        val prompt = (params["prompt"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.trim()
        if (prompt.isNullOrEmpty() || prompt.length > MAX_AGENT_PROMPT_CHARS) {
            throw WorkshopBridgeException(
                WorkshopBridgeErrors.INVALID_PARAMS,
                "prompt must be 1-$MAX_AGENT_PROMPT_CHARS characters",
            )
        }
        val context = params["context"]
        if (context != null && context.toString().length > MAX_AGENT_CONTEXT_CHARS) {
            throw WorkshopBridgeException(
                WorkshopBridgeErrors.INVALID_PARAMS,
                "context must be at most $MAX_AGENT_CONTEXT_CHARS characters",
            )
        }
        return coroutineScope {
            val events = Channel<JsonObject>(Channel.UNLIMITED)
            val forwarder = launch { for (event in events) emit(event) }
            try {
                val text = try {
                    runner.run(prompt, context) { event -> events.trySend(agentEventJson(event)) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: WorkshopAgentException) {
                    throw WorkshopBridgeException(
                        mapAgentErrorCode(error.code),
                        error.message ?: "Agent run failed",
                    )
                } catch (error: Exception) {
                    throw WorkshopBridgeException(
                        WorkshopBridgeErrors.AGENT_FAILED,
                        error.message ?: "Agent run failed",
                    )
                }
                events.close()
                forwarder.join()
                buildJsonObject { put("text", text) }
            } finally {
                events.close()
                forwarder.cancel()
            }
        }
    }

    private suspend fun agentCancel(): JsonElement {
        agent?.cancel()
        return JsonPrimitive(true)
    }

    private fun showToast(params: JsonObject): JsonElement {
        val text = (params["text"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
        if (text == null || text.length > MAX_TOAST_CHARS) {
            throw WorkshopBridgeException(
                WorkshopBridgeErrors.INVALID_PARAMS,
                "text must be at most $MAX_TOAST_CHARS characters",
            )
        }
        toast(text)
        return JsonPrimitive(true)
    }

    private fun requirePermission(permission: String) {
        if (!manifest.permissions.contains(permission)) {
            throw WorkshopBridgeException(
                WorkshopBridgeErrors.FORBIDDEN,
                "Permission is not granted: $permission",
            )
        }
    }

    private fun keyOf(params: JsonObject): String {
        val key = (params["key"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: throw WorkshopBridgeException(WorkshopBridgeErrors.INVALID_PARAMS, "key must be a string")
        if (!WORKSHOP_KEY_REGEX.matches(key)) {
            throw WorkshopBridgeException(
                WorkshopBridgeErrors.DATA_INVALID_KEY,
                "key must match ^[a-z0-9][a-z0-9_-]{0,63}$",
            )
        }
        return key
    }

    private suspend fun <T> dataCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw WorkshopBridgeException(
            WorkshopBridgeErrors.DATA_CORRUPT,
            error.message ?: "Workshop data is unavailable",
        )
    }

    private fun successResponse(id: Long, result: JsonElement): String = buildJsonObject {
        put("id", id)
        put("ok", true)
        put("result", result)
    }.toString()

    private fun errorResponse(id: Long?, code: String, message: String): String = buildJsonObject {
        if (id == null) put("id", JsonNull) else put("id", id)
        put("ok", false)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }.toString()
}

private fun agentEventJson(event: WorkshopAgentEvent): JsonObject = buildJsonObject {
    put("event", AGENT_EVENT)
    when (event) {
        WorkshopAgentEvent.Started -> put("type", "started")
        is WorkshopAgentEvent.Text -> {
            put("type", "text")
            put("delta", event.delta)
        }

        is WorkshopAgentEvent.Tool -> {
            put("type", "tool")
            put("name", event.name)
        }

        WorkshopAgentEvent.Finished -> put("type", "finished")
    }
}

private fun mapAgentErrorCode(code: String): String = when (code.removePrefix("agent_")) {
    "busy" -> WorkshopBridgeErrors.BUSY
    "timeout" -> WorkshopBridgeErrors.AGENT_TIMEOUT
    "cancelled", "canceled" -> WorkshopBridgeErrors.AGENT_CANCELLED
    "model_missing" -> WorkshopBridgeErrors.MODEL_MISSING
    else -> WorkshopBridgeErrors.AGENT_FAILED
}

private class WorkshopBridgeException(val code: String, message: String) : Exception(message)
