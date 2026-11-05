package io.github.whywood.deepdeep.runtime

import dev.pi.ai.TextContent
import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.session.AgentSession
import dev.pi.coding.tools.CodingTool
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoalToolsTest {
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("network is not used")
    }

    private val credentials = object : CredentialStore {
        override fun read(providerId: String): Credential? = null
        override fun readAll(): Map<String, Credential> = emptyMap()
        override fun readApiKey(providerId: String): String? = null
        override fun readEnv(providerId: String): Map<String, String>? = null
        override fun list(): List<CredentialInfo> = emptyList()
        override fun keys(): List<String> = emptyList()
        override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? = null
        override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) = Unit
        override fun write(providerId: String, credential: Credential) = Unit
        override fun writeRawCredential(providerId: String, credential: JsonElement) = Unit
        override fun delete(providerId: String) = Unit
        override fun readRaw(): Map<String, JsonElement> = emptyMap()
    }

    private lateinit var root: File
    private lateinit var session: AgentSession
    private lateinit var service: GoalService
    private lateinit var tools: List<CodingTool>

    @Before
    fun setUp() = runBlocking {
        root = Files.createTempDirectory("deepdeep-goal-tools").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val engine = PiSessionEngine(
            workspace = workspace,
            agentDir = agentDir,
            credentials = credentials,
            transport = transport,
            shellExecutor = null,
            providers = ProviderCatalog(transport, credentials),
            fs = FileSystem.SYSTEM,
        )
        session = engine.createSession(file = null, config = null, shellEnabled = false, mcpEnabled = false)
        service = GoalService()
        service.noteTurn(session.sessionId, TurnSource.USER)
        tools = goalTools(SessionBinding().apply { attach(session) }, service) { 10 }
    }

    @After
    fun tearDown() = runBlocking {
        if (::session.isInitialized) runCatching { session.close() }
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun toolsSeparateReadingCreationAndUpdates() {
        assertEquals(listOf("get_goal", "create_goal", "update_goal"), tools.map { it.name })
        val actions = tools.last().parameters.getValue("properties").jsonObject
            .getValue("action").jsonObject.getValue("enum").toString()
        listOf("edit", "pause", "resume", "complete", "blocked").forEach { assertTrue(actions.contains(it)) }
        assertTrue(!actions.contains("create"))
    }

    @Test
    fun createGoalUsesDefaultMaxRounds() = runTest {
        val goal = executeOk(
            buildJsonObject {
                put("action", "create")
                put("objective", "发布新版本")
            },
        ).getValue("goal").jsonObject

        assertEquals(10, goal.getValue("maxRounds").jsonPrimitive.int)
    }

    @Test
    fun explicitCreateRoundsWinOverDefault() = runTest {
        val goal = executeOk(
            buildJsonObject {
                put("action", "create")
                put("objective", "发布新版本")
                put("maxRounds", 2)
            },
        ).getValue("goal").jsonObject

        assertEquals(2, goal.getValue("maxRounds").jsonPrimitive.int)
    }

    @Test
    fun getReturnsCreatedGoal() = runTest {
        val created = executeOk(
            buildJsonObject {
                put("action", "create")
                put("objective", "整理会议记录")
            },
        ).getValue("goal").jsonObject

        val read = executeOk(buildJsonObject { put("action", "get") }).getValue("goal").jsonObject

        assertEquals(created.getValue("id").jsonPrimitive.content, read.getValue("id").jsonPrimitive.content)
        assertEquals(created.getValue("revision").jsonPrimitive.int, read.getValue("revision").jsonPrimitive.int)
    }

    @Test
    fun editKeepsExistingRoundsWhenNotPassed() = runTest {
        val created = service.create(session.sessionId, "旧目标", 5)!!.goal

        val goal = executeOk(
            buildJsonObject {
                put("action", "edit")
                put("goalId", created.id)
                put("revision", created.revision)
                put("objective", "新目标")
            },
        ).getValue("goal").jsonObject

        assertEquals("新目标", goal.getValue("objective").jsonPrimitive.content)
        assertEquals(5, goal.getValue("maxRounds").jsonPrimitive.int)
        assertEquals(5, service.goals.value.getValue(session.sessionId).goal.maxRounds)
    }

    @Test
    fun editWithoutChangesIsRejected() = runTest {
        val created = service.create(session.sessionId, "旧目标", 5)!!.goal

        val error = executeError(
            buildJsonObject {
                put("action", "edit")
                put("goalId", created.id)
                put("revision", created.revision)
            },
        )

        assertTrue(error.contains("requires objective or maxRounds"))
        assertEquals(5, service.goals.value.getValue(session.sessionId).goal.maxRounds)
        assertEquals("旧目标", service.goals.value.getValue(session.sessionId).goal.objective)
    }

    @Test
    fun explicitEditRoundsStillApply() = runTest {
        val created = service.create(session.sessionId, "旧目标", 5)!!.goal

        val goal = executeOk(
            buildJsonObject {
                put("action", "edit")
                put("goalId", created.id)
                put("revision", created.revision)
                put("maxRounds", 4)
            },
        ).getValue("goal").jsonObject

        assertEquals(4, goal.getValue("maxRounds").jsonPrimitive.int)
    }

    @Test
    fun legacyParameterNamesAreRejected() = runTest {
        val error = executeError(buildJsonObject {
            put("action", "create")
            put("objective", "发布新版本")
            put("max_goal_rounds", 3)
        })
        assertTrue(error.contains("max_goal_rounds"))
        assertTrue(service.goals.value.isEmpty())
    }

    @Test
    fun resumeActiveGoalReportsAlreadyActive() = runTest {
        val created = executeOk(
            buildJsonObject {
                put("action", "create")
                put("objective", "整理会议记录")
            },
        ).getValue("goal").jsonObject

        val error = executeError(
            buildJsonObject {
                put("action", "resume")
                put("goalId", created.getValue("id").jsonPrimitive.content)
                put("revision", created.getValue("revision").jsonPrimitive.int)
            },
        )

        assertTrue(error, error.contains("already active"))
        assertTrue(error, error.contains("get_goal"))
    }

    @Test
    fun resumeCompleteGoalReportsAlreadyComplete() = runTest {
        val created = executeOk(
            buildJsonObject {
                put("action", "create")
                put("objective", "整理会议记录")
            },
        ).getValue("goal").jsonObject
        val completed = executeOk(
            buildJsonObject {
                put("action", "complete")
                put("goalId", created.getValue("id").jsonPrimitive.content)
                put("revision", created.getValue("revision").jsonPrimitive.int)
            },
        ).getValue("goal").jsonObject

        val error = executeError(
            buildJsonObject {
                put("action", "resume")
                put("goalId", completed.getValue("id").jsonPrimitive.content)
                put("revision", completed.getValue("revision").jsonPrimitive.int)
            },
        )

        assertTrue(error, error.contains("already complete"))
    }

    @Test
    fun eachManagementEntryRejectsMissingAndUnrelatedArguments() = runTest {
        val binding = SessionBinding().apply { attach(session) }
        val timers = TimerService(io.github.whywood.deepdeep.runtime.testing.FakeAgentRuntime(), TimerStore(File(root, "timers")), backgroundScope)
        val jobs = JobRegistry(backgroundScope, notifier = { _, _, _ -> })
        val entries = tools + jobTools(binding, jobs) + timerTools(timers, binding)
        assertEquals(11, entries.size)
        for (entry in entries) {
            val wrong = entry.execute("bad", buildJsonObject { put("unrelated", true) }, null, null)
            assertEquals(entry.name, true, wrong.isError)
            val required = entry.parameters["required"].toString()
            if (required != "[]") assertEquals(entry.name, true, entry.execute("missing", buildJsonObject {}, null, null).isError)
        }
        assertEquals(true, entries.first { it.name == "job_list" }.execute("old", buildJsonObject { put("action", "list") }, null, null).isError)
    }

    @Test
    fun timerCreateHonorsDisabledAndRejectsConflictingTriggers() = runTest {
        val file = session.sessionFile
        assertTrue(file != null)
        val store = TimerStore(File(root, "timers"))
        val service = TimerService(io.github.whywood.deepdeep.runtime.testing.FakeAgentRuntime(), store, backgroundScope)
        val entries = timerTools(service, SessionBinding().apply { attach(session) })
        val create = entries.first { it.name == "timer_create" }
        val args = buildJsonObject {
            put("prompt", "检查输出")
            put("frequency", "cron")
            put("cron", "0 9 * * *")
            put("enabled", false)
        }
        val result = create.execute("create", args, null, null)
        assertNull(result.content.toString(), result.isError)
        assertEquals(false, store.list().single().enabled)
        val conflicting = JsonObject(args + ("date" to kotlinx.serialization.json.JsonPrimitive("2028-01-01")))
        assertEquals(true, create.execute("conflict", conflicting, null, null).isError)
        assertEquals(1, store.list().size)
        val invalidType = JsonObject(args + ("enabled" to kotlinx.serialization.json.JsonPrimitive("false")))
        assertEquals(true, create.execute("type", invalidType, null, null).isError)
    }

    @Test
    fun updatesRejectFractionalRevisionsAndIrrelevantActionFields() = runTest {
        val created = service.create(session.sessionId, "目标", 5)!!.goal
        val update = tools.first { it.name == "update_goal" }
        val fractional = buildJsonObject { put("goalId", created.id); put("revision", 1.5); put("action", "complete") }
        assertEquals(true, update.execute("fractional", fractional, null, null).isError)
        val irrelevant = buildJsonObject { put("goalId", created.id); put("revision", created.revision); put("action", "pause"); put("objective", "不应改写") }
        assertEquals(true, update.execute("irrelevant", irrelevant, null, null).isError)
        assertEquals(created, service.goals.value.getValue(session.sessionId).goal)
    }

    @Test
    fun backgroundJobReadAndKillRequireOwningConversation() = runTest {
        val registry = JobRegistry(backgroundScope, BackgroundCommandRunner { _, _ -> kotlinx.coroutines.awaitCancellation() }, notifier = { _, _, _ -> })
        val other = registry.start("other-session", "sleep", "other").id!!
        val entries = jobTools(SessionBinding().apply { attach(session) }, registry)
        for (name in listOf("job_output", "job_kill")) {
            assertEquals(true, entries.first { it.name == name }.execute("other", buildJsonObject { put("jobId", other) }, null, null).isError)
        }
        val own = registry.start(session.sessionId, "sleep", "own").id!!
        assertEquals(true, entries.first { it.name == "job_output" }.execute("wait", buildJsonObject { put("jobId", own); put("timeoutMs", 50) }, null, null).isError)
        registry.finishSession(session.sessionId)
        registry.finishSession("other-session")
    }

    private suspend fun execute(args: JsonObject): dev.pi.agent.AgentToolResult {
        val action = args.getValue("action").jsonPrimitive.content
        val name = when (action) { "get" -> "get_goal"; "create" -> "create_goal"; else -> "update_goal" }
        return tools.first { it.name == name }.execute("call", if (name == "update_goal") args else JsonObject(args - "action"), null, null)
    }

    private suspend fun executeOk(args: JsonObject): JsonObject {
        val result = execute(args)
        val text = result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
        assertNull("unexpected tool error: $text", result.isError)
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun executeError(args: JsonObject): String {
        val result = execute(args)
        val text = result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
        assertEquals("expected tool error: $text", true, result.isError)
        return text
    }
}
