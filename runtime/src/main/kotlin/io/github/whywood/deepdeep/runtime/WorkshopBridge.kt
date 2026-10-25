package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object WorkshopBridgeErrors {
    const val INVALID_REQUEST = "invalid_request"
    const val UNKNOWN_METHOD = "unknown_method"
    const val INVALID_PARAMS = "invalid_params"
    const val DATA_INVALID_KEY = "data_invalid_key"
    const val DATA_TOO_LARGE = "data_too_large"
    const val DATA_CORRUPT = "data_corrupt"
    const val AGENT_UNAVAILABLE = "agent_unavailable"
    const val AGENT_FAILED = "agent_failed"
}

private const val MAX_DATA_VALUE_CHARS = 256 * 1024
private const val MAX_TOAST_CHARS = 200
private const val MAX_AGENT_PROMPT_CHARS = 64 * 1024
private const val MAX_AGENT_SYSTEM_PROMPT_CHARS = 16 * 1024
private const val MAX_AGENT_TURNS = 64
private val WORKSHOP_KEY_REGEX = Regex("[a-z0-9][a-z0-9_-]{0,63}")

class WorkshopBridgeRouter(
    private val manifest: WorkshopManifest,
    private val data: WorkshopData,
    private val toast: (String) -> Unit,
    private val agent: WorkshopAgentGateway? = null,
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
            successResponse(id, dispatch(method, params, id, emit))
        } catch (error: BridgeException) {
            errorResponse(id, error.code, error.message ?: error.code)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorResponse(id, WorkshopBridgeErrors.INVALID_REQUEST, error.message ?: "Workshop bridge error")
        }
    }

    private suspend fun dispatch(
        method: String,
        params: JsonObject,
        id: Long,
        emit: suspend (JsonObject) -> Unit,
    ): JsonElement = when (method) {
        "info" -> info()
        "data.get" -> dataGet(params)
        "data.set" -> dataSet(params)
        "data.remove" -> dataRemove(params)
        "data.keys" -> dataKeys()
        "ui.toast" -> showToast(params)
        "agent.models" -> agentModels()
        "agent.capabilities" -> agentCapabilities()
        "agent.run" -> agentRun(id, params, emit)
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

    private fun requireAgent(): WorkshopAgentGateway = agent
        ?: throw BridgeException(WorkshopBridgeErrors.AGENT_UNAVAILABLE, "workshop agent is unavailable")

    private suspend fun agentModels(): JsonElement {
        val gateway = requireAgent()
        val models = agentCall { gateway.models() }
        return buildJsonObject {
            putJsonArray("models") {
                models.forEach { model ->
                    add(
                        buildJsonObject {
                            put("providerId", model.providerId)
                            put("modelId", model.modelId)
                            put("label", model.label)
                            putJsonArray("thinkingLevels") {
                                model.thinkingLevels.forEach { level -> add(JsonPrimitive(level)) }
                            }
                        },
                    )
                }
            }
        }
    }

    private suspend fun agentCapabilities(): JsonElement {
        val gateway = requireAgent()
        val capabilities = agentCall { gateway.capabilities() }
        return buildJsonObject {
            putJsonArray("tools") {
                capabilities.tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                        },
                    )
                }
            }
            putJsonArray("skills") {
                capabilities.skills.forEach { skill ->
                    add(
                        buildJsonObject {
                            put("name", skill.name)
                            put("description", skill.description)
                        },
                    )
                }
            }
        }
    }

    private suspend fun agentRun(
        id: Long,
        params: JsonObject,
        emit: suspend (JsonObject) -> Unit,
    ): JsonElement {
        val gateway = requireAgent()
        val request = WorkshopAgentRequest(
            prompt = promptOf(params),
            providerId = optionalText(params, "providerId"),
            modelId = optionalText(params, "modelId"),
            thinkingLevel = optionalText(params, "thinkingLevel"),
            systemPrompt = systemPromptOf(params),
            maxTurns = maxTurnsOf(params),
            tools = stringListOf(params, "tools"),
            skills = stringListOf(params, "skills"),
        )
        val text = agentCall {
            gateway.run(request) { event -> emit(eventEnvelope(id, event)) }
        }
        return buildJsonObject { put("text", text) }
    }

    private fun eventEnvelope(id: Long, event: WorkshopAgentEvent): JsonObject = buildJsonObject {
        put("id", id)
        putJsonObject("event") {
            put("type", event.type)
            event.text?.let { put("text", it) }
            event.name?.let { put("name", it) }
            event.isError?.let { put("isError", it) }
            event.durationMs?.let { put("durationMs", it) }
            event.status?.let { put("status", it) }
            if (event.type == WorkshopAgentEventType.RUN_END) put("truncated", event.truncated)
            event.code?.let { put("code", it) }
            event.message?.let { put("message", it) }
        }
    }

    private fun promptOf(params: JsonObject): String {
        val prompt = (requiredText(params, "prompt") ?: throw BridgeException(
            WorkshopBridgeErrors.INVALID_PARAMS,
            "prompt is required",
        )).trim()
        if (prompt.isEmpty()) {
            throw BridgeException(WorkshopBridgeErrors.INVALID_PARAMS, "prompt must not be empty")
        }
        if (prompt.length > MAX_AGENT_PROMPT_CHARS) {
            throw BridgeException(
                WorkshopBridgeErrors.INVALID_PARAMS,
                "prompt must be at most $MAX_AGENT_PROMPT_CHARS characters",
            )
        }
        return prompt
    }

    private fun systemPromptOf(params: JsonObject): String? {
        val systemPrompt = optionalText(params, "systemPrompt") ?: return null
        if (systemPrompt.length > MAX_AGENT_SYSTEM_PROMPT_CHARS) {
            throw BridgeException(
                WorkshopBridgeErrors.INVALID_PARAMS,
                "systemPrompt must be at most $MAX_AGENT_SYSTEM_PROMPT_CHARS characters",
            )
        }
        return systemPrompt
    }

    private fun maxTurnsOf(params: JsonObject): Int? {
        val element = params["maxTurns"] ?: return null
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || primitive.isString) {
            throw BridgeException(WorkshopBridgeErrors.INVALID_PARAMS, "maxTurns must be an integer")
        }
        val turns = primitive.intOrNull
            ?: throw BridgeException(WorkshopBridgeErrors.INVALID_PARAMS, "maxTurns must be an integer")
        if (turns < 1 || turns > MAX_AGENT_TURNS) {
            throw BridgeException(
                WorkshopBridgeErrors.INVALID_PARAMS,
                "maxTurns must be between 1 and $MAX_AGENT_TURNS",
            )
        }
        return turns
    }

    private fun stringListOf(params: JsonObject, field: String): List<String>? {
        val element = params[field] ?: return null
        if (element is JsonNull) return null
        val array = element as? JsonArray ?: throw BridgeException(
            WorkshopBridgeErrors.INVALID_PARAMS,
            "$field must be an array of strings",
        )
        return array.map { item ->
            val primitive = item as? JsonPrimitive
            if (primitive == null || !primitive.isString) {
                throw BridgeException(WorkshopBridgeErrors.INVALID_PARAMS, "$field must be an array of strings")
            }
            primitive.content.trim()
        }.filter { it.isNotEmpty() }
    }

    private fun requiredText(params: JsonObject, field: String): String? {
        val element = params[field] ?: return null
        return textOf(element, field)
    }

    private fun optionalText(params: JsonObject, field: String): String? {
        val element = params[field] ?: return null
        return textOf(element, field)
    }

    private fun textOf(element: JsonElement, field: String): String? {
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            throw BridgeException(WorkshopBridgeErrors.INVALID_PARAMS, "$field must be a string")
        }
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }

    private suspend fun <T> agentCall(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw BridgeException(
            WorkshopBridgeErrors.INVALID_PARAMS,
            error.message ?: "invalid workshop agent request",
        )
    } catch (error: Exception) {
        throw BridgeException(
            WorkshopBridgeErrors.AGENT_FAILED,
            error.message ?: "workshop agent failed",
        )
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
