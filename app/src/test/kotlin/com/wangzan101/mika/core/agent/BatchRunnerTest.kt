package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.guard.OperationGuards
import com.wangzan101.mika.core.run.SteerSignal
import com.wangzan101.mika.core.run.waitOrSteer
import com.wangzan101.mika.core.tool.Until
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import com.wangzan101.mika.core.uistate.UntilWait
import com.wangzan101.mika.core.uistate.UntilWaitPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class StepResult(val changed: Boolean?)
private data class StepEvent(val callId: String, val source: String = "batch")
private data class ConfirmationEvent(val matched: String)
private class TestConfirmation

private class BatchScreen(vararg val screens: List<Pair<String, List<Int>>>) : UntilWaitPort {
    val calls = mutableListOf<String>()
    val results = ArrayDeque<StepResult>()
    var replaced: List<Pair<String, List<Int>>>? = null
    var limitAfterBack: String? = null
    var settles = 0
    fun peek() = ScreenSnapshot("p1", "com.x", (replaced ?: screens[0]).mapIndexed { i, (name, b) ->
        UiElement("@e${i + 1}", "button", name, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2,
            b, null, true, name.startsWith("输入"), false)
    }, 1080, 2400)
    override suspend fun waitStable(timeoutMs: Long): Boolean { settles++; return true }
    override suspend fun awaitChange(timeoutMs: Long): Boolean { delay(timeoutMs); return false }
}

class BatchRunnerTest {
    private val BATCHABLE = listOf("phone_back", "phone_tap", "phone_type_text", "wait")
    private val home = listOf("搜索框" to listOf(0, 0, 999, 80), "返回" to listOf(0, 900, 100, 999), "提交订单" to listOf(800, 900, 999, 999))
    private val events = mutableListOf<Any>()
    private var gates = 0
    private var onGate: () -> Unit = {}

    private fun TestScope.runner(
        screen: BatchScreen,
        guards: OperationGuards = OperationGuards(),
        confirmations: TestConfirmation? = TestConfirmation(),
        steerWaiting: () -> Boolean = { false },
    ): BatchRunner {
        val time = { testScheduler.currentTime }
        val dispatcher = object : BatchScreenPort {
            override suspend fun settle(timeoutMs: Long) { screen.waitStable(timeoutMs) }
            override suspend fun waitUntil(until: Until, clock: () -> Long, interrupted: () -> Boolean) =
                UntilWait(screen, screen::peek, clock, interrupted).run(until)
        }
        val executor = object : BatchActionPort {
            override val recorder = BatchRecorder(time) { events += it }
            override val confirms = confirmations != null
            override suspend fun run(call: BatchToolCall, step: Int): BatchActionOutcome {
                gates++
                onGate()
                val args = Json.parseToJsonElement(call.argsJson).jsonObject
                var limit: String? = null
                when (call.name) {
                    "phone_tap" -> {
                        val ref = args["ref"]?.jsonPrimitive?.content
                        val elements = screen.peek().elements
                        val target = if (ref != null) {
                            elements.firstOrNull { it.ref == ref }
                        } else {
                            val x = args.getValue("x").jsonPrimitive.int
                            val y = args.getValue("y").jsonPrimitive.int
                            elements.firstOrNull { x in it.bounds[0]..it.bounds[2] && y in it.bounds[1]..it.bounds[3] }
                        } ?: return BatchActionOutcome.Refused("target_unresolved")
                        if (target.name == "提交订单") {
                            events += ConfirmationEvent(target.name)
                            return BatchActionOutcome.Refused("confirmation_required",
                                hit = BatchActionOutcome.GuardHit(BatchActionOutcome.GuardAction("order"), target.name))
                        }
                        screen.calls += "tapRef $ref ${args["snapshotId"]?.jsonPrimitive?.content}"
                    }
                    "phone_type_text" -> screen.calls += "type ${args.getValue("text").jsonPrimitive.content}"
                    "phone_back" -> {
                        screen.calls += "back"
                        limit = screen.limitAfterBack
                    }
                    "wait" -> {
                        val millis = (args.getValue("seconds").jsonPrimitive.content.toDouble() * 1_000).toLong()
                        if (waitOrSteer { delay(millis); true } == null) return BatchActionOutcome.Ran(
                            BatchExecution(buildJsonObject { put("ok", false); put("error", BatchRunner.NEW_MESSAGE) }),
                        )
                    }
                }
                events += StepEvent(call.id)
                return BatchActionOutcome.Ran(BatchExecution(buildJsonObject {
                    put("ok", true)
                    limit?.let { put("limitReached", it) }
                }, changed = screen.results.removeFirstOrNull()?.changed))
            }
        }
        return BatchRunner(dispatcher, guards, executor, steerWaiting = steerWaiting)
    }

