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
    private lateinit var tools: Map<String, CodingTool>

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
        tools = goalTools(SessionBinding().apply { attach(session) }, service) { 10 }.associateBy { it.name }
    }

    @After
    fun tearDown() = runBlocking {
        if (::session.isInitialized) runCatching { session.close() }
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun createGoalUsesDefaultMaxRounds() = runTest {
        val goal = executeOk("create_goal", buildJsonObject { put("objective", "发布新版本") })
            .getValue("goal").jsonObject

        assertEquals(10, goal.getValue("maxRounds").jsonPrimitive.int)
    }

    @Test
    fun explicitCreateRoundsWinOverDefault() = runTest {
        val goal = executeOk(
            "create_goal",
            buildJsonObject {
                put("objective", "发布新版本")
                put("max_goal_rounds", 2)
            },
        ).getValue("goal").jsonObject

        assertEquals(2, goal.getValue("maxRounds").jsonPrimitive.int)
    }

    @Test
    fun updateGoalEditKeepsExistingRoundsWhenNotPassed() = runTest {
        val created = service.create(session.sessionId, "旧目标", 5)!!.goal

        val goal = executeOk(
            "update_goal",
            buildJsonObject {
                put("goal_id", created.id)
                put("revision", created.revision)
                put("action", "edit")
                put("objective", "新目标")
            },
        ).getValue("goal").jsonObject

        assertEquals("新目标", goal.getValue("objective").jsonPrimitive.content)
        assertEquals(5, goal.getValue("maxRounds").jsonPrimitive.int)
        assertEquals(5, service.goals.value.getValue(session.sessionId).goal.maxRounds)
    }

    @Test
    fun updateGoalEditWithoutChangesIsStillRejected() = runTest {
        val created = service.create(session.sessionId, "旧目标", 5)!!.goal

        val error = executeError(
            "update_goal",
            buildJsonObject {
                put("goal_id", created.id)
                put("revision", created.revision)
                put("action", "edit")
            },
        )

        assertTrue(error.contains("requires objective or max_goal_rounds"))
        assertEquals(5, service.goals.value.getValue(session.sessionId).goal.maxRounds)
        assertEquals("旧目标", service.goals.value.getValue(session.sessionId).goal.objective)
    }

    @Test
    fun explicitEditRoundsStillApply() = runTest {
        val created = service.create(session.sessionId, "旧目标", 5)!!.goal

        val goal = executeOk(
            "update_goal",
            buildJsonObject {
                put("goal_id", created.id)
                put("revision", created.revision)
                put("action", "edit")
                put("max_goal_rounds", 4)
            },
        ).getValue("goal").jsonObject

        assertEquals(4, goal.getValue("maxRounds").jsonPrimitive.int)
    }

    private suspend fun executeOk(name: String, args: JsonObject): JsonObject {
        val result = tools.getValue(name).execute("call-1", args, null, null)
        val text = result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
        assertNull("unexpected tool error: $text", result.isError)
        return Json.parseToJsonElement(text).jsonObject
    }

    private suspend fun executeError(name: String, args: JsonObject): String {
        val result = tools.getValue(name).execute("call-1", args, null, null)
        val text = result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
        assertEquals("expected tool error: $text", true, result.isError)
        return text
    }
}
