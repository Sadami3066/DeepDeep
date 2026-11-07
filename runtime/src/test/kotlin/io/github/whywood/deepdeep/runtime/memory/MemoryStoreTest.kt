package io.github.whywood.deepdeep.runtime.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MemoryStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun store() = MemoryStore(temp.root)

    @Test
    fun appendDailyWritesMarkerAndContent() = runTest {
        val store = store()

        store.write("daily", "今天讨论记忆系统。", MemoryWriteMode.APPEND, null, "sess-12345678", null)

        val text = store.read("daily", null, null)
        assertTrue(text.contains("今天讨论记忆系统。"))
        assertTrue(text.contains("<!-- "))
        assertTrue(text.contains("[sess-123"))
    }

    @Test
    fun dailyRejectsNonAppendModes() = runTest {
        val store = store()

        val result = store.write("daily", "x", MemoryWriteMode.OVERWRITE, null, null, null)

        assertTrue(result.contains("append"))
        assertTrue(store.read("daily", null, null).contains("还没有记录"))
    }

    @Test
    fun appendCreatesMissingSection() = runTest {
        val store = store()

        store.write("profile", "Milous 偏好简洁。", MemoryWriteMode.APPEND, "用户", "s1", null)

        val section = store.read("profile", "用户", null)
        assertTrue(section.contains("Milous 偏好简洁。"))
    }

    @Test
    fun replaceSectionReplacesOnlyThatSection() = runTest {
        val store = store()
        store.write(
            "context",
            "### 记忆系统\n状态：设计\n\n## 目标\n\n长期目标：做完。",
            MemoryWriteMode.REPLACE_SECTION,
            "进行中的项目",
            "s1",
            null,
        )

        store.write(
            "context",
            "### 记忆系统\n状态：实现",
            MemoryWriteMode.REPLACE_SECTION,
            "进行中的项目",
            "s2",
            null,
        )

        val body = store.read("context", "进行中的项目", null)
        assertTrue(body.contains("状态：实现"))
        assertFalse(body.contains("状态：设计"))
        assertTrue(store.read("context", "目标", null).contains("长期目标：做完。"))
    }

    @Test
    fun overwriteReplacesWholeFile() = runTest {
        val store = store()
        store.write("profile", "旧内容", MemoryWriteMode.OVERWRITE, null, "s1", null)

        store.write("profile", "新内容", MemoryWriteMode.OVERWRITE, null, "s2", null)

        assertEquals("新内容", store.read("profile", null, null))
    }

    @Test
    fun searchMatchesChineseAndLatin() = runTest {
        val store = store()
        store.write("profile", "用户偏好简洁的方案，不喜欢过度设计。", MemoryWriteMode.OVERWRITE, null, "s1", null)
        store.write("playbook", "构建前先跑 gradlew-locked，避免并发 OOM。", MemoryWriteMode.OVERWRITE, null, "s1", null)

        val chinese = store.search("简洁", 5)
        assertTrue(chinese.hits.isNotEmpty())
        assertTrue(chinese.hits.first().snippet.contains("简洁"))

        val latin = store.search("gradlew", 5)
        assertTrue(latin.hits.isNotEmpty())
        assertTrue(latin.hits.first().snippet.contains("gradlew"))
    }

    @Test
    fun injectedSectionShowsProfileQuickRulesAndSnapshotHeader() = runTest {
        val store = store()
        store.write("profile", "## 用户\n\nMilous 偏好简洁。", MemoryWriteMode.OVERWRITE, null, "s1", null)
        store.write(
            "playbook",
            "## 速查\n\n- 先给最小可验证方案。\n\n## 构建\n\n用 gradlew-locked。",
            MemoryWriteMode.OVERWRITE,
            null,
            "s1",
            null,
        )

        val section = store.section()

        assertNotNull(section)
        assertTrue(section!!.contains("用户与协作"))
        assertTrue(section.contains("方法速查"))
        assertTrue(section.contains("先给最小可验证方案"))
        assertFalse(section.contains("用 gradlew-locked"))
        assertTrue(section.contains("快照"))
    }

    @Test
    fun compactionHandoffAppendsFocusAndRecentLog() = runTest {
        val store = store()
        store.write("context", "当前在实现记忆系统。", MemoryWriteMode.REPLACE_SECTION, "近期焦点", "s1", null)
        store.write("daily", "先写了 store 和 search。", MemoryWriteMode.APPEND, null, "s1", null)

        store.onCompaction("sess-12345678")

        val daily = store.read("daily", null, null)
        assertTrue(daily.contains("Session Handoff"))
        assertTrue(daily.contains("当前在实现记忆系统。"))
        assertTrue(daily.contains("先写了 store 和 search。"))
    }

    @Test
    fun statusCountsProjectsAndFiles() = runTest {
        val store = store()
        store.write(
            "context",
            "### 记忆系统\n状态：实现\n\n### 其他项目\n状态：搁置",
            MemoryWriteMode.REPLACE_SECTION,
            "进行中的项目",
            "s1",
            null,
        )

        val status = store.status()

        assertEquals(2, status.activeProjects)
        assertTrue(status.contextChars > 0)
        assertTrue(status.dailyDays >= 0)
    }

    @Test
    fun summaryWatermarkAdvancesWithEachCommit() = runTest {
        val store = store()

        store.commitSummary("s1", "hash-1", "entry-1")

        assertTrue(store.isSummaryDuplicate("s1", "hash-1"))
        assertFalse(store.isSummaryDuplicate("s1", "hash-2"))
        assertEquals("entry-1", store.lastSummarizedEntryId("s1"))

        store.commitSummary("s1", "hash-2", "entry-2")

        assertEquals("entry-2", store.lastSummarizedEntryId("s1"))
    }

    @Test
    fun summaryCommitWithoutEntryDoesNotMoveWatermark() = runTest {
        val store = store()

        store.commitSummary("s1", "hash-1", null)

        assertTrue(store.isSummaryDuplicate("s1", "hash-1"))
        assertNull(store.lastSummarizedEntryId("s1"))
    }
}
