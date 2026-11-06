package io.github.whywood.deepdeep.data.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionExporterTest {
    @Test
    fun suggestedFileNameKeepsReadableTitleAndSessionId() {
        assertEquals("chat-abc.jsonl", SessionExporter.suggestedFileName("chat", "abc"))
    }

    @Test
    fun suggestedFileNameStripsPathCharactersAndTrimsLength() {
        val name = SessionExporter.suggestedFileName("a/b:c*d?e\"f<g>h|i\n", "id")
        assertEquals("a_b_c_d_e_f_g_h_i-id.jsonl", name)
    }

    @Test
    fun suggestedFileNameStripsTrailingPunctuation() {
        assertEquals("hello-id.jsonl", SessionExporter.suggestedFileName("hello, ", "id"))
    }

    @Test
    fun suggestedFileNameFallsBackWhenTitleIsBlank() {
        assertEquals("session-id.jsonl", SessionExporter.suggestedFileName("   ", "id"))
    }
}
