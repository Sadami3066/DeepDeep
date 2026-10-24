package io.github.whywood.deepdeep.runtime

import dev.pi.ai.PiJson
import dev.pi.mcp.config.McpServerConfig
import dev.pi.mcp.config.parseMcpConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class McpParsedEntry(
    val name: String,
    val config: McpServerConfig,
)

fun parseMcpEntries(text: String): List<McpParsedEntry> {
    val trimmed = text.trim().removePrefix("\uFEFF")
    if (trimmed.isEmpty()) throw IllegalArgumentException("请粘贴 MCP JSON")
    val servers = extractServers(trimmed)
    if (servers.isEmpty()) throw IllegalArgumentException("没有解析到 MCP 服务器")
    val normalized = JsonObject(servers.mapValues { (_, value) -> normalizeType(value) })
    val parsed = parseMcpConfig(buildJsonObject { put("mcpServers", normalized) })
    if (parsed.errors.isNotEmpty()) {
        throw IllegalArgumentException("MCP 配置不正确，请检查 type、url、headers")
    }
    return parsed.servers.map { (name, config) -> McpParsedEntry(name, config) }
}

fun formatMcpEntry(name: String, entry: JsonObject): String {
    val pretty = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), entry)
    val lines = pretty.lines()
    if (lines.size <= 2) return "${JsonPrimitive(name)}: $pretty"
    val body = lines.drop(1).dropLast(1)
    return buildString {
        append(JsonPrimitive(name)).append(": {\n")
        body.forEach { line ->
            append("  ").append(line).append('\n')
        }
        append('}')
    }
}

private fun extractServers(text: String): Map<String, JsonObject> {
    val root = runCatching { PiJson.parseToJsonElement(text).jsonObject }.getOrNull()
    if (root != null) {
        val wrapped = root["mcpServers"] as? JsonObject
        if (wrapped != null) return wrapped.objects()
        if (root.isNotEmpty() && root.values.all { it is JsonObject }) {
            return root.objects()
        }
    }
    return extractFragment(text)?.let { mapOf(it) } ?: emptyMap()
}

private fun extractFragment(text: String): Pair<String, JsonObject>? {
    val nameMatch = Regex("""^\s*("(?:\\.|[^"\\])*")\s*:""").find(text) ?: return null
    val name = runCatching {
        (PiJson.parseToJsonElement(nameMatch.groupValues[1]) as? JsonPrimitive)?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val open = text.indexOf('{', nameMatch.range.last + 1)
    if (open < 0) return null
    val body = balancedObject(text, open) ?: return null
    val entry = runCatching { PiJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    return name to entry
}

private fun balancedObject(text: String, start: Int): String? {
    var depth = 0
    var inString = false
    var escaped = false
    for (index in start until text.length) {
        val char = text[index]
        if (inString) {
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return text.substring(start, index + 1)
            }
        }
    }
    return null
}

private fun JsonObject.objects(): Map<String, JsonObject> {
    val result = LinkedHashMap<String, JsonObject>()
    forEach { (name, value) ->
        (value as? JsonObject)?.let { result[name] = it }
    }
    return result
}

private fun normalizeType(value: JsonObject): JsonObject {
    val type = (value["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return value
    val normalized = when (type.trim().lowercase().replace("_", "").replace("-", "")) {
        "streamablehttp", "http" -> "streamable-http"
        else -> return value
    }
    return JsonObject(value + ("type" to JsonPrimitive(normalized)))
}
