package io.github.whywood.superpi.plugin.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

public object Cards {
    public const val KEY: String = "superpi.card"
    public const val RAW_KEY: String = "superpi.raw"
    public const val GUIDANCE_KEY: String = "superpi.guidance"
    public const val DISPLAY_KEY: String = "superpi.display"

    private const val RAW_HEADING = "完整返回（上游原样）："
    private const val GUIDANCE_HEADING = "使用说明："
    private const val INSTRUCTION_PREFIX = "展示这张卡片："

    public fun descriptor(customType: String?, title: String?, payload: JsonObject?): JsonObject =
        buildJsonObject {
            customType?.takeIf { it.isNotBlank() }?.let { put("customType", it) }
            title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
            payload?.let { put("payload", it) }
        }

    public fun instruction(callId: String, title: String?): String {
        val label = title?.trim()?.takeIf { it.isNotEmpty() }?.let { "（$it）" }.orEmpty()
        return "展示这张卡片：present({\"items\":[{\"type\":\"card\",\"id\":\"$callId\"}]})$label"
    }

    public fun deliver(callId: String, result: PluginToolResult): PluginToolResult {
        val details = result.details ?: return result
        val card = details[KEY] as? JsonObject
        val raw = details.text(RAW_KEY)
        val guidance = details.text(GUIDANCE_KEY)
        if (card == null && raw == null && guidance == null) return result
        val title = card?.text("title")
        val existing = result.text.orEmpty()
        val body = buildList {
            existing.trim().takeIf { it.isNotEmpty() }?.let(::add)
            raw?.let { add("$RAW_HEADING\n$it") }
            guidance?.let { add("$GUIDANCE_HEADING\n$it") }
            if (card != null && !result.isError && !existing.contains(INSTRUCTION_PREFIX)) {
                add(instruction(callId, title))
            }
        }.joinToString("\n\n")
        val display = title
            ?: result.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: guidance
        val removed = buildSet {
            add(RAW_KEY)
            add(GUIDANCE_KEY)
            if (result.isError) add(KEY)
        }
        val cleaned = JsonObject(
            details - removed + buildMap {
                display?.let { put(DISPLAY_KEY, JsonPrimitive(it)) }
            },
        )
        if (result.blocks.isEmpty()) return textResult(body, cleaned, result.isError)
        val blocks = result.blocks + body.takeIf { it.isNotBlank() }?.let { listOf(textBlock(it)) }.orEmpty()
        return richResult(blocks, cleaned, result.isError)
    }

    private fun JsonObject.text(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
