package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolTextPolicyTest {
    private val forbidden = listOf(
        "helper",
        "daemon",
        "uid 2000",
        "uiautomator",
        "displayId",
        "taskDisplayId",
        "nodeBackend",
        "inputBackend",
        "FLAG_SECURE",
        "CalendarContract",
        "proot",
        "pi2kotlin",
        "engine",
    )

    private lateinit var root: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        root = Files.createTempDirectory("deepdeep-tool-text").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun runtimeToolTextStaysTaskOriented() {
        tools().forEach { tool ->
            assertTrue("${tool.name} description is too long", tool.description.length <= 240)
            assertNull("${tool.name} should not send promptSnippet", tool.promptSnippet)
            assertNull("${tool.name} should not send promptGuidelines", tool.promptGuidelines)
            scan(tool.name, tool.description)
            scan(tool.name, tool.parameters)
        }
    }

    private fun scan(tool: String, text: String) {
        val lower = text.lowercase()
        forbidden.forEach { term ->
            assertTrue("$tool contains forbidden term '$term': $text", !lower.contains(term))
        }
    }

    private fun scan(tool: String, element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                scan(tool, key)
                scan(tool, value)
            }
            is JsonArray -> element.forEach { scan(tool, it) }
            is JsonPrimitive -> scan(tool, element.toString())
        }
    }

    private fun tools(): List<CodingTool> {
        val binding = SessionBinding()
        val registry = JobRegistry(scope = scope, notifier = { _, _, _ -> })
        val store = WorkshopStore(File(root, "workshop"))
        val controller = object : PluginController {
            override val setupPluginIds: List<String> = listOf("feishu", "wecom", "micloud")

            override suspend fun setup(pluginId: String): PluginSetupResult =
                PluginSetupResult(true, pluginId, true, true)
        }
        return buildList {
            add(pluginSetupTool(controller)!!)
            add(askUserQuestionTool(QuestionCenter(), binding))
            add(presentTool(null) { null })
            addAll(goalTools(binding, GoalService()))
            addAll(jobTools(binding, registry))
            addAll(workshopTools(store))
            add(workshopEditTool(store))
            add(
                adbShellTool(
                    executor = RootCommandExecutor { _, _ -> RootCommandResult(0, "", "", false) },
                    enabled = { true },
                    status = { RootToolStatus.Granted },
                ),
            )
            add(
                rootShellTool(
                    executor = RootCommandExecutor { _, _ -> RootCommandResult(0, "", "", false) },
                    enabled = { true },
                    status = { RootToolStatus.Granted },
                ),
            )
        }
    }

}
