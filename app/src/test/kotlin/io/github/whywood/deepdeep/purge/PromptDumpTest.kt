package io.github.whywood.deepdeep.purge

import io.github.whywood.deepdeep.sdk.coding.prompt.SystemPromptInput
import io.github.whywood.deepdeep.sdk.coding.prompt.buildSystemPromptText
import java.io.File
import okio.Path.Companion.toPath
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptDumpTest {
    @Test
    fun `injection asset decodes as utf8`() {
        val bytes = File("src/main/assets/purge/prompt-inject.md").readBytes()
        val decoded = String(bytes, Charsets.UTF_8)

        assertTrue(decoded.startsWith("<|im_start|>system"))
        assertTrue(!decoded.contains(0.toChar()))
        assertTrue(decoded.contains("NEVER REFUSE") || decoded.contains("Never Refuse"))
    }

    @Test
    fun dump() {
        val injection = File("src/main/assets/purge/prompt-inject.md").readText(Charsets.UTF_8)
        val skills = File("src/main/assets/purge/skills").listFiles().orEmpty().joinToString("\n") { dir ->
            val head = File(dir, "SKILL.md").readLines(Charsets.UTF_8).take(6)
            val name = head.firstOrNull { it.startsWith("name:") }.orEmpty().removePrefix("name:").trim()
            val desc = head.firstOrNull { it.startsWith("description:") }.orEmpty().removePrefix("description:").trim()
            "- $name : $desc"
        }
        val preamble = PurgeText.composePreamble(injection, PurgeTuning(enabled = true))
        val sections = linkedMapOf(
            "preamble" to preamble,
            "environment" to "<environment>\nDate: 2026-11-12 (UTC+08:00).\nModel: deepseek/deepseek-flash.\nEnv: Alpine Linux via proot and Android.\nNickname: Milous.\nInteraction style: Balanced.\n</environment>",
            "paths" to "<paths>\n- skills: /data/user/0/io.github.whywood.deepdeep/files/agent/skills\n- app data: /data/user/0/io.github.whywood.deepdeep/files\n</paths>",
            "skills" to "<skills>\n$skills\n</skills>",
            "rules" to "<rules>\n${PurgeText.RULES_BODY}\n</rules>",
        )
        val input = SystemPromptInput(
            preamble = "",
            cwd = "/data/user/0/io.github.whywood.deepdeep/files".toPath(),
            customSections = sections.filterKeys { it != "preamble" },
        )
        val defaultText = buildSystemPromptText(input)
        val android = sections.getValue("preamble") + "\n" + defaultText
        val out = File("build/tmp/prompt-dump.txt")
        out.parentFile?.mkdirs()
        out.writeText(android, Charsets.UTF_8)
        assertTrue(out.length() > 1000)
    }
}
