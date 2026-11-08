package io.github.whywood.deepdeep.ui.market

import io.github.whywood.deepdeep.data.MarketCategory
import io.github.whywood.deepdeep.data.MarketPreset
import io.github.whywood.deepdeep.data.filterMarketPresets
import org.junit.Assert.assertEquals
import org.junit.Test

class MarketPresetsTest {

    private val writing = preset("writing", nameRes = 1, summaryRes = 2, tagsRes = 3, category = MarketCategory.Skill)
    private val summary = preset("summary", nameRes = 4, summaryRes = 5, tagsRes = 6, category = MarketCategory.Skill)
    private val search = preset("search", nameRes = 7, summaryRes = 8, tagsRes = 9, category = MarketCategory.Mcp)

    private val presets = listOf(writing, summary, search)

    private val texts = mapOf(
        1 to "写作润色",
        2 to "把草稿打磨成通顺的表达",
        3 to "写作 · 编辑",
        4 to "长文摘要",
        5 to "提炼长文要点",
        6 to "总结 · 阅读",
        7 to "网页搜索",
        8 to "搜索并整理网页内容",
        9 to "搜索 · 检索",
    )

    private val resolve: (MarketPreset) -> String = { preset ->
        listOf(preset.nameRes, preset.summaryRes, preset.tagsRes).joinToString(" ") { texts.getValue(it) }
    }

    @Test
    fun emptyQueryKeepsAllPresets() {
        assertEquals(presets, filterMarketPresets(presets, "", resolve))
    }

    @Test
    fun blankQueryKeepsAllPresets() {
        assertEquals(presets, filterMarketPresets(presets, "   ", resolve))
    }

    @Test
    fun matchesName() {
        assertEquals(listOf(writing), filterMarketPresets(presets, "写作", resolve))
    }

    @Test
    fun matchesSummary() {
        assertEquals(listOf(summary), filterMarketPresets(presets, "要点", resolve))
    }

    @Test
    fun matchesTags() {
        assertEquals(listOf(search), filterMarketPresets(presets, "检索", resolve))
    }

    @Test
    fun ignoresCase() {
        val english = mapOf(1 to "Polish Writing", 2 to "refine drafts", 3 to "Writing")
        val preset = preset("english", nameRes = 1, summaryRes = 2, tagsRes = 3, category = MarketCategory.Plugin)
        assertEquals(
            listOf(preset),
            filterMarketPresets(listOf(preset), "  WRITING ", { english.getValue(it.nameRes) }),
        )
    }

    @Test
    fun noMatchReturnsEmpty() {
        assertEquals(emptyList<MarketPreset>(), filterMarketPresets(presets, "不存在", resolve))
    }

    @Test
    fun resolverCanUseAnyText() {
        assertEquals(listOf(summary), filterMarketPresets(presets, "x", { if (it.id == "summary") "x" else "" }))
    }

    private fun preset(
        id: String,
        nameRes: Int,
        summaryRes: Int,
        tagsRes: Int,
        category: MarketCategory,
    ) = MarketPreset(id = id, nameRes = nameRes, summaryRes = summaryRes, tagsRes = tagsRes, category = category)
}
