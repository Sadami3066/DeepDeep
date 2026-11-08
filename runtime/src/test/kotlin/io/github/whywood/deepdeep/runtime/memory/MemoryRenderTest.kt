package io.github.whywood.deepdeep.runtime.memory

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
        val text = render(profile = "# 用户与我\n\n喜欢简洁")
        assertEquals(1, Regex("(?m)^# 用户与我$").findAll(text).count())
        assertTrue(text.contains("喜欢简洁"))
    }

    @Test
    fun diaryHeadingsAreNestedBelowDiarySection() {
        val text = render(
            todayText = "# 2026-11-08\n\n<!-- 2026-11-08 09:00:00 [abc] -->\n\n## 会话纪要（自动沉淀）\n\n内容",
        )
        assertTrue(text.contains("## 今天（2026-11-08）"))
        assertTrue(text.contains("### 会话纪要（自动沉淀）"))
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
        assertTrue(text.contains("## 本月（2026-11）"))
        assertTrue(text.contains("### 2026-11-05"))
        assertTrue(text.contains("## 本年（2026）"))
        assertTrue(text.contains("### 2026-10"))
    }

    @Test
    fun headingsInsideCodeFencesStayUntouched() {
        val text = render(todayText = "# 2026-11-08\n\n```\n## 不是标题\n```")
        assertTrue(text.contains("## 不是标题"))
        assertFalse(text.contains("### 不是标题"))
    }
}
