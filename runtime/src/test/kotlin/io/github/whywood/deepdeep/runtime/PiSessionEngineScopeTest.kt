package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import dev.pi.ai.TextContent
import dev.pi.ai.auth.Credential
import dev.pi.ai.auth.CredentialInfo
import dev.pi.ai.auth.CredentialStore
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import dev.pi.agent.AgentToolResult
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.ShellResult
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PiSessionEngineScopeTest {
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

    @Test
    fun workshopToolsAreScopedWhileSessionFilesStayOutsideWorkspace() = runTest {
        val root = Files.createTempDirectory("deepdeep-engine-scope").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        val catalog = ProviderCatalog(transport, credentials)
        val engine = PiSessionEngine(
            workspace = workspace,
            agentDir = agentDir,
            credentials = credentials,
            transport = transport,
            shellExecutor = null,
            providers = catalog,
            toolFs = ScopedFileSystem(FileSystem.SYSTEM, listOf(workspace.absolutePath.toPath())),
            fs = FileSystem.SYSTEM,
        )
        try {
            val session = engine.createSession(
                file = null,
                config = null,
                shellEnabled = false,
                toolsFilter = { tool -> workshopAgentToolVisible(tool.name) },
                mcpEnabled = false,
            )
            try {
                assertEquals(
                    listOf("edit", "find", "grep", "ls", "read", "write"),
                    session.activeToolNames.sorted(),
                )
                val sessionFile = session.sessionFile
                assertNotNull(sessionFile)
                assertTrue(sessionFile.toString().startsWith(agentDir.absolutePath))
            } finally {
                session.close()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun workshopCapabilitiesAndFileToolsStayInsideWorkspace() = runTest {
        val root = Files.createTempDirectory("deepdeep-engine-tools").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        File(root, "outside.txt").writeText("outside secret")
        val catalog = ProviderCatalog(transport, credentials)
        val engine = PiSessionEngine(
            workspace = workspace,
            agentDir = agentDir,
            credentials = credentials,
            transport = transport,
            shellExecutor = null,
            providers = catalog,
            toolFs = ScopedFileSystem(FileSystem.SYSTEM, listOf(workspace.absolutePath.toPath())),
            fs = FileSystem.SYSTEM,
        )
        try {
            assertEquals(
                listOf("edit", "find", "grep", "ls", "read", "write"),
                buildWorkshopAgentTools(engine.availableTools()).map { it.name },
            )
            File(workspace, "notes.txt").writeText("hello scope\n")
            File(workspace, "draft").mkdirs()
            File(workspace, "draft/page.html").writeText("<html></html>")
            val tools = engine.availableTools()
                .filter { tool -> workshopAgentToolVisible(tool.name) }
                .associateBy { it.name }

            assertTrue(runTool(tools.getValue("ls"), JsonObject(emptyMap())).contains("notes.txt"))
            assertTrue(
                runTool(tools.getValue("find"), buildJsonObject { put("pattern", "*.html") })
                    .contains("draft/page.html"),
            )
            assertTrue(
                runTool(tools.getValue("grep"), buildJsonObject { put("pattern", "hello scope") })
                    .contains("notes.txt"),
            )
            assertTrue(
                runTool(
                    tools.getValue("write"),
                    buildJsonObject {
                        put("path", "fresh.txt")
                        put("content", "written inside")
                    },
                ).isNotEmpty(),
            )
            assertEquals(
                "written inside",
                runTool(tools.getValue("read"), buildJsonObject { put("path", "fresh.txt") }).trim(),
            )

            val escape = runCatching {
                tools.getValue("read").execute("call", buildJsonObject { put("path", "../outside.txt") }, null, null)
            }.exceptionOrNull()
            assertTrue("expected IOException but got $escape", escape is IOException)
            val absoluteEscape = runCatching {
                tools.getValue("read").execute("call", buildJsonObject { put("path", "/etc/passwd") }, null, null)
            }.exceptionOrNull()
            assertTrue("expected IOException but got $absoluteEscape", absoluteEscape is IOException)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun engineRegistersCustomBashAndWorkspacePaths() = runTest {
        val root = Files.createTempDirectory("deepdeep-engine-shell").toFile()
        val workspace = File(root, "workspace").apply { mkdirs() }
        val agentDir = File(root, "agent").apply { mkdirs() }
        File(workspace, "notes.txt").writeText("hello")
        val catalog = ProviderCatalog(transport, credentials)
        val shell = object : ShellExecutor {
            override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult =
                ShellResult("ran $command\n", 0)
        }
        try {
            val engine = PiSessionEngine(
                workspace = workspace,
                agentDir = agentDir,
                credentials = credentials,
                transport = transport,
                shellExecutor = shell,
                providers = catalog,
            )
            val tools = engine.availableTools().associateBy { it.name }
            val bash = tools.getValue("bash")
            assertTrue(bash.description.contains("[exit: N]"))
            assertTrue(runTool(bash, buildJsonObject { put("command", "true") }).contains("[exit: 0]"))

            val read = tools.getValue("read")
            val prepared = read.prepareArguments!!.invoke(buildJsonObject { put("path", "/workspace/notes.txt") })
            assertEquals(
                File(workspace, "notes.txt").absolutePath,
                prepared.getValue("path").jsonPrimitive.content,
            )
            assertEquals("hello", runTool(read, prepared).trim())

            val withoutShell = PiSessionEngine(
                workspace = workspace,
                agentDir = agentDir,
                credentials = credentials,
                transport = transport,
                shellExecutor = null,
                providers = catalog,
            )
            assertNull(withoutShell.availableTools().firstOrNull { it.name == "bash" })
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun runTool(tool: CodingTool, args: JsonObject): String {
        val result: AgentToolResult = tool.execute("call", args, null, null)
        return result.content.filterIsInstance<TextContent>().joinToString("") { it.text }
    }
}
