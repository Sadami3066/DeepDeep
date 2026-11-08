package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.FileMutationQueue
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import io.github.whywood.deepdeep.sdk.coding.tools.defaultCodingTools
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
