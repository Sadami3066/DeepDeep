package io.github.whywood.deepdeep.ui.sessions

import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.ui.chat.SessionSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSearchTest {
    @Test
    fun blankQueryReturnsEverySession() {
        val hits = listOf(hit("Weekly plan", "默认"), hit("Trip notes", "写作"))
        assertEquals(hits, filterSessionSearch(hits, ""))
        assertEquals(hits, filterSessionSearch(hits, "   "))
    }

    @Test
    fun queryIsTrimmedBeforeMatching() {
        val hits = listOf(hit("Weekly plan", "默认"), hit("Trip notes", "写作"))
        assertEquals(listOf(hits.first()), filterSessionSearch(hits, "  weekly  "))
    }

    @Test
    fun titleMatchIgnoresCase() {
        val hits = listOf(hit("Weekly Plan", "默认"), hit("trip notes", "写作"))
        assertEquals(listOf(hits[1]), filterSessionSearch(hits, "TRIP"))
        assertEquals(listOf(hits[0]), filterSessionSearch(hits, "weekly plan"))
    }

    @Test
    fun partialTitleMatchWorks() {
        val hits = listOf(hit("Markdown 学习笔记", "默认"), hit("Kotlin coroutines", "研究"))
        assertEquals(listOf(hits[0]), filterSessionSearch(hits, "markdown"))
    }

    @Test
    fun workspaceNameIsNeverMatched() {
        val hits = listOf(hit("Weekly plan", "写作空间"), hit("Trip notes", "旅行"))
        assertTrue(filterSessionSearch(hits, "写作空间").isEmpty())
        assertTrue(filterSessionSearch(hits, "旅行").isEmpty())
    }

    private fun hit(title: String, workspace: String, updatedAt: Long = 0L): SessionSearchHit =
        SessionSearchHit(
            conversation = ConversationSummary(
                id = title,
                title = title,
                updatedAt = updatedAt,
                filePath = "$title.jsonl",
            ),
            workspaceName = workspace,
        )
}
