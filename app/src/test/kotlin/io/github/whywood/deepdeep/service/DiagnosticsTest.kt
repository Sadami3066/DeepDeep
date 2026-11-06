package io.github.whywood.deepdeep.service

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    @Test
    fun appendsMessageClassAndSanitizedDetail() {
        val dir = Files.createTempDirectory("diagnostics").toFile()
        Diagnostics.log(dir, "process: start failed", IllegalStateException("line one\nline two"))
        val text = File(dir, "errors.log").readText()
        assertTrue(text.contains("process: start failed"))
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("line one line two"))
        assertEquals(1, text.lines().count { it.isNotBlank() })
    }

    @Test
    fun keepsOnlyTailWhenFileGrowsPastLimit() {
        val dir = Files.createTempDirectory("diagnostics").toFile()
        val file = File(dir, "errors.log")
        file.writeText((1..500).joinToString("\n") { "old-$it" } + "\n")
        Diagnostics.log(dir, "trim", IllegalStateException("boom"))
        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(400, lines.size)
        assertTrue(lines.last().contains("trim"))
    }
}
