package io.github.whywood.deepdeep.contract

import android.app.UiAutomation
import android.provider.Settings
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pi.ai.RequestAbortedException
import dev.pi.ai.RunSignal
import dev.pi.ai.TextContent
import dev.pi.ai.http.HttpRequest
import dev.pi.ai.http.HttpResponse
import dev.pi.ai.http.HttpTransport
import dev.pi.coding.tools.CodingTool
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.plugin.gui.GuiAccessibilityActions
import io.github.whywood.deepdeep.runtime.JobRegistry
import io.github.whywood.deepdeep.runtime.PiSessionEngine
import io.github.whywood.deepdeep.runtime.presentTool
import io.github.whywood.deepdeep.screen.AccessibilityScreenBackend
import io.github.whywood.deepdeep.screen.AndroidScreenAppCatalog
import io.github.whywood.deepdeep.screen.ScreenRouter
import io.github.whywood.deepdeep.screen.VirtualEngineBackend
import io.github.whywood.deepdeep.screen.screenTools
import io.github.whywood.deepdeep.service.AppShellExecutor
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import io.github.whywood.deepdeep.service.LinuxJobRunner
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.vscreen.PrivilegedLauncher
import io.github.whywood.deepdeep.vscreen.VirtualScreenEngine
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolContractDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val graph get() = (context.applicationContext as DeepDeepApplication).graph
    private val transport = object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse = error("No model request in device contract test")
    }
    private fun linux() = LinuxEnvironment(context, graph.homeDir, extraBinds = graph.linuxBinds())
    private fun tools(environment: LinuxEnvironment): Map<String, CodingTool> = PiSessionEngine(
        graph.homeDir, graph.agentDir, graph.credentials, transport, AppShellExecutor(environment), graph.providerCatalog,
    ).availableTools().associateBy { it.name }
    private fun dev.pi.agent.AgentToolResult.text() = content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
    private suspend fun run(tool: CodingTool, args: JsonObject = buildJsonObject {}) = tool.execute("device", args, null, null)
    private fun args(path: File) = buildJsonObject { put("path", path.absolutePath) }

    @Test
    fun absolutePathsAndLargeOutputWorkAcrossRealLinuxAndFileTools() = runBlocking {
        val environment = linux()
        val tools = tools(environment)
        val file = File(graph.homeDir, "contract-${UUID.randomUUID()}.txt")
        var log: File? = null
        val downloaded = File(context.filesDir, "micloud/recordings/contract-${UUID.randomUUID()}.txt")
        downloaded.parentFile!!.mkdirs()
        val draft = graph.workshopStore.createDraft("Contract ${UUID.randomUUID().toString().take(8)}")
        try {
            assertNull(run(tools.getValue("write"), buildJsonObject { put("path", file.absolutePath); put("content", "contract-marker\n") }).isError)
            val command = run(tools.getValue("bash"), buildJsonObject { put("command", "printf 'from-linux\\n' >> '${file.absolutePath}'; cat '${file.absolutePath}'") })
            assertNull(command.isError)
            assertTrue(command.text().contains("from-linux"))
            downloaded.writeText("neutral-download")
            assertTrue(run(tools.getValue("bash"), buildJsonObject { put("command", "cat '${downloaded.absolutePath}'") }).text().contains("neutral-download"))
            assertTrue(run(tools.getValue("read"), args(downloaded)).text().contains("neutral-download"))
            File(draft.directory, "index.html").writeText("<html><body>neutral-workshop</body></html>")
            val loader = io.github.whywood.deepdeep.runtime.workshopTools(graph.workshopStore, false).first { it.name == "workshop_load" }
            val installed = run(loader, args(draft.directory))
            assertNull(installed.text(), installed.isError)
            val installedDirectory = File(Json.parseToJsonElement(installed.text()).jsonObject.getValue("path").jsonPrimitive.content)
            assertTrue(run(tools.getValue("bash"), buildJsonObject { put("command", "cat '${File(installedDirectory, "index.html").absolutePath}'") }).text().contains("neutral-workshop"))
            assertTrue(run(tools.getValue("read"), args(File(installedDirectory, "index.html"))).text().contains("neutral-workshop"))

            assertTrue(run(tools.getValue("read"), args(file)).text().contains("from-linux"))
            val attachment = io.github.whywood.deepdeep.data.AttachmentRef(
                "contract", file.name, "text/plain", file.length(), file.name,
                io.github.whywood.deepdeep.data.AttachmentKind.TEXT,
            )
            val attachmentPrompt = io.github.whywood.deepdeep.data.encodeAttachmentBlock(listOf(attachment), graph.homeDir)
            val attachmentPath = io.github.whywood.deepdeep.data.parseAttachmentBlock(attachmentPrompt).attachments.single().path
            assertEquals(file.absolutePath, attachmentPath)
            assertTrue(run(tools.getValue("read"), args(File(attachmentPath))).text().contains("from-linux"))
            assertTrue(run(tools.getValue("grep"), buildJsonObject { put("path", graph.homeDir.absolutePath); put("pattern", "contract-marker"); put("glob", file.name) }).text().contains(file.absolutePath))
            val delivery = presentTool(null) { io.github.whywood.deepdeep.runtime.BoundSession("contract-device", null, graph.homeDir.absolutePath) }
            val delivered = run(delivery, buildJsonObject { put("items", buildJsonArray { add(buildJsonObject { put("type", "file"); put("path", file.absolutePath) }) }) })
            assertNull(delivered.text(), delivered.isError)
            val missing = run(delivery, buildJsonObject { put("items", buildJsonArray { add(buildJsonObject { put("type", "file"); put("path", file.absolutePath + ".missing") }) }) })
            assertEquals(true, missing.isError)
            assertTrue(runCatching { run(tools.getValue("read"), buildJsonObject { put("path", file.name) }) }.exceptionOrNull() is IllegalArgumentException)
            val big = run(tools.getValue("bash"), buildJsonObject { put("command", "head -c 180000 /dev/zero | tr '\\0' x; printf '\\nFINAL_ERROR\\n'; exit 7") })
            assertEquals(true, big.isError)
            assertTrue(big.text().contains("FINAL_ERROR"))
            log = File(big.details!!.jsonObject.getValue("fullOutputPath").jsonPrimitive.content)
            assertEquals(180013L, log.length())
            assertTrue(run(tools.getValue("read"), buildJsonObject { put("path", log.absolutePath); put("offset", 2); put("limit", 1) }).text().contains("FINAL_ERROR"))
            assertTrue(run(tools.getValue("bash"), buildJsonObject { put("command", "tail -c 32 '${log.absolutePath}'") }).text().contains("FINAL_ERROR"))
        } finally { file.delete(); log?.delete(); downloaded.delete(); graph.workshopStore.delete(draft.manifest.id); draft.directory.deleteRecursively() }
    }

    @Test
    fun linuxReadsAppDataThroughAndroidAliases() = runBlocking {
        val tools = tools(linux())
        val file = File(graph.homeDir, "alias-${UUID.randomUUID()}.txt")
        try {
            file.writeText("alias-marker\n")
            val alias = "/data/data/${context.packageName}/files/home/${file.name}"
            val aliased = run(tools.getValue("bash"), buildJsonObject { put("command", "cat '$alias'") })
            assertNull(aliased.text(), aliased.isError)
            assertTrue(aliased.text().contains("alias-marker"))
            val native = run(tools.getValue("bash"), buildJsonObject { put("command", "ls '/data/user/0/${context.packageName}/files'") })
            assertNull(native.text(), native.isError)
            assertTrue(native.text().contains("home"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun timeoutCancellationAndBackgroundCleanupStopRealProcesses() = runBlocking {
        val environment = linux()
        val bash = tools(environment).getValue("bash")
        val timeout = run(bash, buildJsonObject { put("command", "printf partial; sleep 20"); put("timeout", 1) })
        assertEquals(true, timeout.isError)
        assertTrue(timeout.text().contains("partial"))
        assertTrue(timeout.text().contains("timed out"))
        val pidFile = File(graph.homeDir, "contract-pid-${UUID.randomUUID()}")
        val signal = RunSignal()
        try {
            val running = async {
                runCatching { bash.execute("cancel", buildJsonObject { put("command", "echo $$ > '${pidFile.absolutePath}'; sleep 60") }, signal, null) }
            }
            withTimeout(5000) { while (!pidFile.exists() || pidFile.length() == 0L) delay(30) }
            val pid = pidFile.readText().trim().toLong()
            signal.abort("contract cancellation")
            assertTrue(running.await().exceptionOrNull() is RequestAbortedException)
            assertTrue(environment.execute("kill -0 $pid 2>/dev/null").exitCode != 0)
            pidFile.delete()
            val registry = JobRegistry(this, LinuxJobRunner(environment), notifier = { _, _, _ -> })
            registry.start("contract", "echo $$ > '${pidFile.absolutePath}'; sleep 60", "neutral")
            withTimeout(5000) { while (!pidFile.exists() || pidFile.length() == 0L) delay(30) }
            val jobPid = pidFile.readText().trim().toLong()
            registry.finishSession("contract")
            assertTrue(registry.jobs.value.isEmpty())
            assertTrue(environment.execute("kill -0 $jobPid 2>/dev/null").exitCode != 0)
            val root = io.github.whywood.deepdeep.shell.RootShell()
            assertEquals(io.github.whywood.deepdeep.shell.RootStatus.Granted, root.probe())
            val rootTool = io.github.whywood.deepdeep.runtime.rootShellTool(
                executor = io.github.whywood.deepdeep.runtime.RootCommandExecutor { command, timeout ->
                    val result = root.execute(command, timeout)
                    io.github.whywood.deepdeep.runtime.RootCommandResult(result.exitCode, result.stdout, result.stderr, result.truncated, result.timedOut)
                }, enabled = { true }, status = { io.github.whywood.deepdeep.runtime.RootToolStatus.Granted },
            )
            val rootTimeout = run(rootTool, buildJsonObject { put("command", "printf root-partial; sleep 20"); put("timeoutMs", 1000) })
            assertEquals(true, rootTimeout.isError)
            assertTrue(rootTimeout.text(), rootTimeout.text().contains("root-partial") && rootTimeout.text().contains("timed out"))
            pidFile.delete()
            val rootSignal = RunSignal()
            val rootRun = async { runCatching { rootTool.execute("root-cancel", buildJsonObject { put("command", "echo $$ > '${pidFile.absolutePath}'; sleep 60") }, rootSignal, null) } }
            withTimeout(5000) { while (!pidFile.exists() || pidFile.length() == 0L) delay(30) }
            val rootPid = pidFile.readText().trim().toLong()
            rootSignal.abort("contract root cancellation")
            assertTrue(rootRun.await().exceptionOrNull() is RequestAbortedException)
            assertTrue(root.execute("kill -0 $rootPid 2>/dev/null", 3000).exitCode != 0)

        } finally { signal.abort("cleanup"); pidFile.delete() }
    }

    @Test
    fun mainScreenWorksAndRefsCannotCrossDisplays() = runBlocking {
        graph.privilege.refresh()
        ensureAccessibilityConnected()
        val engine = VirtualScreenEngine(context, PrivilegedLauncher(context, graph.adbBridge, graph.privilege))
        val virtual = ScreenRouter(AccessibilityScreenBackend(GuiAccessibilityActions()), VirtualEngineBackend { engine }, AndroidScreenAppCatalog(context))
        val main = io.github.whywood.deepdeep.screen.MainScreenController(
            GuiAccessibilityActions(), AndroidScreenAppCatalog(context),
            {
                val metrics = android.util.DisplayMetrics()
                (context.getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager)
                    .getDisplay(0).getRealMetrics(metrics)
                metrics.widthPixels to metrics.heightPixels
            }, { virtual.status() },
            io.github.whywood.deepdeep.screen.PrivilegedMainScreenShell(graph.privilege, io.github.whywood.deepdeep.shell.RootShell(), graph.adbBridge),
        )
        val fixture = InstrumentationRegistry.getInstrumentation().context.packageName
        val tools = screenTools(virtual, File(context.filesDir, "gui/screenshots"), main).associateBy { it.name }
        fun mainArgs(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {}) = buildJsonObject { put("target", "main"); block() }
        var capture: File? = null
        try {
            val launched = run(tools.getValue("screen_launch"), mainArgs { put("packageName", fixture) })
            assertNull(launched.text(), launched.isError)
            val ready = run(tools.getValue("screen_wait"), mainArgs { put("forPackage", fixture); put("forText", "Initial neutral"); put("timeoutMs", 10000) })
            assertTrue(ready.text(), ready.text().contains("\"ok\":true"))
            val observed = run(tools.getValue("screen_observe"), mainArgs { put("class", "EditText"); put("desc", "Contract input") })
            val ref = Json.parseToJsonElement(observed.text()).jsonObject.getValue("nodes").jsonArray.single().jsonObject.getValue("ref").jsonPrimitive.content
            assertTrue(ref.startsWith("0:"))
            assertTrue(observed.text().contains("\"target\":\"main\""))
            val typed = run(tools.getValue("screen_type"), mainArgs { put("ref", ref); put("text", "主屏中性中文") })
            assertNull(typed.text(), typed.isError)
            assertTrue(typed.text().contains("\"verified\":true"))
            assertNull(run(tools.getValue("screen_tap"), mainArgs { put("text", "确认测试") }).isError)
            val confirmed = run(tools.getValue("screen_wait"), mainArgs { put("forText", "已确认：主屏中性中文") })
            assertTrue(confirmed.text(), confirmed.text().contains("\"ok\":true"))
            val shot = run(tools.getValue("screen_screenshot"), mainArgs())
            assertNull(shot.text(), shot.isError)
            capture = File(Json.parseToJsonElement(shot.text()).jsonObject.getValue("path").jsonPrimitive.content)
            assertTrue(capture.length() > 100)
            File(context.cacheDir, "dual-screen-main-proof.png").writeBytes(capture.readBytes())
            assertEquals(0, linux().execute("test -s '${capture.absolutePath}'").exitCode)
            assertNull(run(tools.getValue("screen_tap"), mainArgs { put("x", 10); put("y", 10) }).isError)
            assertNull(run(tools.getValue("screen_swipe"), mainArgs { put("x1", 500); put("y1", 1700); put("x2", 500); put("y2", 1400) }).isError)
            assertNull(run(tools.getValue("screen_scroll"), mainArgs { put("direction", "up") }).isError)
            assertNull(run(tools.getValue("screen_key"), mainArgs { put("key", "home") }).isError)
            assertTrue(engine.start().ok)
            val opened = run(tools.getValue("screen_launch"), buildJsonObject { put("packageName", fixture) })
            assertNull(opened.text(), opened.isError)
            val virtualReady = run(tools.getValue("screen_wait"), buildJsonObject { put("forPackage", fixture); put("timeoutMs", 10000) })
            assertTrue(virtualReady.text(), virtualReady.text().contains("\"ok\":true"))
            val virtualSnapshot = run(tools.getValue("screen_observe"))
            val virtualRef = Json.parseToJsonElement(virtualSnapshot.text()).jsonObject.getValue("nodes").jsonArray.first().jsonObject.getValue("ref").jsonPrimitive.content
            assertEquals(true, run(tools.getValue("screen_tap"), mainArgs { put("ref", virtualRef) }).isError)
            assertEquals(true, run(tools.getValue("screen_tap"), buildJsonObject { put("ref", ref) }).isError)
            assertEquals(true, run(tools.getValue("screen_tap"), mainArgs { put("ref", ref) }).isError)
            val noMove = run(tools.getValue("screen_launch"), mainArgs { put("packageName", fixture) })
            assertEquals(noMove.text(), true, noMove.isError)
            val stillVirtual = run(tools.getValue("screen_observe"), buildJsonObject { put("packageName", fixture) })
            assertNull(stillVirtual.text(), stillVirtual.isError)
        } finally { engine.stop(); capture?.delete() }
    }

    @Test
    fun virtualScreenHandlesNeutralChineseFormWithoutMainFallback() = runBlocking {
        graph.privilege.refresh()
        val engine = VirtualScreenEngine(context, PrivilegedLauncher(context, graph.adbBridge, graph.privilege))
        val controller = ScreenRouter(AccessibilityScreenBackend(GuiAccessibilityActions()), VirtualEngineBackend { engine }, AndroidScreenAppCatalog(context), { GuiAccessibilityState.isEnabled(context) })
        val screenshotDir = File(context.filesDir, "gui/screenshots")
        val tools = screenTools(controller, screenshotDir).associateBy { it.name }
        var screenshot: File? = null
        val fixturePackage = InstrumentationRegistry.getInstrumentation().context.packageName
        try {
            assertTrue(run(tools.getValue("screen_list_apps")).text().contains(fixturePackage))
            assertEquals(true, run(tools.getValue("screen_tap"), buildJsonObject { put("x", 1); put("y", 1) }).isError)
            assertEquals(true, run(tools.getValue("screen_status"), buildJsonObject { put("target", "main") }).isError)
            val started = engine.start()
            assertTrue(started.message, started.ok)
            assertNull(run(tools.getValue("screen_launch"), buildJsonObject { put("packageName", fixturePackage) }).isError)
            ensureAccessibilityConnected()
            val wait = run(tools.getValue("screen_wait"), buildJsonObject { put("forPackage", fixturePackage); put("forText", "Initial neutral"); put("timeoutMs", 10000) })
            assertTrue(wait.text(), Json.parseToJsonElement(wait.text()).jsonObject.getValue("ok").jsonPrimitive.content == "true")
            val observed = run(tools.getValue("screen_observe"), buildJsonObject { put("class", "EditText"); put("desc", "Contract input") })
            val input = Json.parseToJsonElement(observed.text()).jsonObject.getValue("nodes").jsonArray.single().jsonObject
            val ref = input.getValue("ref").jsonPrimitive.content
            val typed = run(tools.getValue("screen_type"), buildJsonObject { put("ref", ref); put("text", "中性测试中文") })
            assertNull(typed.text(), typed.isError)
            assertTrue(typed.text().contains("\"verified\":true"))
            assertNull(run(tools.getValue("screen_tap"), buildJsonObject { put("text", "确认测试") }).isError)
            val confirmed = run(tools.getValue("screen_wait"), buildJsonObject { put("forText", "已确认：中性测试中文") })
            assertTrue(confirmed.text().contains("\"ok\":true"))
            run(tools.getValue("screen_observe"))
            assertEquals(true, run(tools.getValue("screen_type"), buildJsonObject { put("ref", ref); put("text", "旧引用不应写入") }).isError)
            assertNull(run(tools.getValue("screen_tap"), buildJsonObject { put("x", 10); put("y", 10) }).isError)
            val capture = run(tools.getValue("screen_screenshot"))
            assertNull(capture.text(), capture.isError)
            screenshot = File(Json.parseToJsonElement(capture.text()).jsonObject.getValue("path").jsonPrimitive.content)
            assertTrue(screenshot.length() > 100)
            File(context.cacheDir, "contract-proof.png").writeBytes(screenshot.readBytes())
            assertNull(run(tools.getValue("screen_scroll"), buildJsonObject { put("direction", "down") }).isError)
            assertEquals(0, linux().execute("test -s '${screenshot.absolutePath}'").exitCode)
            assertEquals(true, run(tools.getValue("screen_key"), buildJsonObject { put("key", "home") }).isError)
            assertNull(run(tools.getValue("screen_key"), buildJsonObject { put("key", "back") }).isError)
            engine.stop()
            assertEquals(true, run(tools.getValue("screen_tap"), buildJsonObject { put("x", 1); put("y", 1) }).isError)
            assertTrue(engine.start().ok)
            assertEquals(true, run(tools.getValue("screen_tap"), buildJsonObject { put("ref", ref) }).isError)
        } finally { engine.stop(); screenshot?.delete() }
    }

    private suspend fun ensureAccessibilityConnected() {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        if (!GuiAccessibilityState.connected.value) {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            assertTrue("Accessibility must already be authorized", enabled.contains(context.packageName))
            val withoutOwn = enabled.split(':').filterNot { it.startsWith(context.packageName + "/") }.joinToString(":")
            fun shell(command: String) {
                val descriptor = automation.executeShellCommand(command)
                val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().decodeToString() }
                assertFalse(output, output.contains("Error") || output.contains("Exception"))
            }
            if (withoutOwn.isEmpty()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services $withoutOwn")
            delay(3000)
            shell("settings put secure enabled_accessibility_services $enabled")
            shell("settings put secure accessibility_enabled 1")
        }
        withTimeout(10000) { while (!GuiAccessibilityState.connected.value) delay(100) }
    }
}
