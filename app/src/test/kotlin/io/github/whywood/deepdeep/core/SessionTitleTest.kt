package io.github.whywood.deepdeep.core

import io.github.whywood.deepdeep.runtime.ModelOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTitleTest {
    private fun model(provider: String, id: String, name: String) =
        ModelOption(provider, id, name, emptyList())

    @Test
    fun prefersDeepSeekFlashThenFlashThenHaikuThenTerra() {
        val ranked = SessionTitleModels.rank(
            listOf(
                model("p", "terra", "GPT Terra"),
                model("p", "haiku", "Claude Haiku"),
                model("p", "flash-lite", "Gemini Flash Lite"),
                model("p", "deepseek-flash", "DeepSeek V4 Flash"),
            ),
        )
        assertEquals(
            listOf("deepseek-flash", "flash-lite", "haiku", "terra"),
            ranked.map { it.id },
        )
        assertEquals("deepseek-flash", SessionTitleModels.preferred(ranked)?.id)
    }

    @Test
    fun candidatesExcludeUnscoredModelsInRankOrder() {
        val candidates = SessionTitleModels.candidates(
            listOf(
                model("p", "pro", "DeepSeek Pro"),
                model("p", "terra", "GPT Terra"),
                model("p", "flash", "GLM Flash"),
                model("p", "deepseek-flash", "DeepSeek V4.1 Flash"),
            ),
        )
        assertEquals(listOf("deepseek-flash", "flash", "terra"), candidates.map { it.id })
    }

    @Test
    fun prefersAnyScoredModelOverUnscored() {
        val preferred = SessionTitleModels.preferred(
            listOf(model("p", "pro", "DeepSeek Pro"), model("p", "haiku", "Claude Haiku")),
        )
        assertEquals("haiku", preferred?.id)
        assertNull(SessionTitleModels.preferred(listOf(model("p", "pro", "DeepSeek Pro"))))
    }

    @Test
    fun sanitizesModelTitle() {
        assertEquals("修复登录问题", sanitizeSessionTitle("标题：\"修复登录问题\"。"))
        assertEquals("第一行标题", sanitizeSessionTitle("第一行标题\n第二行解释"))
        assertEquals("一二三四五六七八九十一二三四五六七八九十", sanitizeSessionTitle("一二三四五六七八九十一二三四五六七八九十一二三"))
        assertNull(sanitizeSessionTitle("  \n "))
    }
}
