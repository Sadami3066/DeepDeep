package io.github.whywood.deepdeep.runtime

import com.wangzan101.mika.core.guard.GuardVerdict
import com.wangzan101.mika.core.guard.OperationGuards
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import io.github.whywood.deepdeep.sdk.ai.TextContent

internal class PhongentScreenGuard {
    private val screens = mutableMapOf<String, OperationGuards>()
    var stop: GuardVerdict? = null
        private set

    fun record(context: ToolResultContext): List<GuardVerdict> {
        val envelope = context.args
        val tool = if (context.toolName == "tool_execute") envelope.string("name") ?: return emptyList() else context.toolName
        val args = if (context.toolName == "tool_execute") envelope["arguments"] as? JsonObject ?: return emptyList() else envelope
        if (tool !in ACTIONS && tool !in OBSERVATIONS && tool != "screen_wait" && tool != "do_steps") return emptyList()
        val payload = context.result.structuredContent as? JsonObject
            ?: context.result.content.filterIsInstance<TextContent>().firstNotNullOfOrNull {
                runCatching { Json.parseToJsonElement(it.text) as? JsonObject }.getOrNull()
            }
            ?: buildJsonObject {
                put("ok", context.result.isError != true)
                if (context.result.isError == true) {
                    context.result.content.filterIsInstance<TextContent>().firstOrNull()?.let { put("error", it.text.take(300)) }
                }
            }
        val guard = screens.getOrPut(payload.string("target") ?: args.string("target") ?: "virtual") { OperationGuards() }
        val verdicts = buildList {
            val steps = (payload["host_actions"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
            if (tool == "do_steps") {
                for (step in (payload["steps"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()) {
                    if (step["dispatched"] != JsonPrimitive(true) || step["executed"] == JsonPrimitive(false)) continue
                    val childTool = step.string("tool") ?: continue
                    if (childTool !in ACTIONS && childTool != "screen_wait") continue
                    if (childTool in ACTIONS) {
                        guard.noteAction()
                        (step["effectDetail"] as? JsonObject)?.string("kind")?.let { addAll(guard.effect(it)) }
                    }
                    val toolResult = JsonObject(step + ("ok" to (step["toolOk"] ?: step["ok"] ?: JsonPrimitive(false))))
                    addAll(guard.result(childTool, toolResult))
                    if (guard.stop != null) break
                }
            } else if (tool == "screen_act" && steps.isNotEmpty()) {
                for (step in steps) {
                    if (step.string("verb") != "WaitFor") {
                        guard.noteAction()
                        (step["effect"] as? JsonObject)?.string("kind")?.let { addAll(guard.effect(it)) }
                    }
                    addAll(guard.result(tool, step))
                    if (guard.stop != null) break
                }
            } else if (tool in ACTIONS) {
                addAll(guard.beforeAction(tool, args))
                guard.noteAction()
                (payload["effect"] as? JsonObject)?.string("kind")?.let { addAll(guard.effect(it)) }
            }
            if (steps.isEmpty() && tool != "do_steps") addAll(guard.result(tool, payload))
            if (tool in OBSERVATIONS && context.result.isError != true) {
                ((payload["nodes"] ?: payload["elements"]) as? JsonArray)?.let { nodes ->
                    val elements = JsonArray(nodes.map { node ->
                        if (node is JsonObject) JsonObject(node - "ref") else node
                    })
                    val frame = fingerprint(JsonObject(mapOf("nodes" to elements) +
                        payload.filterKeys { it == "package" || it == "title" || it == "currentApp" }))
                    addAll(guard.frame(context.callId, frame))
                    val read = fingerprint(JsonObject(payload - "nodes" - "elements" - "tree" + ("nodes" to elements)))
                    addAll(guard.read(tool, guard.firstSeen(read), fingerprint = read))
                }
            }
        }
        stop = stop ?: guard.stop
        return verdicts
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun fingerprint(content: JsonObject): Long {
        val text = JsonObject(content - "snapshotId" - "guard" - "advice").toString()
        var h = -0x340d631b7bdddcdbL
        for (b in text.toByteArray(Charsets.UTF_8)) h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L
        return h
    }

    private companion object {
        val ACTIONS = setOf("screen_tap", "screen_swipe", "screen_scroll", "screen_type", "screen_key", "screen_launch", "screen_act")
        val OBSERVATIONS = setOf("screen_observe", "screen_probe")
    }
}

internal class ScreenGuardStopped(verdict: GuardVerdict) :
    IllegalStateException("Screen task stopped: ${verdict.code} (${verdict.evidence})")
