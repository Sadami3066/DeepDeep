package io.github.whywood.deepdeep.runtime

import com.wangzan101.mika.core.agent.AnswerReview
import com.wangzan101.mika.core.agent.BeforeAccept
import com.wangzan101.mika.core.observation.InputHints
import com.wangzan101.mika.core.observation.ObsKind
import com.wangzan101.mika.core.observation.Observation
import com.wangzan101.mika.core.observation.Place
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.verify.AnswerSource
import com.wangzan101.mika.core.verify.Evidence
import com.wangzan101.mika.core.verify.EvidenceLedger
import io.github.whywood.deepdeep.sdk.agent.Agent
import io.github.whywood.deepdeep.sdk.agent.AgentContext
import io.github.whywood.deepdeep.sdk.agent.AgentEvent
import io.github.whywood.deepdeep.sdk.agent.AgentRequestUpdate
import io.github.whywood.deepdeep.sdk.agent.AgentTurnDecision
import io.github.whywood.deepdeep.sdk.agent.StandardAgentMessage
import io.github.whywood.deepdeep.sdk.agent.asAssistantMessage
import io.github.whywood.deepdeep.sdk.agent.asLlmMessage
import io.github.whywood.deepdeep.sdk.ai.AssistantMessage
import io.github.whywood.deepdeep.sdk.ai.BlockContent
import io.github.whywood.deepdeep.sdk.ai.PlainTextContent
import io.github.whywood.deepdeep.sdk.ai.StopReason
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.ai.ToolResultMessage
import io.github.whywood.deepdeep.sdk.ai.ToolCall
import io.github.whywood.deepdeep.sdk.ai.UserMessage
import io.github.whywood.deepdeep.sdk.coding.session.AgentSession
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal fun installPhongentAnswerReview(session: AgentSession) {
    installPhongentAnswerReview(session.agent) { timestamp, report ->
        try {
            session.sessionManager.appendCustomEntry(
                "phongent.screen_review",
                JsonObject(report + ("assistantTimestamp" to JsonPrimitive(timestamp))),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logWarn("Phongent", "could not save GUI answer review", error)
        }
    }
}

internal fun installPhongentAnswerReview(agent: Agent, onReview: suspend (Long, JsonObject) -> Unit) {
    val review = PhongentGuiAnswerReview()
    var visibleReads = emptySet<String>()
    var notice: StandardAgentMessage? = null
    var conclusion: Pair<AssistantMessage, JsonObject>? = null

    agent.installToolExecutionHooks(review)
    agent.installToolExecutionHooks(object : ToolExecutionHooks {
        override suspend fun beforeToolCall(context: ToolCallContext): ToolCallDecision {
            if (context.toolName !in PHONGENT_INLINE_TOOLS) return ToolCallDecision.Continue
            return ToolCallDecision.Rewrite(JsonObject(context.args + (
                PHONGENT_VISIBLE_READS to JsonArray(visibleReads.map(::JsonPrimitive))
            )))
        }
    })
    val prepareRequest = agent.prepareRequest
    agent.prepareRequest = { request, signal ->
        val update = prepareRequest?.invoke(request, signal)
        visibleReads = (update?.context ?: request.context).messages.asSequence()
            .mapNotNull { it.asLlmMessage() as? ToolResultMessage }
            .flatMap { it.content.asSequence().filterIsInstance<TextContent>() }
            .mapNotNull { text ->
                val obj = runCatching { Json.parseToJsonElement(text.text) as? JsonObject }.getOrNull()
                    ?: return@mapNotNull null
                val screen = (obj["screen"] as? JsonObject)
                    ?: obj.takeIf { it["elements"] is JsonArray || it["added"] is JsonArray && "base" in it }
                (screen?.get("snapshotId") as? JsonPrimitive)?.contentOrNull
            }.toSet()
        val pending = notice
        notice = null
        if (pending == null) update else {
            val context = update?.context ?: request.context
            AgentRequestUpdate(
                context = AgentContext(messages = context.messages + pending, tools = context.tools),
                model = update?.model,
                thinkingLevel = update?.thinkingLevel,
            )
        }
    }

    val finishTurn = agent.finishTurn
    agent.finishTurn = finish@{ turn, signal ->
        val decision = finishTurn?.invoke(turn, signal)
        val message = turn.message
        if (decision != null || message.stopReason != StopReason.Stop ||
            turn.toolResults.isNotEmpty() || message.content.any { it is ToolCall } ||
            agent.hasQueuedMessages() || !review.acted
        ) return@finish decision

        val answer = message.content.filterIsInstance<TextContent>().joinToString("") { it.text }.trim()
        if (answer.isEmpty()) return@finish decision
        when (val before = review.beforeAccept()) {
            is BeforeAccept.Continue -> {
                notice = StandardAgentMessage(UserMessage(PlainTextContent(before.note), System.currentTimeMillis()))
                AgentTurnDecision.Continue
            }
            BeforeAccept.Accept -> {
                val user = turn.context.messages.asReversed().firstNotNullOfOrNull { entry ->
                    val input = entry.asLlmMessage() as? UserMessage ?: return@firstNotNullOfOrNull null
                    when (val content = input.content) {
                        is PlainTextContent -> content.text
                        is BlockContent -> content.blocks.filterIsInstance<TextContent>().joinToString("") { it.text }
                    }.takeUnless { it.startsWith(OPERATION_RESULT_NOTICE_PREFIX) }
                }
                val sources = review.sourcesOnly(user.orEmpty(), answer)
                conclusion = message to review.report(sources)
                decision
            }
        }
    }

    agent.subscribe { event, _ ->
        when (event) {
            is AgentEvent.AgentStart -> {
                review.beginRun()
                notice = null
                conclusion = null
            }
            is AgentEvent.AgentEnd -> {
                val accepted = conclusion
                conclusion = null
                notice = null
                if (accepted != null && event.messages.lastOrNull()?.asAssistantMessage() == accepted.first) {
                    onReview(accepted.first.timestamp, accepted.second)
                }
            }
            else -> Unit
        }
    }
}

const val PHONGENT_VISIBLE_READS = "_phongentVisibleReads"

private val PHONGENT_INLINE_TOOLS = setOf(
    "screen_tap", "screen_swipe", "screen_scroll", "screen_type", "screen_key", "screen_launch",
)

/** Host screen-tool results supply the observations to Phongent's answer-review port. */
internal class PhongentGuiAnswerReview(private val clock: () -> Long = System::currentTimeMillis) : ToolExecutionHooks {
    private val lock = Any()
    private var ledger = EvidenceLedger()
    private var actionMark: Pair<String, Int>? = null
    private val latestReads = mutableMapOf<String, Int>()
    private val previousScreens = mutableMapOf<String, Pair<String, List<JsonObject>>>()
    private var step = 0
    private var resultReadNudged = false
    @Volatile var acted = false
        private set

    private val answerReview = AnswerReview(
        evidence = { _, _ -> synchronized(lock) { Evidence(emptyList(), null, ledger.copy(), emptyList(), false) } },
        verifier = null,
        emit = {},
        now = clock,
    )

    fun beginRun() = synchronized(lock) {
        ledger = EvidenceLedger()
        actionMark = null
        latestReads.clear()
        previousScreens.clear()
        step = 0
        resultReadNudged = false
        acted = false
    }

    // AgentLoop checks this once before accepting a production final answer.
    fun beforeAccept(): BeforeAccept = synchronized(lock) {
        if (!unreadChange() || resultReadNudged) return BeforeAccept.Accept
        resultReadNudged = true
        return BeforeAccept.Continue(OPERATION_RESULT_NOTICE)
    }

    suspend fun sourcesOnly(goal: String, answer: String): List<AnswerSource> =
        answerReview.sourcesOnly(goal, answer, emptyMap())

    fun report(sources: List<AnswerSource>): JsonObject = synchronized(lock) {
        buildJsonObject {
            put("reviewed", false)
            put("verified", false)
            put("resultReadNudged", resultReadNudged)
            put("unreadChange", unreadChange())
            put("sources", buildJsonArray {
                for (source in sources) add(buildJsonObject {
                    put("obsId", source.obsId)
                    put("kind", source.kind)
                    put("place", source.place)
                    put("title", source.title)
                    put("step", source.step)
                    put("source", source.source)
                })
            })
        }
    }

    override suspend fun afterToolResult(context: ToolResultContext): ToolResultDecision = synchronized(lock) {
        val deferred = context.toolName == "tool_execute"
        val tool = if (deferred) context.args.string("name").orEmpty() else context.toolName
        if (tool !in SCREEN_WRITES && tool !in SCREEN_READS && tool != "do_steps" && tool != "screen_act") {
            return ToolResultDecision.Keep
        }
        val args = if (deferred) context.args["arguments"] as? JsonObject else context.args
        val payload = context.result.structuredContent as? JsonObject
            ?: context.result.content.filterIsInstance<TextContent>().firstNotNullOfOrNull {
                parseResult(it.text)?.takeIf { parsed -> tool != "screen_act" || "host_actions" in parsed }
            }
            ?: return ToolResultDecision.Keep
        val target = payload.string("target") ?: args?.string("target") ?: "virtual"
        acted = true
        step++
        when (tool) {
            in SCREEN_READS -> if (context.result.isError != true && payload["ok"] != NO) remember(target, payload)
            "do_steps" -> {
                for (entry in payload.objects("steps")) {
                    when (val child = entry.string("tool")) {
                        in SCREEN_WRITES -> recordWrite(target, SCREEN_WRITES[child], entry, entry["ok"] == NO)
                        in SCREEN_READS -> if (entry["ok"] != NO) (entry["screen"] as? JsonObject)?.let { remember(target, it) }
                    }
                }
                (payload["screen"] as? JsonObject)?.let { remember(target, it) }
            }
            "screen_act" -> {
                for (entry in payload.objects("host_actions")) {
                    val verb = screenVerbsByName[entry.string("verb")]
                    recordWrite(entry.string("target") ?: target, verb, entry, entry["ok"] != YES)
                }
                (payload["screen"] as? JsonObject)?.let { remember(target, it) }
            }
            else -> {
                if (tool == "screen_type" && payload["ok"] == YES && payload["secret"] != YES) {
                    payload.string("text")?.let(ledger::noteTyped)
                }
                val verb = if (tool == "screen_key" && args?.string("key") == "back") Verb.Back else SCREEN_WRITES[tool]
                recordWrite(target, verb, payload, context.result.isError == true)
            }
        }
        return ToolResultDecision.Keep
    }

    private fun unreadChange(): Boolean = actionMark?.let { (target, id) ->
        ledger.readSince(id) && (latestReads[target] ?: 0) > id
    } == false

    private fun recordWrite(target: String, verb: Verb?, result: JsonObject, error: Boolean) {
        if (result["executed"] == NO) return
        val failed = (error || result["ok"] == NO) && result["changed"] != YES
        if (verb in CHANGE_VERBS && !failed) actionMark = target to ledger.lastId
        (result["screen"] as? JsonObject)?.let { remember(target, it) }
    }

    private fun remember(target: String, payload: JsonObject) {
        if (payload["protected"] == YES || payload["readable"] == NO || payload["screenChangedDuringRead"] == YES) return
        val nodes = screenNodes(target, payload)
        val names = nodes.filter { node ->
            node["secret"] != YES && node["editable"] != YES &&
                node.string("role")?.substringAfterLast('.')?.lowercase() !in EDITABLE_ROLES
        }.mapNotNull { node -> node.string("name") ?: node.string("text") ?: node.string("desc") }
            .map { it.replace('\n', ' ').trim() }.filter(String::isNotBlank)
        val title = payload.string("title")
        val pkg = payload.string("currentApp") ?: payload.string("package")
        val visible = payload.objects("visibleText").mapNotNull { it.string("text") }
        if (names.isEmpty() && visible.isEmpty() && title.isNullOrBlank()) return
        val text = (listOfNotNull(pkg?.let { "当前 App:$it" }, title) + names + visible).joinToString("\n")
        val read = Observation(ObsKind.Read, text, source = "phone", place = pkg?.let(Place::app),
            title = title, inputs = InputHints(emptyList()))
        ledger.add(read, step, clock())?.let { latestReads[target] = it.id }
    }

    private fun screenNodes(target: String, payload: JsonObject): List<JsonObject> {
        val snapshot = payload.string("snapshotId") ?: return payload.objects("nodes")
        val nodes = if (payload["full"] == YES) payload.objects("elements") else {
            val prior = previousScreens[target]?.takeIf { it.first == payload.string("base") }?.second.orEmpty()
            val byRef = prior.associateByTo(linkedMapOf()) { it.string("ref") }
            payload.objects("removed").forEach { byRef.remove(it.string("ref")) }
            (payload.objects("changed") + payload.objects("added")).forEach { byRef[it.string("ref")] = it }
            byRef.values.toList()
        }
        if (payload["truncated"] == YES || payload["complete"] == NO) previousScreens.remove(target)
        else previousScreens[target] = snapshot to nodes
        return nodes
    }

    private companion object {
        val YES = JsonPrimitive(true)
        val NO = JsonPrimitive(false)
        val SCREEN_READS = setOf("screen_observe", "screen_probe")
        val SCREEN_WRITES = mapOf(
            "screen_tap" to Verb.Tap,
            "screen_swipe" to Verb.Swipe,
            "screen_scroll" to Verb.Scroll,
            "screen_type" to Verb.TypeText,
            "screen_key" to Verb.PressKey,
            "screen_launch" to Verb.LaunchApp,
        )
        val screenVerbsByName = Verb.entries.associateBy(Verb::name)
        // Extracted from Phongent ClassifiedRisk.changesState.
        val CHANGE_VERBS = setOf(Verb.Tap, Verb.TypeText, Verb.ClearText, Verb.PressKey, Verb.Upload, Verb.Interact)
        val EDITABLE_ROLES = setOf("textbox", "text_field", "edit", "edittext", "autocompletetextview", "multiautocompletetextview")
        fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
        fun parseResult(text: String): JsonObject? =
            runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }
}

private const val OPERATION_RESULT_NOTICE_PREFIX = "<context kind=\"notice\" source=\"operation_result\" trust=\"a note from Mika to you, not words from the user\">"
private const val OPERATION_RESULT_NOTICE = OPERATION_RESULT_NOTICE_PREFIX + "\nThe result of the last action has not been read yet. Look at the current screen first to confirm the actual state, then answer; if it cannot be read, say so plainly.\n</context>"
