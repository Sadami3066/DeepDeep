package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ThinkingLevelTest {
    private val supported = listOf(ThinkingLevel.LOW, ThinkingLevel.HIGH, ThinkingLevel.XHIGH)

    @Test
    fun keepsRequestedLevelWhenSupported() {
        assertEquals(ThinkingLevel.HIGH, supported.resolveThinkingLevel(ThinkingLevel.HIGH))
    }

    @Test
    fun fallsBackToNextHigherSupportedLevel() {
        assertEquals(ThinkingLevel.LOW, supported.resolveThinkingLevel(ThinkingLevel.OFF))
        assertEquals(ThinkingLevel.HIGH, supported.resolveThinkingLevel(ThinkingLevel.MEDIUM))
    }

    @Test
    fun fallsBackToHighestSupportedLevel() {
        assertEquals(ThinkingLevel.XHIGH, supported.resolveThinkingLevel(ThinkingLevel.MAX))
    }

    @Test
    fun keepsRequestedLevelWithoutSupportedLevels() {
        assertEquals(ThinkingLevel.MEDIUM, emptyList<ThinkingLevel>().resolveThinkingLevel(ThinkingLevel.MEDIUM))
    }
}
