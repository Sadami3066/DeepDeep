package io.github.whywood.deepdeep.screen

import com.wangzan101.mika.core.prompt.Feedback
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.uistate.ActionEffect
import com.wangzan101.mika.core.uistate.EffectFeedback
import com.wangzan101.mika.core.uistate.EffectKind
import com.wangzan101.mika.core.uistate.EffectTuning
import com.wangzan101.mika.core.uistate.LimitNotice
import com.wangzan101.mika.core.uistate.PortFacts
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.TreeAdequacy
import com.wangzan101.mika.core.uistate.TreeQuality
import com.wangzan101.mika.core.uistate.UiElement
import com.wangzan101.mika.core.uistate.UiStates
import com.wangzan101.mika.core.uistate.UiTreeNode
import com.wangzan101.mika.core.uistate.UiWindowTree
import com.wangzan101.mika.core.uistate.normalize
import com.wangzan101.mika.core.visual.FrameDiff
import com.wangzan101.mika.core.visual.FrameGrid
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class ScreenEffectEvidence(
    private val effect: ActionEffect?,
    private val verb: Verb,
    private val limit: String?,
    val secret: Boolean,
    private val screen: JsonObject? = null,
) {
    fun fields(): JsonObject = buildJsonObject {
        val changed = effect != null && effect.kind != EffectKind.NoEffect
        if (effect != null) {
            put("effect", effect.toJson())
            put("changed", changed)
        }
        if (screen != null) put("screen", screen)
        if (changed && screen == null) put("interfaceStale", true)
        if (verb == Verb.Scroll && effect?.kind == EffectKind.NoEffect) put("atEnd", true)
        limit?.let { put("limitReached", it) }
        val hint = listOfNotNull(
            limit?.let { Feedback.format("effect.limit_reached", "text" to it) },
            effect?.let { EffectFeedback.hint(it, verb, portSwitched = false) }
                ?.replace("phone_type_text with that point (it checks the result itself)", "screen_type with a field locator once it is observable"),
            Feedback["effect.stale"].takeIf { changed && screen == null && effect.kind != EffectKind.SmallChange },
            "Action effect could not be observed. Observe the screen before the next decision.".takeIf { effect == null },
        )
        if (hint.isNotEmpty()) put("hint", hint.joinToString(" "))
    }
}

internal class PhongentActionReadback(
    val references: PhongentScreenReferences,
    val visibleReads: Set<String>?,
    val visibleBase: String?,
    val ocrEnabled: Boolean,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<PhongentActionReadback>
}

