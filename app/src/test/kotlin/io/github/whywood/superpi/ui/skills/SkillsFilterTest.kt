package io.github.whywood.superpi.ui.skills

import io.github.whywood.superpi.data.InstalledSkill
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillsFilterTest {
    private val skills = listOf(
        InstalledSkill("ppt", "Slides", "Build slide decks", false, 1),
        InstalledSkill("web", "Research", "Search the web and summarize", false, 2),
        InstalledSkill("misc", "Notes", "Local note helpers", true, 3),
    )

    @Test
    fun filtersByNameDescriptionAndId() {
        assertEquals(listOf("web"), filterSkills(skills, "research").map { it.id })
        assertEquals(listOf("ppt"), filterSkills(skills, "slide decks").map { it.id })
        assertEquals(listOf("web"), filterSkills(skills, "WEB").map { it.id })
        assertEquals(listOf("misc"), filterSkills(skills, " note ").map { it.id })
        assertEquals(skills, filterSkills(skills, ""))
        assertEquals(emptyList<InstalledSkill>(), filterSkills(skills, "missing"))
    }
}
