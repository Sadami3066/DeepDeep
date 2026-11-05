package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentEvent
import dev.pi.agent.AgentToolResult
import dev.pi.agent.asAgentMessage
import dev.pi.ai.AssistantMessage
import dev.pi.ai.Model
import dev.pi.ai.StopReason
import dev.pi.ai.TextDeltaEvent
import dev.pi.ai.Usage
import dev.pi.coding.session.QueueBehavior
import dev.pi.coding.session.SessionEvent
import io.github.whywood.deepdeep.runtime.testing.FakeSessionEngine
import io.github.whywood.deepdeep.runtime.testing.FakeSessionHandle
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AgentRuntimeHostTest {
    @Test
    fun sendReattachesSessionAfterHandleIsReleased() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.addSession("session-1")
            fixture.host.send("session-1", "first")
            assertEquals(1, fixture.engine.createCount)
            assertEquals(listOf("first"), fixture.engine.handles[0].prompts.map { it.text })

            fixture.host.closeSession("session-1")
            assertEquals(1, fixture.engine.handles[0].closed.size)

            fixture.host.send("session-1", "second")
            assertEquals(2, fixture.engine.createCount)
            assertEquals("session-1", fixture.engine.handles[1].sessionId)
            assertEquals(listOf("second"), fixture.engine.handles[1].prompts.map { it.text })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun concurrentOpensCreateSingleSession() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val file = fixture.addSession("session-1")
            val opened = withContext(Dispatchers.Default) {
                (1..8).map {
                    async { fixture.host.openSession(file, fixture.homeDir.absolutePath) }
                }.awaitAll()
            }
            assertEquals(1, fixture.engine.createCount)
            assertEquals(1, opened.map { it.sessionId }.distinct().size)
            assertEquals(0, fixture.engine.handles[0].closed.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun deliverReminderInjectsWhenBusyAndStartsTurnWhenIdle() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val handle = fixture.open("session-1")
            handle.busy = true
            fixture.host.deliverReminder("session-1", "busy reminder", "r-busy")
            assertEquals(listOf("busy reminder"), handle.prompts.map { it.text })
            assertEquals(QueueBehavior.FollowUp, handle.prompts.single().behavior)

            handle.busy = false
            fixture.host.deliverReminder("session-1", "idle reminder", "r-idle")
            settle()
            assertEquals(listOf("busy reminder", "idle reminder"), handle.prompts.map { it.text })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun stopInvalidatesPendingGoalContinuation() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            fixture.goalDriver.rounds("session-1") += goalRound()
            fixture.goalDriver.rounds("session-2") += goalRound()
            fixture.addSession("session-1")
            fixture.addSession("session-2")
            fixture.host.send("session-1", "stop me")
            fixture.host.send("session-2", "run goal")
            assertEquals(2, fixture.goalDriver.nextRoundCalls)

            fixture.host.stop("session-1")
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(listOf("stop me"), fixture.engine.handles[0].prompts.map { it.text })
            assertEquals(listOf("run goal", "committed"), fixture.engine.handles[1].prompts.map { it.text })
            assertEquals(1, fixture.goalDriver.commits)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun runStartedPrecedesDeltasAndStructureSurvivesFlood() = runTest {
        val fixture = Fixture(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val received = mutableListOf<RuntimeEvent>()
        val subscription = backgroundScope.launch {
            fixture.host.events.collect { scoped ->
                if (!gate.isCompleted) gate.await()
                received += scoped.event
            }
        }
        try {
            runCurrent()
            fixture.engine.onCreated = { handle ->
                handle.onPrompt = {
                    handle.emit(textDelta("a"))
                    handle.emit(textDelta("b"))
                    repeat(600) { index -> handle.emit(textDelta("token $index")) }
                }
            }
            fixture.addSession("session-1")
            backgroundScope.launch { fixture.host.send("session-1", "stream") }
            settle()
            gate.complete(Unit)
            settle()

            val startedIndex = received.indexOfFirst { it is RuntimeEvent.RunStarted }
            val finishedIndex = received.indexOfFirst { it is RuntimeEvent.RunFinished }
            val firstDeltaIndex = received.indexOfFirst { it is RuntimeEvent.TextDelta }
            assertTrue(startedIndex >= 0)
            assertTrue(firstDeltaIndex > startedIndex)
            assertTrue(finishedIndex > startedIndex)
        } finally {
            subscription.cancel()
            fixture.close()
        }
    }

    @Test
    fun runStartedCarriesPromptOnlyForUserTurns() = runTest {
        val fixture = Fixture(backgroundScope)
        val received = mutableListOf<SessionRuntimeEvent>()
        val subscription = backgroundScope.launch {
            fixture.host.events.collect { received += it }
        }
        try {
            runCurrent()
            fixture.addSession("session-1")
            fixture.host.send("session-1", "hello from user")
            settle()
            val userStarted = received.map { it.event }.filterIsInstance<RuntimeEvent.RunStarted>().single()
            assertEquals("hello from user", userStarted.prompt)

            received.clear()
            fixture.host.deliverReminder("session-1", "reminder body", null)
            settle()
            val reminderStarted = received.map { it.event }.filterIsInstance<RuntimeEvent.RunStarted>().single()
            assertEquals("", reminderStarted.prompt)
        } finally {
            subscription.cancel()
            fixture.close()
        }
    }

    @Test
    fun reconfigureDefersRebuildUntilTurnEnds() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val handle = fixture.open("session-1")
            handle.suspendPrompt = true
            backgroundScope.launch { fixture.host.send("session-1", "turn") }
            settle()
            assertEquals(1, fixture.engine.createCount)
            assertEquals(1, handle.prompts.size)

            fixture.host.reconfigure()
            assertEquals(1, fixture.engine.createCount)

            handle.promptGate.complete(Unit)
            settle()

            assertEquals(2, fixture.engine.createCount)
            assertEquals(1, handle.closed.size)
        } finally {
            fixture.close()
        }
    }


    @Test
    fun reconfigureKeepsUnpersistedSessionAttached() = runTest {
        val fixture = Fixture(backgroundScope)
        try {
            val opened = fixture.host.newSession(AgentRuntimeHost.HOME_ID)
            assertEquals(1, fixture.engine.createCount)

            fixture.host.reconfigure()

            assertEquals(1, fixture.engine.createCount)
            fixture.host.send(opened.sessionId, "first")
            assertEquals(listOf("first"), fixture.engine.handles[0].prompts.map { it.text })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun activityFollowsRetryLifecycleAndResetsOnRecovery() = runTest {
        val fixture = Fixture(backgroundScope)
        val received = mutableListOf<SessionRuntimeEvent>()
        val subscription = backgroundScope.launch {
            fixture.host.events.collect { received += it }
        }
        try {
            runCurrent()
            val handle = fixture.open("session-1")
            handle.suspendPrompt = true
            backgroundScope.launch { fixture.host.send("session-1", "retry me") }
            settle()
            assertEquals(1, handle.prompts.size)

            received.clear()
            handle.emit(SessionEvent.RetryScheduled(1, 100, "network"))
            settle()
            assertEquals(listOf<RuntimeActivity?>(RuntimeActivity.Retrying(1)), activityChanges(received))

            handle.emit(messageStart())
            settle()
            assertEquals(RuntimeActivity.Thinking, activityChanges(received).last())

            handle.emit(textDelta("a"))
            handle.emit(textDelta("b"))
            settle()
            assertEquals(1, activityChanges(received).count { it == RuntimeActivity.Thinking })

            handle.emit(SessionEvent.RetryScheduled(2, 100, "network"))
            handle.emit(SessionEvent.RetrySucceeded(2))
            settle()
            assertEquals(RuntimeActivity.Thinking, activityChanges(received).last())

            handle.emit(toolStart("bash"))
            settle()
            assertEquals(RuntimeActivity.RunningTool("bash"), activityChanges(received).last())

            handle.emit(toolEnd("bash"))
            settle()
            assertEquals(RuntimeActivity.Thinking, activityChanges(received).last())

            handle.promptGate.complete(Unit)
            settle()

            assertEquals(
                listOf<RuntimeActivity?>(
                    RuntimeActivity.Retrying(1),
                    RuntimeActivity.Thinking,
                    RuntimeActivity.Retrying(2),
                    RuntimeActivity.Thinking,
                    RuntimeActivity.RunningTool("bash"),
                    RuntimeActivity.Thinking,
                    null,
                ),
                activityChanges(received),
            )
        } finally {
            subscription.cancel()
            fixture.close()
        }
    }

    private fun activityChanges(events: List<SessionRuntimeEvent>): List<RuntimeActivity?> =
        events.map { it.event }.filterIsInstance<RuntimeEvent.ActivityChanged>().map { it.activity }

    private fun messageStart(): SessionEvent = SessionEvent.AgentEventReceived(
        AgentEvent.MessageStart(assistantMessage.asAgentMessage()),
    )

    private fun toolStart(name: String): SessionEvent = SessionEvent.AgentEventReceived(
        AgentEvent.ToolExecutionStart("call-$name", name, JsonObject(emptyMap())),
    )

    private fun toolEnd(name: String): SessionEvent = SessionEvent.AgentEventReceived(
        AgentEvent.ToolExecutionEnd("call-$name", name, AgentToolResult(), false),
    )

    private fun kotlinx.coroutines.test.TestScope.settle() {
        repeat(32) {
            runCurrent()
        }
    }

    private fun goalRound(): GoalRound = GoalRound(
        round = 1,
        maxRounds = 0,
        prompt = "goal round",
        goalId = "goal-1",
        revision = 1,
    )

    private fun textDelta(text: String): SessionEvent = SessionEvent.AgentEventReceived(
        AgentEvent.MessageUpdate(
            message = assistantMessage.asAgentMessage(),
            assistantMessageEvent = TextDeltaEvent(
                contentIndex = 0,
                delta = text,
                partial = assistantMessage,
            ),
        ),
    )

    private class Fixture(private val scope: CoroutineScope) {
        val homeDir: File = Files.createTempDirectory("host-home").toFile()
        val workspacesDir: File = Files.createTempDirectory("host-workspaces").toFile()
        val engine = FakeSessionEngine()
        val settings = FakeModelSettings()
        val catalog = FakeSessionDirectory()
        val workspaces = FakeWorkspaceDirectory()
        val providers = FakeProviderDirectory()
        val goalDriver = FakeGoalDriver()

        val host = AgentRuntimeHost(
            homeDir = homeDir,
            workspacesDir = workspacesDir,
            engineFactory = { engine },
            modelSettings = settings,
            catalog = catalog,
            workspaces = workspaces,
            providers = providers,
            backgroundScope = scope,
            goalDriver = goalDriver,
            sessionCleaner = null,
            sessionArchived = null,
        )

        fun sessionFile(id: String): String = File(homeDir, "$id.jsonl").absolutePath

        fun addSession(id: String): String {
            val file = sessionFile(id)
            File(file).apply {
                parentFile?.mkdirs()
                writeText("{}")
            }
            catalog.sessions += ConversationSummary(
                id = id,
                title = id,
                updatedAt = 0L,
                filePath = file,
                cwd = homeDir.absolutePath,
            )
            return file
        }

        suspend fun open(id: String): FakeSessionHandle {
            val file = addSession(id)
            host.openSession(file, homeDir.absolutePath)
            return engine.handles.last()
        }

        fun close() {
            homeDir.deleteRecursively()
            workspacesDir.deleteRecursively()
        }
    }

    private class FakeModelSettings : ModelSettings {
        private val state = MutableStateFlow(ModelConfig())
        override val config: Flow<ModelConfig> = state
        override suspend fun read(): ModelConfig = state.value
        override suspend fun save(config: ModelConfig) {
            state.value = config
        }
    }

    private class FakeSessionDirectory : SessionDirectory {
        val sessions = mutableListOf<ConversationSummary>()
        override suspend fun list(cwd: String): List<ConversationSummary> = sessions.filter { it.cwd == cwd }
        override suspend fun listAll(cwds: List<String>): List<ConversationSummary> = sessions.toList()
        override suspend fun find(cwds: List<String>, sessionId: String): ConversationSummary? = sessions.firstOrNull { it.id == sessionId }
        override suspend fun delete(filePath: String) = Unit
        override suspend fun rename(id: String, title: String) = Unit
        override suspend fun setPinned(id: String, pinned: Boolean) = Unit
        override suspend fun setArchived(id: String, archived: Boolean) = Unit
        override suspend fun removeMeta(id: String) = Unit
    }

    private class FakeWorkspaceDirectory : WorkspaceDirectory {
        override suspend fun read(): List<RuntimeWorkspace> = emptyList()
        override suspend fun add(workspace: RuntimeWorkspace) = Unit
        override suspend fun remove(id: String) = Unit
    }

    private class FakeProviderDirectory : ProviderDirectory {
        override val summaries: List<ProviderSummary> = emptyList()
        override fun summary(providerId: String): ProviderSummary? = null
        override fun models(providerId: String): List<ModelOption> = emptyList()
        override fun resolveConfig(config: ModelConfig): ModelConfig = config
        override fun sdkModel(providerId: String, modelId: String): Model? = null
        override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"
    }

    private class FakeGoalDriver : GoalDriver {
        private val roundsBySession = mutableMapOf<String, ArrayDeque<GoalRound>>()
        var nextRoundCalls = 0
        var commits = 0
        fun rounds(sessionId: String): ArrayDeque<GoalRound> = roundsBySession.getOrPut(sessionId) { ArrayDeque() }
        override suspend fun noteTurn(sessionId: String, source: TurnSource) = Unit
        override suspend fun nextRound(sessionId: String, source: TurnSource, succeeded: Boolean): GoalRound? {
            nextRoundCalls += 1
            return roundsBySession[sessionId]?.removeFirstOrNull()
        }
        override suspend fun commitRound(sessionId: String, goalId: String, revision: Long): GoalRound? {
            commits += 1
            return GoalRound(round = 2, maxRounds = 0, prompt = "committed", goalId = goalId)
        }
        override suspend fun pauseOnStop(sessionId: String) = Unit
        override suspend fun clearSession(sessionId: String) = Unit
    }

    private companion object {
        val assistantMessage: AssistantMessage = AssistantMessage(
            content = mutableListOf(),
            api = "fake",
            provider = "fake",
            model = "fake",
            usage = Usage.empty(),
            stopReason = StopReason.Stop,
            timestamp = 0L,
        )
    }
}
