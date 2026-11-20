package io.github.whywood.superpi.runtime.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRenderTest {
    private fun render(
        profile: String = "",
        context: String = "",
        cookbook: String = "",
        todayText: String = "",
        monthText: String = "",
        yearText: String = "",
    ): String = renderMemorySection(
        profile = profile,
        context = context,
        cookbook = cookbook,
        todayText = todayText,
        todayLabel = "2026-11-08",
        monthText = monthText,
        monthLabel = "2026-11",
        yearText = yearText,
        yearLabel = "2026",
    )!!

    @Test
    fun zoneHeadingIsNotDuplicated() {
        val text = render(profile = "# You & I\n\n喜欢简洁")
        assertEquals(1, Regex("(?m)^# You & I$").findAll(text).count())
        assertTrue(text.contains("喜欢简洁"))
    }

    @Test
    fun oldLevelOneHeadingIsDroppedAndReplacedWithZoneTitle() {
        val text = render(profile = "# 用户与我\n\n喜欢简洁")
        assertTrue(text.contains("# You & I"))
        assertFalse(text.contains("用户与我"))
        assertTrue(text.contains("喜欢简洁"))
    }

    @Test
    fun diaryHeadingsAreNestedBelowDiarySection() {
        val text = render(
            todayText = "# 2026-11-08\n\n<!-- 2026-11-08 09:00:00 [abc] -->\n\n## Session recap (auto)\n\n内容",
        )
        assertTrue(text.contains("## Today (2026-11-08)"))
        assertTrue(text.contains("### Session recap (auto)"))
        assertFalse(text.contains("\n# 2026-11-08"))
        assertFalse(text.contains("<!--"))
        assertFalse(text.contains("&lt;!--"))
        assertTrue(text.contains("2026-11-08 09:00:00 [abc]"))
    }

    @Test
    fun monthAndYearHeadingsAreNestedUnderTheirDiarySection() {
        val text = render(
            monthText = "# 2026-11\n\n## 2026-11-05\n\n内容甲",
            yearText = "# 2026\n\n## 2026-10\n\n内容乙",
        )
        assertTrue(text.contains("## This month (2026-11)"))
        assertTrue(text.contains("### 2026-11-05"))
        assertTrue(text.contains("## This year (2026)"))
        assertTrue(text.contains("### 2026-10"))
    }

    @Test
    fun headingsInsideCodeFencesStayUntouched() {
        val text = render(todayText = "# 2026-11-08\n\n```\n## 不是标题\n```")
        assertTrue(text.contains("## 不是标题"))
        assertFalse(text.contains("### 不是标题"))
    }

    @Test
    fun truncatedZoneUsesEnglishMarker() {
        val text = render(profile = "# You & I\n\n" + "字".repeat(3_000))
        assertTrue(text.contains("...(truncated)"))
        assertFalse(text.contains("已截断"))
    }
}
