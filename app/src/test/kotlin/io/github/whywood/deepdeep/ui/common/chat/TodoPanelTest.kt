package io.github.whywood.deepdeep.ui.common.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoPanelTest {
    @Test
    fun parseTodosReadsContentAndStatus() {
        val payload = """{"todos":[{"content":"买牛奶","status":"pending"},{"content":"写周报","status":"in_progress"}]}"""

        val todos = parseTodos(payload)

        assertEquals(2, todos.size)
        assertEquals("买牛奶", todos[0].content)
        assertEquals("in_progress", todos[1].status)
    }

    @Test
    fun parseTodosSkipsBlankContent() {
        val payload = """{"todos":[{"content":"  ","status":"pending"},{"content":"读书","status":"completed"}]}"""

        val todos = parseTodos(payload)

        assertEquals(1, todos.size)
        assertEquals("读书", todos[0].content)
    }

    @Test
    fun parseTodosToleratesMissingOrBrokenPayload() {
        assertTrue(parseTodos(null).isEmpty())
        assertTrue(parseTodos("not json").isEmpty())
        assertTrue(parseTodos("""{"items":[]}""").isEmpty())
    }
}
