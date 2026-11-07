package io.github.whywood.deepdeep.runtime.memory

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryStoreTest {
    private lateinit var directory: File
    private lateinit var store: MemoryStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-memory").toFile()
        store = MemoryStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun ensureCreatesZoneFilesWithFixedHeadings() = runTest {
        store.ensure()

        assertEquals("# 用户与我", File(directory, "profile.md").readText().trim())
        assertEquals("# 当下与将来", File(directory, "context.md").readText().trim())
        assertEquals("# 经验与教训", File(directory, "cookbook.md").readText().trim())
    }

    @Test
    fun writeTodayAppendsMarkerAndRejectsOtherModes() = runTest {
        store.ensure()

        val message = store.write("today", "- 喝了一杯水", MemoryWriteMode.APPEND, null, "s1", null)
        assertTrue(message.contains("日记"))

        val text = store.read("today", null, null)
        assertTrue(text.contains("喝了一杯水"))
        assertTrue(text.contains("<!--"))

        val rejected = store.write("today", "x", MemoryWriteMode.OVERWRITE, null, "s1", null)
        assertTrue(rejected.contains("append"))
    }

    @Test
    fun appendProfileKeepsHeadingAndContent() = runTest {
        store.ensure()

        store.write("profile", "## 身份\n- 学生", MemoryWriteMode.APPEND, null, null, null)
        store.write("profile", "## 偏好\n- 先给最小方案", MemoryWriteMode.APPEND, null, null, null)

        val profile = File(directory, "profile.md").readText()
        assertTrue(profile.startsWith("# 用户与我"))
        assertTrue(profile.contains("## 身份"))
        assertTrue(profile.contains("## 偏好"))
    }

    @Test
    fun replaceSectionKeepsUnrelatedSections() = runTest {
        store.ensure()
        store.write(
            "profile",
            "## 身份\n- 学生\n\n## 偏好\n- 简单方案",
            MemoryWriteMode.OVERWRITE,
            null,
            null,
            null,
        )

        store.write("profile", "- 大三学生", MemoryWriteMode.REPLACE_SECTION, "身份", null, null)

        val profile = File(directory, "profile.md").readText()
        assertTrue(profile.contains("- 大三学生"))
        assertFalse(profile.contains("- 学生\n"))
        assertTrue(profile.contains("- 简单方案"))
    }

    @Test
    fun overwriteRestoresZoneHeadingWhenMissing() = runTest {
        store.ensure()

        store.write("context", "- 在做一个项目", MemoryWriteMode.OVERWRITE, null, null, null)

        val context = File(directory, "context.md").readText()
        assertTrue(context.startsWith("# 当下与将来"))
        assertTrue(context.contains("- 在做一个项目"))
    }

    @Test
    fun readMonthAndYearUseTimeTree() = runTest {
        store.ensure()
        store.appendDayBlock("2026-10-31", "- 月末的事")
        store.appendMonthBlock("2026-10-31", "- 十月的摘要")
        store.appendYearBlock("2026-10", "- 2026 年的回顾")

        assertTrue(store.read("month", null, "2026-10-15").contains("十月的摘要"))
        assertTrue(store.read("year", null, "2026-05-01").contains("2026 年的回顾"))
    }

    @Test
    fun searchFindsCuratedAndTimeFiles() = runTest {
        store.ensure()
        store.write("profile", "- 在中山大学读书", MemoryWriteMode.APPEND, null, null, null)
        store.appendDayBlock("2026-11-06", "- 讨论 gradlew-locked 的用法")

        val chinese = store.search("中山", 5)
        assertTrue(chinese.hits.isNotEmpty())

        val latin = store.search("gradlew", 5)
        assertTrue(latin.hits.isNotEmpty())
        assertTrue(latin.hits.first().fileName.contains("2026-11-06"))
    }

    @Test
    fun appendMonthBlockMarksDayAndMatchesHeading() = runTest {
        store.ensure()
        store.appendDayBlock("2026-11-06", "- 一天的事")
        store.appendMonthBlock("2026-11-06", "- 六号摘要")

        assertTrue(store.monthHasDay("2026-11-06"))
        assertFalse(store.monthHasDay("2026-11-05"))
        val month = store.readMonth("2026-11")
        assertTrue(month!!.contains("## 2026-11-06"))
        assertTrue(month.contains("- 六号摘要"))
    }

    @Test
    fun appendYearBlockMarksMonthAndMatchesHeading() = runTest {
        store.ensure()
        store.appendYearBlock("2026-10", "- 十月回顾")

        assertTrue(store.yearHasMonth("2026-10"))
        assertFalse(store.yearHasMonth("2026-11"))
        assertTrue(store.readYear("2026")!!.contains("## 2026-10"))
    }

    @Test
    fun pendingRecapRoundTrip() = runTest {
        store.ensure()
        store.savePendingRecap("sess-1", null, "User: 你好", "e-1", "自动沉淀")

        val pending = store.pendingRecaps()
        assertEquals(1, pending.size)
        assertEquals("sess-1", pending.first().sessionId)
        assertEquals("User: 你好", pending.first().transcript)
        assertEquals("e-1", pending.first().lastEntryId)

        store.deletePendingRecap("sess-1")
        assertTrue(store.pendingRecaps().isEmpty())
    }

    @Test
    fun ensureRemovesLegacyFilesButKeepsTimeTree() = runTest {
        store.ensure()
        File(directory, "playbook.md").writeText("# old")
        val flat = File(directory, "daily/2026-11-06.md")
        flat.parentFile?.mkdirs()
        flat.writeText("# old day")
        store.appendDayBlock("2026-11-07", "- 新格式")

        store.ensure()

        assertFalse(File(directory, "playbook.md").exists())
        assertFalse(flat.exists())
        assertTrue(File(directory, "daily/2026/11/2026-11/2026-11-07.md").isFile)
    }

    @Test
    fun statusCountsZonesAndDays() = runTest {
        store.ensure()
        store.write("profile", "- 学生", MemoryWriteMode.APPEND, null, null, null)
        store.appendDayBlock("2026-11-06", "- 一天")
        store.appendDayBlock("2026-11-07", "- 又一天")

        val status = store.status()

        assertTrue(status.profileChars > 0)
        assertEquals(2, status.dayCount)
        assertTrue(status.totalBytes > 0)
    }

    @Test
    fun injectedSectionCarriesZonesWithoutFrameHeader() = runTest {
        store.ensure()
        store.write("profile", "## 身份\n- 学生", MemoryWriteMode.APPEND, null, null, null)
        store.write("cookbook", "## 速查\n- 先读 AGENTS.md", MemoryWriteMode.APPEND, null, null, null)
        store.appendDayBlock(MemoryText.todayKey(), "- 今天发生的事")

        val section = store.section()

        assertTrue(section != null)
        assertTrue(section!!.contains("# 用户与我"))
        assertTrue(section.contains("# 经验与教训"))
        assertTrue(section.contains("# 日记"))
        assertTrue(section.contains("## 今天（"))
        assertFalse(section.contains("Your Memory"))
        assertFalse(section.contains("playbook"))
        assertFalse(section.contains("## 昨天"))
    }

    @Test
    fun readMissingZoneReturnsPlaceholder() = runTest {
        store.ensure()

        assertNull(store.readDay("2026-01-01"))
        assertTrue(store.read("today", null, "2026-01-01").contains("还没有记录"))
    }
}