internal suspend fun <T> runPhongentEffect(
    controller: ScreenController,
    verb: Verb,
    selector: GuiSelector? = null,
    typed: String? = null,
    succeeded: (T) -> Boolean = { (it as? ScreenLaunchReport)?.let { report -> report.ok && report.landedOnExpectedDisplay } ?: true },
    action: suspend () -> T,
): Pair<T, ScreenEffectEvidence> {
    val inline = coroutineContext[PhongentActionReadback]
    var before: GuiSnapshot? = null
    var beforeGrid: FrameGrid? = null
    var size: ScreenDisplayInfo? = null
    withTimeoutOrNull(EffectTuning.PROBE_MS) {
        before = readEffectFact { controller.observe(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null).snapshot }
        size = readEffectFact { controller.effectDisplayInfo() }
        beforeGrid = readEffectFact { controller.effectFrameGrid() }
    }
    val baseline = before?.toEffectSnapshot(size)
    val selected = before?.let { snap -> selector?.let { GuiNodeTree.find(snap, it) } }
    val target = selected?.let { node -> baseline?.elements?.firstOrNull { it.ref == node.ref } }
        ?.takeUnless { selector is GuiSelector.Point }
    val region = target?.bounds ?: (selector as? GuiSelector.Point)?.let { point ->
        baseline?.let { snap ->
            val x = normalize(point.x, snap.width)
            val y = normalize(point.y, snap.height)
            val radius = EffectTuning.POINT_REGION
            listOf(x - radius, y - radius, x + radius, y + radius).map { it.coerceIn(0, 999) }
        }
    }
    val result = action()
    if (!succeeded(result)) return result to ScreenEffectEvidence(null, verb, null, false)
    var port = when (result) {
        is ScreenTextResult -> PortFacts(verified = result.verified)
        is ScreenLaunchReport -> PortFacts(onUserScreen = result.packageName.takeIf { result.landedOnMain })
        else -> PortFacts()
    }
    val secret = target?.secret == true || (result as? ScreenTextResult)?.secret == true
    suspend fun read() = readEffectFact { controller.observe(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null) }
    fun judge(after: ScreenSnapshot?, afterGrid: FrameGrid?) = ActionEffect.classify(
        verb, baseline, after, port, FrameDiff.compare(beforeGrid, afterGrid, region),
        target, typed, secret = secret,
    )
    var effect = judge(null, null)
    var limit: String? = null
    var observed: ScreenObserveResult? = null
    var afterDisplay: ScreenDisplayInfo? = null
    withTimeoutOrNull(EffectTuning.BUDGET_MS) {
        val settled = readEffectFact { controller.settleForEffect(EffectTuning.SETTLE_MS) }
        if (settled == false) port = port.copy(loading = true)
        observed = read()
        afterDisplay = readEffectFact { controller.effectDisplayInfo() }
        if (size?.instanceId != null && (afterDisplay?.instanceId != size?.instanceId ||
                observed?.scope != size?.instanceId)) observed = null
        val after = observed?.snapshot?.toEffectSnapshot(afterDisplay)
        limit = LimitNotice.find(baseline, after)
        effect = judge(after, readEffectFact { controller.effectFrameGrid() }.takeIf { observed != null || inline == null })
        if (effect?.kind == EffectKind.NoEffect) {
            delay(EffectTuning.RECHECK_MS)
            val next = read()
            val nextDisplay = readEffectFact { controller.effectDisplayInfo() }
            val sameDisplay = size?.instanceId == null ||
                (nextDisplay?.instanceId == size?.instanceId && (next == null || next.scope == size?.instanceId))
            if (!sameDisplay) {
                observed = null
                effect = judge(null, null)
                return@withTimeoutOrNull
            }
            if (next != null) {
                observed = next
                afterDisplay = nextDisplay
            }
            val again = next?.snapshot?.toEffectSnapshot(nextDisplay)
            limit = limit ?: LimitNotice.find(baseline, again)
            effect = judge(again, readEffectFact { controller.effectFrameGrid() }) ?: effect
        }
    }
    val quality = baseline?.let { TreeAdequacy.ofTree(it).quality }
        ?: TreeQuality.Partial.takeIf { before?.truncated == true }
    val classified = effect?.let {
        if (it.kind == EffectKind.NoEffect && ActionEffect.treeBlind(verb, quality, baseline, target, region)) {
            it.copy(treeBlind = true)
        } else {
            it
        }
    }
    val readback = if (inline != null) observed?.let { last ->
        val display = afterDisplay
        if (display?.instanceId != null && (last.scope != display.instanceId ||
                readEffectFact { controller.effectDisplayInfo() } != display)) return@let null
        val frame = last.snapshot.toEffectSnapshot(display)
        val protected = display?.instanceId != null && frame.hasWindow &&
            isPhongentFrameBlack(controller.previewForEffect(display))
        val supplement = if (protected) {
            buildJsonObject {
                put("protected", true)
                put("readable", false)
            }
        } else if (!secret && inline.ocrEnabled && display?.instanceId != null) {
            val focus = region.takeIf { classified?.kind != EffectKind.PlaceChanged }
            readPhongentVisibleText(frame, focus, preview = { controller.previewForEffect(display) }) {
                controller.screenshotForEffect(display)
            }
        } else null
        if (supplement?.get("protected") == JsonPrimitive(true)) {
            inline.references.invalidate()
            supplement
        } else if (display?.instanceId != null && readEffectFact { controller.effectDisplayInfo() } != display) {
            null
        } else {
            val diff = inline.references.update(last, inline.visibleBase, GUI_MAX_NODES,
                SCREEN_DEFAULT_MAX_DEPTH to GUI_MAX_NODES, display, inline.visibleReads)
            JsonObject(diff + supplement.orEmpty() + ("complete" to JsonPrimitive(
                !last.snapshot.truncated && diff["truncated"] != JsonPrimitive(true)
            )))
        }
    } else null
    return result to ScreenEffectEvidence(classified, verb, limit?.take(120), secret || classified?.readback?.secret == true, readback)
}

