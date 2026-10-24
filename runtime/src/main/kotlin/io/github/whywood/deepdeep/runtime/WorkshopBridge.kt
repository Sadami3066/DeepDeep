package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object WorkshopBridgeErrors {
    const val INVALID_REQUEST = "invalid_request"
    const val UNKNOWN_METHOD = "unknown_method"
    const val INVALID_PARAMS = "invalid_params"
    const val DATA_INVALID_KEY = "data_invalid_key"
    const val DATA_TOO_LARGE = "data_too_large"
    const val DATA_CORRUPT = "data_corrupt"
}

private const val MAX_DATA_VALUE_CHARS = 256 * 1024
private const val MAX_TOAST_CHARS = 200
private val WORKSHOP_KEY_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")

class WorkshopBridgeRouter(
    private val manifest: WorkshopManifest,
    private val data: WorkshopData,
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
            successResponse(id, dispatch(method, params))
        } catch (error: BridgeException) {
            errorResponse(id, error.code, error.message ?: error.code)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorResponse(id, WorkshopBridgeErrors.INVALID_REQUEST, error.message ?: "Workshop bridge error")
        }
    }

    private suspend fun dispatch(method: String, params: JsonObject): JsonElement = when (method) {
        "info" -> info()
        "data.get" -> dataGet(params)
        "data.set" -> dataSet(params)
        "data.remove" -> dataRemove(params)
        "data.keys" -> dataKeys()
        "ui.toast" -> showToast(params)
        else -> throw BridgeException(WorkshopBridgeErrors.UNKNOWN_METHOD, "Unknown method: $method")
    }

    private fun info(): JsonObject = buildJsonObject {
        putJsonObject("app") {
            put("id", manifest.id)
            put("name", manifest.name)
            put("icon", manifest.icon)
            put("version", manifest.version)
        }
    }

    private suspend fun dataGet(params: JsonObject): JsonElement {
        val key = keyOf(params)
        return dataCall { data.get(key) } ?: JsonNull
    }

    private suspend fun dataSet(params: JsonObject): JsonElement {
        val key = keyOf(params)
        val value = params["value"] ?: throw BridgeException(
            WorkshopBridgeErrors.INVALID_PARAMS,
            "value is required",
        )
        if (value.toString().length > MAX_DATA_VALUE_CHARS) {
            throw BridgeException(
                WorkshopBridgeErrors.DATA_TOO_LARGE,
                "value must be at most $MAX_DATA_VALUE_CHARS characters",
            )
        }
        dataCall { data.set(key, value) }
        return JsonPrimitive(true)
    }

    private suspend fun dataRemove(params: JsonObject): JsonElement {
        val key = keyOf(params)
        return JsonPrimitive(dataCall { data.remove(key) })
    }

    private suspend fun dataKeys(): JsonElement {
        val keys = dataCall { data.keys() }.sorted()
        return buildJsonArray { keys.forEach { add(JsonPrimitive(it)) } }
    }

    private fun showToast(params: JsonObject): JsonElement {
        val text = (params["text"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
        if (text == null || text.length > MAX_TOAST_CHARS) {
            throw BridgeException(
                WorkshopBridgeErrors.INVALID_PARAMS,
                "text must be at most $MAX_TOAST_CHARS characters",
            )
        }
        toast(text)
        return JsonPrimitive(true)
    }

    private fun keyOf(params: JsonObject): String {
        val key = (params["key"] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?: throw BridgeException(WorkshopBridgeErrors.INVALID_PARAMS, "key must be a string")
        if (!WORKSHOP_KEY_REGEX.matches(key)) {
            throw BridgeException(
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
        throw BridgeException(
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

private class BridgeException(val code: String, message: String) : Exception(message)
