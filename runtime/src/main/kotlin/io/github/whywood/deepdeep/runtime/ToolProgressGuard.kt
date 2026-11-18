package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.Content
import io.github.whywood.deepdeep.sdk.ai.ImageContent
import io.github.whywood.deepdeep.sdk.ai.TextContent
import java.security.MessageDigest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.Json

internal class ToolProgressGuard(
    private val retryLimit: Int = 2,
    private val rememberedImages: Int = 16,
    private val rememberedCalls: Int = 64,
) : ToolExecutionHooks {
    private data class PendingCall(
        val signature: String,
        val strategy: String?,
    )

    private data class ResultHistory(
        val stateEpoch: Int,
        val fingerprint: String,
        val repeats: Int,
    )

    private val lock = Any()
    private var phongent = PhongentScreenGuard()
    private val pendingCalls = mutableMapOf<String, PendingCall>()
    private val resultHistory = linkedMapOf<String, ResultHistory>()
    private val strategyAttempts = mutableMapOf<String, Int>()
    private val failedStrategyAttempts = mutableMapOf<String, Int>()
    private val imageFingerprints = linkedSetOf<String>()
    private val observedStateTrail = ArrayDeque<String>()
    private var stateEpoch = 0
    private var lastObservationFingerprint: String? = null
    private var actionsSinceObservation = 0
    private var stagnantObservations = 0
    private var stateCycleDetected = false
    private var lastActionStrategy: String? = null
    private var cycleStrategy: String? = null

    fun beginRun() = synchronized(lock) {
        phongent = PhongentScreenGuard()
        pendingCalls.clear()
        resultHistory.clear()
        strategyAttempts.clear()
        failedStrategyAttempts.clear()
        imageFingerprints.clear()
        observedStateTrail.clear()
        stateEpoch = 0
        lastObservationFingerprint = null
        actionsSinceObservation = 0
        stagnantObservations = 0
        stateCycleDetected = false
        lastActionStrategy = null
        cycleStrategy = null
    }

    fun checkRunCompleted() = synchronized(lock) {
        phongent.stop?.let { throw ScreenGuardStopped(it) }
    }

    override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision = synchronized(lock) {
        phongent.stop?.let { return@synchronized ToolCallDecision.Block("Screen task stopped: ${it.code}") }
        val signature = callSignature(context.toolName, context.args)
        val actionStrategy = actionStrategy(context.toolName, context.args)
        val progressStrategy = actionStrategy ?: waitStrategy(context.toolName, context.args)
        val history = resultHistory[signature]
        if (progressStrategy != null && history?.stateEpoch == stateEpoch && history.repeats >= retryLimit) {
            return@synchronized ToolCallDecision.Block(
                "No progress after $retryLimit equivalent attempts. Observe the current state and use a different locator, wait condition, or action strategy.",
            )
        }
        if (progressStrategy != null && failedStrategyAttempts.getOrDefault(progressStrategy, 0) >= retryLimit) {
            return@synchronized ToolCallDecision.Block(
                "The $progressStrategy strategy failed $retryLimit times without a verified state change. Observe the screen and choose a different strategy.",
            )
        }
        if (
            actionStrategy != null &&
            (
                (stagnantObservations >= retryLimit && strategyAttempts.getOrDefault(actionStrategy, 0) >= retryLimit) ||
                    (stateCycleDetected && actionStrategy == cycleStrategy)
                )
        ) {
            return@synchronized ToolCallDecision.Block(
                if (stateCycleDetected) {
                    "A repeating screen-state cycle was detected after $actionStrategy. Do not repeat this strategy; choose a different transition or report the blocker."
                } else {
                    "The screen remained unchanged after $retryLimit $actionStrategy attempts. Stop coordinate guessing and switch to a structured locator or report the blocker."
                },
            )
        }
        pendingCalls[context.callId] = PendingCall(signature, progressStrategy)
        if (actionStrategy != null) {
            strategyAttempts[actionStrategy] = strategyAttempts.getOrDefault(actionStrategy, 0) + 1
            actionsSinceObservation += 1
            lastActionStrategy = actionStrategy
        }
        ToolCallDecision.Continue
    }

    override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision = synchronized(lock) {
        val pending = pendingCalls.remove(context.callId)
        val signature = pending?.signature ?: callSignature(context.toolName, context.args)
        val resultFingerprint = resultFingerprint(context.result)
        val effect = resultJson(context.result)?.get("effect") as? JsonObject
        val madeProgress = effect?.string("kind") in setOf("done", "place_changed")
        val previous = resultHistory[signature]
        val repeats = if (previous?.stateEpoch == stateEpoch && previous.fingerprint == resultFingerprint) {
            previous.repeats + 1
        } else {
            1
        }
        if (madeProgress) {
            resultHistory.remove(signature)
            pending?.strategy?.let { failedStrategyAttempts.remove(it) }
        } else {
            resultHistory[signature] = ResultHistory(stateEpoch, resultFingerprint, repeats)
        }
        while (resultHistory.size > rememberedCalls) {
            resultHistory.remove(resultHistory.keys.first())
        }

        if (pending?.strategy != null && resultIndicatesFailure(context.result)) {
            failedStrategyAttempts[pending.strategy] = failedStrategyAttempts.getOrDefault(pending.strategy, 0) + 1
        }

        val observation = isStateObservation(context.toolName, context.args)
        var noProgressWarning = false
        if (observation) {
            val previousObservation = lastObservationFingerprint
            if (previousObservation != null && previousObservation == resultFingerprint && actionsSinceObservation > 0) {
                stagnantObservations += 1
                noProgressWarning = stagnantObservations >= retryLimit
            } else if (previousObservation != null && previousObservation != resultFingerprint) {
                stateEpoch += 1
                stagnantObservations = 0
                strategyAttempts.clear()
                failedStrategyAttempts.clear()
                resultHistory.clear()
            }
            if (actionsSinceObservation > 0 || previousObservation == null) {
                observedStateTrail.addLast(resultFingerprint)
                while (observedStateTrail.size > 6) observedStateTrail.removeFirst()
                stateCycleDetected = hasRepeatingStateCycle(observedStateTrail)
                cycleStrategy = if (stateCycleDetected) lastActionStrategy else null
            }
            lastObservationFingerprint = resultFingerprint
            actionsSinceObservation = 0
        }

        val verdicts = phongent.record(context)
        val compact = suppressRepeatedImages(context.result)
        val transformed = if (verdicts.isEmpty()) compact else {
            AgentToolResult(
                content = compact.content + verdicts.map { TextContent(it.toJson().toString()) },
                details = compact.details,
                structuredContent = compact.structuredContent,
                usage = compact.usage,
                isError = compact.isError,
                terminate = if (phongent.stop != null) true else compact.terminate,
            )
        }
        val withWarning = if (noProgressWarning) {
            transformed.withAdditionalText(
                "No observable screen change after $retryLimit action/observation cycles. Do not repeat the same coordinate strategy; use resourceId, text, description, or return a blocker.",
            )
        } else {
            transformed
        }
        if (withWarning == context.result) ToolResultDecision.Keep else ToolResultDecision.Replace(withWarning)
    }

    private fun hasRepeatingStateCycle(states: Collection<String>): Boolean {
        if (states.size < 4) return false
        val tail = states.toList().takeLast(4)
        return tail[0] == tail[2] && tail[1] == tail[3] && tail[0] != tail[1]
    }

    private fun resultIndicatesFailure(result: AgentToolResult): Boolean {
        if (result.isError == true) return true
        val json = resultJson(result) ?: return false
        return (json["ok"] as? JsonPrimitive)?.booleanOrNull == false ||
            (json["matched"] as? JsonPrimitive)?.booleanOrNull == false ||
            (json["effect"] as? JsonObject)?.string("kind") == "no_effect" ||
            json.string("reason") in setOf("timeout", "no_effect", "stale_state")
    }

    private fun resultJson(result: AgentToolResult): JsonObject? =
        (result.structuredContent as? JsonObject) ?: result.content.filterIsInstance<TextContent>()
            .firstNotNullOfOrNull { runCatching { Json.parseToJsonElement(it.text) as? JsonObject }.getOrNull() }

    private fun suppressRepeatedImages(result: AgentToolResult): AgentToolResult {
        var changed = false
        val content = result.content.flatMap { block ->
            if (block !is ImageContent) return@flatMap listOf(block)
            val fingerprint = digest("${block.mimeType}:${block.data}")
            if (fingerprint in imageFingerprints) {
                changed = true
                listOf(TextContent("Repeated image omitted because its pixels were already provided in this session."))
            } else {
                rememberImage(fingerprint)
                listOf(block)
            }
        }
        return if (changed) result.withContent(content) else result
    }

    private fun rememberImage(fingerprint: String) {
        imageFingerprints += fingerprint
        while (imageFingerprints.size > rememberedImages) {
            imageFingerprints.remove(imageFingerprints.first())
        }
    }

    private fun AgentToolResult.withAdditionalText(text: String): AgentToolResult =
        withContent(content + TextContent(text))

    private fun AgentToolResult.withContent(content: List<Content>): AgentToolResult = AgentToolResult(
        content = content,
        details = details,
        structuredContent = structuredContent,
        usage = usage,
        isError = isError,
        terminate = terminate,
    )

    private fun callSignature(toolName: String, args: JsonObject): String =
        "$toolName:${digest(canonicalJson(args))}"

    private fun resultFingerprint(result: AgentToolResult): String = digest(
        buildString {
            append(result.isError)
            append('|')
            result.content.forEach { content ->
                when (content) {
                    is TextContent -> append(normalizeResultText(content.text))
                    is ImageContent -> append("image:").append(content.mimeType).append(':').append(digest(content.data))
                    else -> append(content.toString())
                }
                append('|')
            }
            result.structuredContent?.let { append(canonicalJson(it)) }
        },
    )

    private fun actionStrategy(toolName: String, args: JsonObject): String? = when (toolName) {
        "screen_tap" -> if (args.hasStructuredLocator()) "screen_tap:locator" else "screen_tap:coordinates"
        "screen_act" -> "screen_act"
        "screen_type" -> "screen_type"
        "screen_key" -> "screen_key"
        "screen_swipe" -> "screen_swipe"
        "screen_scroll" -> "screen_scroll"
        "screen_launch" -> "screen_launch"
        "system_shell" -> args.string("command")
            ?.trim()
            ?.takeIf { it.startsWith("input ") }
            ?.substringAfter("input ")
            ?.substringBefore(' ')
            ?.let { "system_shell:input-$it" }
        else -> null
    }

    private fun waitStrategy(toolName: String, args: JsonObject): String? = when (toolName) {
        "screen_wait" -> "screen_wait:${digest(canonicalJson(args))}"
        else -> null
    }

    private fun isStateObservation(toolName: String, args: JsonObject): Boolean = when (toolName) {
        "screen_observe", "screen_probe" -> true
        "read" -> args.string("path")?.lowercase()?.let { path ->
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".webp") || path.endsWith(".gif")
        } == true
        else -> false
    }

    private fun JsonObject.hasStructuredLocator(): Boolean =
        listOf("ref", "text", "desc", "resourceId", "class").any { key -> !string(key).isNullOrBlank() }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun normalizeResultText(text: String): String = text
        .replace(Regex("\"ref\"\\s*:\\s*\"[^\"]+\""), "\"ref\":\"<ref>\"")
        .replace(Regex("\"path\"\\s*:\\s*\"[^\"]+\""), "\"path\":\"<path>\"")
        .replace(Regex("\"elapsedMs\"\\s*:\\s*\\d+"), "\"elapsedMs\":<elapsed>")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun canonicalJson(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key:${canonicalJson(value)}" }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]") { canonicalJson(it) }
        is JsonPrimitive -> element.toString()
        JsonNull -> "null"
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
