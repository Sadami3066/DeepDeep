package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.ToolTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresentCardParseTest {
    @Test
    fun parsesPresentedFiles() {
        val files = requireNotNull(parsePresentedFiles(trace()))
        assertEquals(3, files.size)
        assertEquals(PresentedFile(path = "reports/q3.md", name = "q3.md"), files[0])
        assertEquals(PresentedFile(path = "slides.pptx", name = "slides.pptx"), files[1])
        assertEquals("demo", files[2].name)
    }

    @Test
    fun otherTraceOrMissingOutputReturnsNull() {
        assertNull(parsePresentedFiles(trace(name = "bash")))
        assertNull(
            parsePresentedFiles(
                ToolTrace(id = 1L, name = "present", arguments = emptyMap(), output = null),
            ),
        )
    }

    @Test
    fun malformedOrEmptyOutputReturnsNull() {
        assertNull(parsePresentedFiles(trace(output = "not json")))
        assertNull(parsePresentedFiles(trace(output = """{"files":[]}""")))
        assertNull(parsePresentedFiles(trace(output = """{"files":[{"path":"  "}]}""")))
        assertNull(parsePresentedFiles(trace(output = """{"files":{}}""")))
        assertNull(parsePresentedFiles(trace(output = """{"files":[{"path":"a.md"},{"name":"b.md"}]}""")))
    }

    @Test
    fun pathIsTrimmedAndDirectoryNameDerived() {
        val file = requireNotNull(
            parsePresentedFiles(
                trace(output = """{"files":[{"path":"  out/demo/  "}]}"""),
            ),
        ).single()
        assertEquals("out/demo/", file.path)
        assertEquals("demo", file.name)
    }

    private fun trace(
        output: String? = """
            {"files":[
              {"path":"reports/q3.md"},
              {"path":"slides.pptx"},
              {"path":"build/demo/"}
            ]}
        """.trimIndent(),
        name: String = "present",
    ): ToolTrace = ToolTrace(id = 1L, name = name, arguments = emptyMap(), output = output)
}
