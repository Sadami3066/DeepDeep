package io.github.whywood.deepdeep.plugin.host.services

import com.wangzan101.mika.core.tool.Verb
import io.github.whywood.deepdeep.plugin.api.PrivilegeLevel
import io.github.whywood.deepdeep.plugin.api.PrivilegeResult
import io.github.whywood.deepdeep.plugin.api.PrivilegeService
import io.github.whywood.deepdeep.plugin.api.ScreenTarget
import io.github.whywood.deepdeep.screen.GuiSelector
import io.github.whywood.deepdeep.screen.ScreenController
import io.github.whywood.deepdeep.screen.runPhongentEffect
import io.github.whywood.deepdeep.screen.withScreenExecution
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val GUI_ACT_PLUGIN_ID = "io.github.whywood.deepdeep.plugin.guiact"

internal class PluginScreenEvidence(val target: ScreenTarget) :
    AbstractCoroutineContextElement(PluginScreenEvidence) {
    companion object Key : CoroutineContext.Key<PluginScreenEvidence>

    private val entries = mutableListOf<JsonObject>()
    private var currentStep: Int? = null
    private val observations = mutableMapOf<Int, Set<String>>()

    fun actions(): List<JsonObject> = synchronized(entries) { entries.toList() }

    fun beginStep(index: Int) = synchronized(entries) { currentStep = index }

    fun observe(texts: Set<String>) = synchronized(entries) {
        currentStep?.let { observations.putIfAbsent(it, texts) }
    }

    fun assertion(index: Int, expected: String): Boolean? = synchronized(entries) {
        observations[index]?.any { it.contains(expected, ignoreCase = true) }
    }

    fun record(verb: Verb, ok: Boolean, evidence: JsonObject? = null) {
        synchronized(entries) {
            entries += buildJsonObject {
                put("index", entries.size)
                currentStep?.let { put("step", it) }
                put("verb", verb.name)
                put("ok", ok)
                evidence?.forEach { (key, value) -> put(key, value) }
            }
        }
    }
}

internal class GuiActPrivilegeService(
    private val delegate: PrivilegeService,
    private val main: ScreenController?,
    private val virtual: ScreenController?,
) : PrivilegeService by delegate {
    override suspend fun exec(command: String, level: PrivilegeLevel, timeoutMs: Long): PrivilegeResult {
        val collector = coroutineContext[PluginScreenEvidence] ?: return delegate.exec(command, level, timeoutMs)
        val match = input.matchEntire(command.trim()) ?: return refused("Unsupported GUI Act input command")
        val controller = (if (collector.target == ScreenTarget.MAIN) main else virtual)
            ?: return refused("GUI Act screen unavailable")
        val (display, op, args) = match.destructured
        if (!argumentPatterns.getValue(op).matches(args)) return refused("Invalid GUI Act input arguments")
        val screen = controller.effectDisplayInfo() ?: return refused("GUI Act could not confirm the target display")
        if (display.toIntOrNull() != screen.displayId) return refused("GUI Act input targets a different display")
        val verb = when (op) {
            "tap" -> Verb.Tap
            "swipe" -> Verb.Swipe
            "keyevent" -> Verb.PressKey
            else -> Verb.TypeText
        }
        val point = if (op == "tap") {
            val coordinates = args.split(' ')
            val x = coordinates[0].toIntOrNull() ?: return refused("Invalid tap")
            val y = coordinates[1].toIntOrNull() ?: return refused("Invalid tap")
            GuiSelector.Point(x, y)
        } else null
        val typed = args.removeSurrounding("'").replace("%s", " ").takeIf { op == "text" }
        return controller.verifyPluginScreenAction(
            verb, point, typed, succeeded = { it.exitCode == 0 && !it.timedOut },
        ) {
            if (controller.effectDisplayInfo() != screen) {
                refused("GUI Act display changed before dispatch")
            } else {
                delegate.exec(command, level, timeoutMs)
            }
        }
    }

    private fun refused(message: String): PrivilegeResult = PrivilegeResult(1, "", message)

    private companion object {
        val input = Regex("""/system/bin/input -d (\d+) (tap|swipe|keyevent|text) (.+)""")
        val argumentPatterns = mapOf(
            "tap" to Regex("""\d{1,6} \d{1,6}"""),
            "swipe" to Regex("""\d{1,6} \d{1,6} \d{1,6} \d{1,6} \d{1,4}"""),
            "keyevent" to Regex("""KEYCODE_[A-Z0-9_]{1,24}"""),
            "text" to Regex("""'[A-Za-z0-9_%.,@:/+=-]{1,3072}'"""),
        )
    }
}

internal suspend fun <T> ScreenController.verifyPluginScreenAction(
    verb: Verb,
    selector: GuiSelector? = null,
    typed: String? = null,
    succeeded: (T) -> Boolean = { true },
    action: suspend () -> T,
): T {
    val collector = coroutineContext[PluginScreenEvidence]
    return withScreenExecution {
        if (collector == null) return@withScreenExecution action()
        try {
            val (result, effect) = runPhongentEffect(this, verb, selector, typed, succeeded, action)
            collector.record(verb, succeeded(result), effect.fields())
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            collector.record(verb, false)
            throw error
        }
    }
}
