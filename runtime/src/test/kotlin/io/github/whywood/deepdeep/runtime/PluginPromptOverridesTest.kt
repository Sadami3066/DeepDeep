package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPromptOverridesTest {
    private val warnings = mutableListOf<String>()

    private fun merge(overrides: PluginPromptOverrides, legacyBody: String? = null): PluginPromptMerge =
        mergePluginPromptOverrides(overrides, legacyBody) { warnings += it }

    private fun contribution(
        pluginId: String,
        priority: Int = 0,
        preamble: String? = null,
        rules: String? = null,
        sections: List<PluginPromptSection> = emptyList(),
    ): PluginPromptContribution = PluginPromptContribution(
        pluginId = pluginId,
        priority = priority,
        preamble = preamble,
        rules = rules,
        sections = sections,
    )

    private fun section(pluginId: String, id: String, body: String, priority: Int): PluginPromptSection =
        PluginPromptSection(pluginId, id, body, priority)

    @Test
    fun emptyOverridesKeepLegacyBody() {
        val merge = merge(PluginPromptOverrides.None, legacyBody = "legacy")
        assertEquals("legacy", merge.pluginsBody)
        assertNull(merge.preamble)
        assertNull(merge.rules)
    }

    @Test
    fun emptyOverridesWithoutLegacyBodyProduceNothing() {
        assertEquals(PluginPromptMerge.None, merge(PluginPromptOverrides.None))
    }

    @Test
    fun preambleUsesHighestPriorityAndLogsConflict() {
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution("low", priority = 1, preamble = "low preamble"),
                    contribution("high", priority = 5, preamble = "high preamble"),
                ),
            ),
        )
        assertEquals("high preamble", merge.preamble)
        assertTrue(warnings.single().contains("multiple plugin prompt preamble overrides"))
        assertTrue(warnings.single().contains("low"))
    }

    @Test
    fun higherPriorityWinsWhenRegisteredLater() {
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution("first", priority = 2, rules = "first rules"),
                    contribution("second", priority = 9, rules = "second rules"),
                ),
            ),
        )
        assertEquals("second rules", merge.rules)
    }

    @Test
    fun equalPriorityUsesRegistrationOrder() {
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution("first", priority = 3, preamble = "first"),
                    contribution("second", priority = 3, preamble = "second"),
                ),
            ),
        )
        assertEquals("first", merge.preamble)
    }

    @Test
    fun sectionsSortedByPriorityAndAppendedAfterLegacyBody() {
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution(
                        "alpha",
                        sections = listOf(
                            section("alpha", "late", "late body", priority = 20),
                            section("alpha", "early", "early body", priority = 1),
                        ),
                    ),
                ),
            ),
            legacyBody = "legacy",
        )
        assertEquals("legacy\n\nearly body\n\nlate body", merge.pluginsBody)
    }

    @Test
    fun duplicateSectionIdKeepsHighestPriorityAndLogs() {
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution("low", priority = 1, sections = listOf(section("low", "shared", "low body", 1))),
                    contribution("high", priority = 5, sections = listOf(section("high", "shared", "high body", 5))),
                ),
            ),
        )
        assertEquals("high body", merge.pluginsBody)
        assertTrue(warnings.single().contains("duplicate plugin prompt section shared"))
    }

    @Test
    fun perPluginOverBudgetIsSkipped() {
        val huge = "x".repeat(PLUGIN_PROMPT_PER_PLUGIN_BUDGET + 1)
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution("huge", priority = 10, preamble = huge),
                    contribution("small", priority = 1, preamble = "small"),
                ),
            ),
        )
        assertEquals("small", merge.preamble)
        assertTrue(warnings.any { it.contains("huge") && it.contains("skipped") })
    }

    @Test
    fun totalBudgetSkipsExcessContributions() {
        val chunk = "x".repeat(30 * 1024)
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution("a", priority = 3, rules = chunk),
                    contribution("b", priority = 2, rules = chunk),
                    contribution("c", priority = 1, rules = chunk),
                ),
            ),
        )
        assertEquals(chunk, merge.rules)
        assertEquals(1, warnings.count { it.contains("exceeds total budget") })
    }

    @Test
    fun blankContentIsIgnored() {
        val merge = merge(
            PluginPromptOverrides(
                listOf(
                    contribution("alpha", preamble = "  ", rules = " ", sections = listOf(section("alpha", "s", " ", 1))),
                ),
            ),
        )
        assertEquals(PluginPromptMerge.None, merge)
    }

    @Test
    fun sectionsKeepBodiesVerbatim() {
        val merge = merge(
            PluginPromptOverrides(
                listOf(contribution("alpha", sections = listOf(section("alpha", "s", "  body  ", 0)))),
            ),
        )
        assertEquals("body", merge.pluginsBody)
    }
}
