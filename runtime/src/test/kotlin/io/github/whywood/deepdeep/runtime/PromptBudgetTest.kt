package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RunSignal
import dev.pi.coding.tools.FileMutationQueue
import dev.pi.coding.tools.ShellExecutor
import dev.pi.coding.tools.ShellResult
import dev.pi.coding.tools.defaultCodingTools
import java.io.File
import java.nio.file.Files
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PromptBudgetTest {
    private lateinit var directory: File
    private lateinit var cwd: Path

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-prompt-budget").toFile()
        cwd = directory.absolutePath.toPath()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun globalRulesStayShort() {
        assertTrue(GLOBAL_TOOL_RULES.size <= 5)
        GLOBAL_TOOL_RULES.forEach { rule -> assertTrue(rule.length <= 220) }
    }

    @Test
    fun fileAndBashRulesStayBoundedAndDeduplicated() {
        val tools = defaultCodingTools(cwd, FileSystem.SYSTEM, FileMutationQueue())
            .filter { it.name != "bash" }
            .map { it.withAbsolutePaths() } +
            deepDeepBashTool(NoopShell)
        val rules = buildToolRuleSection(tools).getValue("rules")
        val bullets = rules.lines().filter { it.startsWith("- ") }

        assertEquals(bullets.size, bullets.distinct().size)
        assertTrue("rules section too long: ${rules.length}", rules.length <= 2_200)
        assertTrue(bullets.any { it.contains("absolute paths") })
        assertTrue(bullets.any { it.contains("[exit: N]") })
        assertFalse(rules.contains("job_start"))
        assertFalse(rules.contains("schedule_create"))
    }

    private object NoopShell : ShellExecutor {
        override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult =
            ShellResult(output = "", exitCode = 0)
    }
}
