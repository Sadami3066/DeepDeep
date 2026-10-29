package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.plugin.PluginSkillFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopPluginTest {
    private val files = mapOf(
        "skills/workshop/SKILL.md" to "# Skill",
        "skills/workshop/references/api.md" to "# API",
        "skills/workshop/examples/01-hello.html" to "<html></html>",
    )

    private fun list(path: String): List<String>? {
        val prefix = "$path/"
        return files.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .distinct()
            .sorted()
            .takeIf { it.isNotEmpty() }
    }

    @Test
    fun collectsEveryAssetFileWithSkillFirst() {
        val collected = collectWorkshopAssetFiles(
            root = "skills/workshop",
            list = ::list,
            read = { path -> files[path] },
        )

        assertEquals(
            listOf(
                PluginSkillFile("SKILL.md", "# Skill"),
                PluginSkillFile("examples/01-hello.html", "<html></html>"),
                PluginSkillFile("references/api.md", "# API"),
            ),
            collected,
        )
    }

    @Test
    fun ignoresMissingRoot() {
        val collected = collectWorkshopAssetFiles(
            root = "skills/missing",
            list = { null },
            read = { null },
        )

        assertTrue(collected.isEmpty())
    }
}
