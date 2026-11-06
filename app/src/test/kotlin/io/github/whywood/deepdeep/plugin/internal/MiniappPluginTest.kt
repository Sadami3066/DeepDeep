package io.github.whywood.deepdeep.plugin.internal

import io.github.whywood.deepdeep.plugin.api.PluginSkillFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniappPluginTest {
    private val files = mapOf(
        "skills/miniapp/SKILL.md" to "# Skill",
        "skills/miniapp/references/api.md" to "# API",
        "skills/miniapp/examples/01-hello.html" to "<html></html>",
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
        val collected = collectMiniappAssetFiles(
            root = "skills/miniapp",
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
        val collected = collectMiniappAssetFiles(
            root = "skills/missing",
            list = { null },
            read = { null },
        )

        assertTrue(collected.isEmpty())
    }
}