    private fun steps(vararg s: Triple<String, String, String?>) = s.map { BatchStep(it.first, it.second, it.third?.let { e -> Until(appears = e) }) }
    private fun st(tool: String, args: String = "{}", expect: String? = null) = Triple(tool, args, expect)
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun allStepsPassReturnsOk() = runTest {
        val s = BatchScreen(home)
        val d = s
        val r = runner(d).run(steps(st("phone_tap", """{"ref":"@e1","snapshotId":"p1"}"""), st("phone_type_text", """{"text":"咖啡"}"""), st("phone_back")), "c1", 3).content
        assertEquals("true", r.s("ok")); assertEquals(3, r["completed"]!!.jsonPrimitive.int); assertEquals(3, r["total"]!!.jsonPrimitive.int)
        assertEquals(listOf("tapRef @e1 p1", "type 咖啡", "back"), s.calls)
        assertEquals(3, gates)
        val results = events.filterIsInstance<StepEvent>()
        assertEquals(listOf("batch", "batch", "batch"), results.map { it.source })
        assertEquals(listOf("c1.1", "c1.2", "c1.3"), results.map { it.callId })
        assertEquals(BatchEvent(events.filterIsInstance<BatchEvent>().single().atMs, 3, 3, false, null), events.filterIsInstance<BatchEvent>().single())
    }

    @Test fun newUserMessageStopsBatchAtTheNextBoundary() = runTest {
        val s = BatchScreen(home)
        val d = s
        var waiting = false
        onGate = { waiting = true }
        val r = runner(d, steerWaiting = { waiting })
            .run(steps(st("phone_back"), st("phone_back"), st("phone_back")), "c1").content
        assertEquals(listOf("back"), s.calls)
        assertEquals("false", r.s("ok"))
        assertEquals(BatchRunner.NEW_MESSAGE, r.s("reason"))
        assertEquals("false", r.s("counts_as_failure"))
        assertEquals(1, r["completed"]!!.jsonPrimitive.int)
        val entries = r["steps"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, entries.size)
        assertEquals("true", entries[0].s("ok"))
        assertEquals(listOf(BatchRunner.NEW_MESSAGE, BatchRunner.NEW_MESSAGE), entries.drop(1).map { it.s("error") })
        assertEquals(listOf("false", "false"), entries.drop(1).map { it.s("executed") })
        assertTrue(events.filterIsInstance<BatchEvent>().single().stopped)
    }

    @Test fun steerCuttingAWaitStepSkipsItAndTheRest() = runTest {
        val s = BatchScreen(home)
        val pending = MutableStateFlow(false)
        launch { delay(1_000); pending.value = true }
        val r = withContext(SteerSignal(pending)) {
            runner(s).run(steps(st("phone_back"), st("wait", """{"seconds":10}"""), st("phone_back")), "c1").content
        }
        assertEquals(1_000L, currentTime)
        assertEquals(listOf("back"), s.calls)
        assertEquals(BatchRunner.NEW_MESSAGE, r.s("reason"))
        assertEquals(1, r["completed"]!!.jsonPrimitive.int)
        val entries = r["steps"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("false", "false"), entries.drop(1).map { it.s("executed") })
    }