private suspend fun <T> readEffectFact(read: suspend () -> T): T? = try {
    read()
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

internal fun GuiSnapshot.toEffectSnapshot(display: ScreenDisplayInfo?): ScreenSnapshot {
    val width = display?.width ?: (nodes.maxOfOrNull { it.bounds.right } ?: 0)
    val height = display?.height ?: (nodes.maxOfOrNull { it.bounds.bottom } ?: 0)
    fun normalized(bounds: GuiBounds) = listOf(
        normalize(bounds.left, width), normalize(bounds.top, height),
        normalize(bounds.right, width), normalize(bounds.bottom, height),
    )
    // Mika ScreenReader.visit keeps anonymous containers in the tree with no ref.
    val seen = HashSet<Triple<String, String, GuiBounds>>()
    fun ownLabel(node: GuiSnapshotNode): String? {
        val labels = if (node.secret) listOf(node.placeholder) else listOf(node.text, node.desc, node.placeholder)
        return labels.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
    }
    val elements = nodes.mapIndexedNotNull { position, node ->
        val bounds = node.bounds
        if (bounds.width < 5 || bounds.height < 5) return@mapIndexedNotNull null
        val own = ownLabel(node)
        if (own == null && !node.clickable && !node.editable && !node.scrollable) return@mapIndexedNotNull null
        val name = own ?: if (node.clickable && !node.secret) {
            val parts = linkedSetOf<String>()
            for (next in position + 1 until nodes.size) {
                val child = nodes[next]
                if (child.depth <= node.depth || parts.joinToString(" ").length > 60) break
                if (child.depth <= node.depth + 7 && !child.secret) ownLabel(child)?.let(parts::add)
            }
            val joined = parts.joinToString(" ")
            if (joined.length > 60) joined.take(60) + "…" else joined
        } else ""
        if (!seen.add(Triple(node.className, name, bounds))) return@mapIndexedNotNull null
        UiElement(
            ref = node.ref,
            role = node.className,
            name = name,
            centerX = bounds.centerX,
            centerY = bounds.centerY,
            bounds = normalized(bounds),
            resourceId = node.resourceId,
            clickable = node.clickable,
            editable = node.editable,
            scrollable = node.scrollable,
            hint = node.hint,
            focused = node.focused,
            secret = node.secret,
            placeholder = node.placeholder,
            states = buildSet {
                if (node.checked) add(UiStates.CHECKED)
                if (node.selected) add(UiStates.SELECTED)
                if (!node.enabled) add(UiStates.DISABLED)
            },
        )
    }
    val refs = elements.mapTo(HashSet()) { it.ref }
    var index = 0
    fun readTree(): UiTreeNode {
        val node = nodes[index++]
        val children = buildList {
            while (index < nodes.size && nodes[index].depth > node.depth) add(readTree())
        }
        return UiTreeNode(
            role = node.className, bounds = normalized(node.bounds), ref = node.ref.takeIf { it in refs },
            text = node.text.takeUnless { node.secret }, description = node.desc.takeUnless { node.secret },
            hintText = node.placeholder, resourceId = node.resourceId,
            clickable = node.clickable, editable = node.editable, scrollable = node.scrollable,
            checked = node.checked, selected = node.selected, enabled = node.enabled,
            focused = node.focused, secret = node.secret, children = children,
        )
    }
    val tree = buildList { while (index < nodes.size) add(readTree()) }
    return ScreenSnapshot(
        snapshotId = nodes.firstOrNull()?.ref.orEmpty(),
        packageName = packageName,
        elements = elements,
        width = width,
        height = height,
        hasWindow = packageName != null || nodes.isNotEmpty() || windows.any { it.selected },
        windows = windows.map { window ->
            UiWindowTree(
                id = window.id, type = window.kind.name.lowercase(), layer = window.layer,
                title = window.title, packageName = window.packageName, active = window.active,
                focused = window.focused, nodes = if (window.selected) tree else emptyList(),
            )
        },
    )
}
