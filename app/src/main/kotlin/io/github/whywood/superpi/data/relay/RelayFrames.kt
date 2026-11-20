package io.github.whywood.superpi.data.relay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class RelayBot(
    val id: String,
    val name: String,
)

data class RelayDevice(
    val deviceId: String,
    val name: String,
    val platform: String,
    val online: Boolean,
    val lastSeen: Long,
    val bots: List<RelayBot>,
)

sealed interface RelayServerFrame {
    data class HelloOk(
        val accountId: String,
        val devices: List<RelayDevice>,
        val memoryVersion: Long,
    ) : RelayServerFrame

    data class Presence(
        val deviceId: String,
        val online: Boolean,
        val lastSeen: Long,
        val bots: List<RelayBot>?,
    ) : RelayServerFrame

    data class Route(
        val id: String,
        val from: String,
        val method: String,
        val params: JsonObject,
    ) : RelayServerFrame

    data class RouteOk(
        val id: String,
        val result: JsonObject?,
    ) : RelayServerFrame

    data class RouteError(
        val id: String,
        val code: String,
        val message: String,
    ) : RelayServerFrame

    data class RouteEvent(
        val id: String,
        val seq: Long,
        val event: JsonObject,
    ) : RelayServerFrame

    data class MemoryChanged(val version: Long) : RelayServerFrame

    data object Pong : RelayServerFrame

    data class Unknown(val type: String) : RelayServerFrame
}

internal object RelayFrames {
    private const val PROTOCOL_VERSION = 1

    fun hello(
        deviceId: String,
        platform: String,
        appVersion: String,
        bots: List<RelayBot>,
    ): JsonObject = buildJsonObject {
        put("type", "hello")
        put("protocolVersion", PROTOCOL_VERSION)
        put("deviceId", deviceId)
        put("platform", platform)
        put("appVersion", appVersion)
        put("bots", botsArray(bots))
        put("capabilities", JsonArray(emptyList()))
    }

    fun route(id: String, to: String, method: String, params: JsonObject): JsonObject = buildJsonObject {
        put("type", "route")
        put("id", id)
        put("to", to)
        put("method", method)
        put("params", params)
    }

    fun routeOk(id: String, result: JsonObject): JsonObject = buildJsonObject {
        put("type", "route_ok")
        put("id", id)
        put("result", result)
    }

    fun routeError(id: String, code: String, message: String): JsonObject = buildJsonObject {
        put("type", "route_error")
        put("id", id)
        put("code", code)
        put("message", message)
    }

    fun routeEvent(id: String, seq: Long, event: JsonObject): JsonObject = buildJsonObject {
        put("type", "route_event")
        put("id", id)
        put("seq", seq)
        put("event", event)
    }

    fun ping(): JsonObject = buildJsonObject { put("type", "ping") }

    fun parse(raw: String): RelayServerFrame? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        return when (json.string("type")) {
            "hello_ok" -> RelayServerFrame.HelloOk(
                accountId = json.string("accountId").orEmpty(),
                devices = json["devices"].let(::parseDevices),
                memoryVersion = json["memoryVersion"]?.jsonPrimitive?.longOrNull ?: 0L,
            )

            "presence" -> RelayServerFrame.Presence(
                deviceId = json.string("deviceId") ?: return null,
                online = json["online"]?.jsonPrimitive?.booleanOrNull ?: false,
                lastSeen = json["lastSeen"]?.jsonPrimitive?.longOrNull ?: 0L,
                bots = json["bots"]?.let(::parseBots),
            )

            "route" -> RelayServerFrame.Route(
                id = json.string("id") ?: return null,
                from = json.string("from").orEmpty(),
                method = json.string("method") ?: return null,
                params = json["params"] as? JsonObject ?: JsonObject(emptyMap()),
            )

            "route_ok" -> RelayServerFrame.RouteOk(
                id = json.string("id") ?: return null,
                result = json["result"] as? JsonObject,
            )

            "route_error" -> RelayServerFrame.RouteError(
                id = json.string("id") ?: return null,
                code = json.string("code").orEmpty(),
                message = json.string("message").orEmpty(),
            )

            "route_event" -> RelayServerFrame.RouteEvent(
                id = json.string("id") ?: return null,
                seq = json["seq"]?.jsonPrimitive?.longOrNull ?: 0L,
                event = json["event"] as? JsonObject ?: return null,
            )

            "memory_changed" -> RelayServerFrame.MemoryChanged(
                version = json["version"]?.jsonPrimitive?.longOrNull ?: 0L,
            )

            "pong" -> RelayServerFrame.Pong
            else -> RelayServerFrame.Unknown(json.string("type").orEmpty())
        }
    }

    private fun parseDevices(element: JsonElement?): List<RelayDevice> {
        if (element !is JsonArray) return emptyList()
        return element.mapNotNull { item ->
            val json = item as? JsonObject ?: return@mapNotNull null
            val deviceId = json.string("deviceId") ?: return@mapNotNull null
            RelayDevice(
                deviceId = deviceId,
                name = json.string("name") ?: deviceId,
                platform = json.string("platform").orEmpty(),
                online = json["online"]?.jsonPrimitive?.booleanOrNull ?: false,
                lastSeen = json["lastSeen"]?.jsonPrimitive?.longOrNull ?: 0L,
                bots = json["bots"]?.let(::parseBots).orEmpty(),
            )
        }
    }

    private fun parseBots(element: JsonElement?): List<RelayBot> {
        if (element !is JsonArray) return emptyList()
        return element.mapNotNull { item ->
            val json = item as? JsonObject ?: return@mapNotNull null
            val id = json.string("id") ?: return@mapNotNull null
            RelayBot(id = id, name = json.string("name") ?: id)
        }
    }

    private fun botsArray(bots: List<RelayBot>): JsonArray = JsonArray(
        bots.map { bot ->
            buildJsonObject {
                put("id", bot.id)
                put("name", bot.name)
            }
        },
    )

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }

    fun intOrNull(element: JsonElement?): Int? =
        element?.jsonPrimitive?.intOrNull
}