    @Test fun unchangedScreenAloneDoesNotStopKnownSteps() = runTest {
        val s = BatchScreen(home).apply { results += StepResult(changed = true); results += StepResult(changed = false) }
        val d = s
        val r = runner(d).run(steps(st("phone_back"), st("phone_back"), st("phone_back")), "c1").content
        assertEquals("true", r.s("ok"))
        assertEquals(3, r["completed"]!!.jsonPrimitive.int)
        assertEquals(3, r["steps"]!!.jsonArray.size)
        assertEquals(listOf("back", "back", "back"), s.calls)
        assertTrue(!events.filterIsInstance<BatchEvent>().single().stopped)
    }

    @Test fun stopsOnExpectationUnmet() = runTest {
        val s = BatchScreen(home)
        val r = runner(s).run(steps(st("phone_back", expect = "搜索结果"), st("phone_back")), "c1").content
        assertEquals("expectation_unmet", r.s("reason"))
        val last = r["steps"]!!.jsonArray.last().jsonObject
        assertEquals("搜索结果", last.s("expect"))
        assertEquals(listOf("back"), s.calls)
        // 屏上有的文字就算对上
        val ok = runner(BatchScreen(home)).run(steps(st("phone_back", expect = "搜索"), st("phone_back")), "c2").content
        assertEquals("true", ok.s("ok"))
    }

    @Test fun stopsWhenALimitIsReported() = runTest {
        val s = BatchScreen(home)
        s.limitAfterBack = "今日次数已用完"
        val r = runner(s).run(steps(st("phone_back"), st("phone_back")), "c1").content
        assertEquals("limit_reached", r.s("reason"))
        assertEquals("今日次数已用完", r["steps"]!!.jsonArray.last().jsonObject.s("limitReached"))
        assertEquals(listOf("back"), s.calls)
    }

    @Test fun waitMayBeUnchanged() = runTest {
        val s = BatchScreen(home)
        val r = runner(s).run(steps(st("wait", """{"seconds":0.5}"""), st("phone_back")), "c1").content
        assertEquals("true", r.s("ok")); assertEquals(listOf("back"), s.calls)
    }

