package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolTextPolicyTest {
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
            assertTrue("${tool.name} description is empty", tool.description.isNotBlank())
            assertNull("${tool.name} should not send promptSnippet", tool.promptSnippet)
            tool.promptGuidelines.orEmpty().forEach { guideline ->
                assertTrue("${tool.name} guideline is empty", guideline.isNotBlank())
                assertFalse("${tool.name} guideline should stay one line", guideline.contains("\n"))
            }
            scan(tool.name, tool.description)
            scan(tool.name, tool.parameters)
        }
    }

    private fun scan(tool: String, text: String) {
        assertTrue("$tool contains empty guidance", text.isNotBlank())
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
        val store = MiniappStore(File(root, "miniapp"))
        return buildList {
            add(askUserQuestionTool(QuestionCenter(), binding))
            add(presentTool(null) { null })
            addAll(goalTools(binding, GoalService()))
            addAll(jobTools(binding, registry))
            addAll(miniappTools(store))
            add(miniappEditTool(store))
            add(
                systemShellTool {
                    object : ShellExecutor {
                        override suspend fun run(
                            command: String,
                            timeoutSeconds: Long?,
                            signal: RunSignal?,
                        ): ShellResult = ShellResult(output = "", exitCode = 0, truncated = false)
                    }
                },
            )
        }
    }

}
