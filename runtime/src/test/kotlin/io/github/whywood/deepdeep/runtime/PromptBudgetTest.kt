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

    private object NoopShell : ShellExecutor {
        override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult =
            ShellResult(output = "", exitCode = 0)
    }
}