    @Test fun stopsBeforeIrreversible() = runTest {
        val s = BatchScreen(home)
        val d = s
        val r = runner(d).run(steps(st("phone_tap", """{"ref":"@e1","snapshotId":"p1"}"""), st("phone_type_text", """{"text":"你好"}"""),
            st("phone_tap", """{"ref":"@e3","snapshotId":"p1"}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertEquals(2, r["completed"]!!.jsonPrimitive.int)
        assertEquals("false", r["steps"]!!.jsonArray.last().jsonObject.s("executed"))
        assertEquals(listOf("tapRef @e1 p1", "type 你好"), s.calls)
        assertEquals(3, gates) // 先过关卡再按此刻的屏幕判,第 3 步判出不可逆就不执行
        assertNotNull(events.filterIsInstance<ConfirmationEvent>().singleOrNull())
    }

    @Test fun coordinateTapOnSendStopsBefore() = runTest {
        val s = BatchScreen(home)
        val r = runner(s).run(steps(st("phone_back"), st("phone_tap", """{"x":900,"y":950}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertTrue("提交订单" in r.s("matched"))
        assertEquals(listOf("back"), s.calls)
    }

    @Test fun blindCoordinateTapStops() = runTest {
        val s = BatchScreen(listOf("返回" to listOf(0, 900, 100, 999)))
        val r = runner(s).run(steps(st("phone_back"), st("phone_tap", """{"x":500,"y":500}""")), "c1").content
        assertEquals("target_unresolved", r.s("reason")); assertEquals(listOf("back"), s.calls)
    }

    @Test fun rejectsNonBatchableTool() {
        val avail = BATCHABLE.toSet() + "finish"
        val (none, msg) = BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}},{"tool":"finish","args":{"answer":"x"}}]}""", avail, BATCHABLE)
        assertNull(none); assertTrue(msg!!, "steps[2].tool" in msg && "finish" in msg)
        assertTrue("steps" in BatchRunner.parse("""{"steps":[]}""", avail, BATCHABLE).second!!)
        assertEquals(1, BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}}]}""", avail, BATCHABLE).first!!.size)
        assertTrue("expect" in BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{},"expect":"${"x".repeat(201)}"},{"tool":"phone_back","args":{}}]}""", avail, BATCHABLE).second!!)
        // 此刻没提供的工具(开关关着的 phone_open_link)也不行
        assertTrue("phone_open_link" in BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}},{"tool":"phone_open_link","args":{}}]}""", setOf("phone_back"), BATCHABLE).second!!)
        assertEquals(2, BatchRunner.parse("""{"steps":[{"tool":"phone_back","args":{}},{"tool":"phone_back"}]}""", avail, BATCHABLE).first!!.size)
    }

    @Test fun gateBeforeIrreversibleCheck() = runTest {
        // 等锁屏/被拿走的关卡期间屏幕换了:同一位置现在是「提交订单」,按过关卡之后的屏幕判,不点
        val s = BatchScreen(listOf("确定" to listOf(400, 450, 600, 550), "返回" to listOf(0, 900, 100, 999)))
        onGate = { if (gates == 2) s.replaced = listOf("提交订单" to listOf(400, 450, 600, 550)) }
        val r = runner(s).run(steps(st("phone_back"), st("phone_tap", """{"x":500,"y":500}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertEquals(listOf("back"), s.calls)
    }

    @Test fun waitsForStableScreenBetweenSteps() = runTest {
        val s = BatchScreen(home)
        runner(s).run(steps(st("phone_back"), st("phone_back", expect = "搜索"), st("phone_back")), "c1")
        // 步与步之间等两次;端口在动作里已等过界面(报了变没变),判效果时不再另等
        assertEquals(2, s.settles)
    }

    @Test fun stopEntriesSayWhetherTheStepRan() = runTest {
        val ran = runner(BatchScreen(home)).run(steps(st("phone_back", expect = "没有的字"), st("phone_back")), "c1").content
        assertEquals("true", ran["steps"]!!.jsonArray.last().jsonObject.s("executed"))
        assertTrue(ran.s("message"), "was run but did not pass its check (expectation_unmet)" in ran.s("message"))
        val notRun = runner(BatchScreen(listOf("返回" to listOf(0, 900, 100, 999)))).run(steps(st("phone_back"), st("phone_tap", """{"x":500,"y":500}""")), "c2").content
        assertEquals("false", notRun["steps"]!!.jsonArray.last().jsonObject.s("executed"))
        assertTrue("was not run (target_unresolved)" in notRun.s("message"))
    }

    @Test fun confirmationStopCarriesGuardVerdicts() = runTest {
        val guards = OperationGuards()
        val tap = """{"x":900,"y":950}"""
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(tap).jsonObject
        repeat(2) { guards.beforeAction("phone_tap", parsed) }
        val r = runner(BatchScreen(home), guards).run(steps(st("phone_tap", tap), st("phone_back")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertTrue(r["steps"]!!.jsonArray.single().jsonObject["guard"] == null)
    }

    @Test fun withoutConfirmationToolMessageDoesNotAskForIt() = runTest {
        val r = runner(BatchScreen(home), confirmations = null).run(steps(st("phone_back"), st("phone_tap", """{"x":900,"y":950}""")), "c1").content
        assertEquals("confirmation_required", r.s("error"))
        assertTrue("request_confirmation" !in r.s("message"))
        val with = runner(BatchScreen(home)).run(steps(st("phone_back"), st("phone_tap", """{"x":900,"y":950}""")), "c2").content
        assertTrue("request_confirmation" in with.s("message"))
    }
}
