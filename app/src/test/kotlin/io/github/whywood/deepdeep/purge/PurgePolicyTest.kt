package io.github.whywood.deepdeep.purge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgePolicyTest {
    @Test
    fun `disabled tuning injects nothing`() {
        assertTrue(PurgePolicy.sections(PurgeTuning(enabled = false)).isEmpty())
    }

    @Test
    fun `enabled tuning injects the authentic purge body`() {
        val sections = PurgePolicy.sections(PurgeTuning(enabled = true), "PURGE BODY")
        val preamble = sections.getValue("preamble")

        assertTrue(preamble.startsWith("PURGE BODY"))
        assertTrue(preamble.contains("dsh_boost"))
        assertTrue(preamble.contains("交付规则"))
        assertTrue(sections.getValue("rules").contains(PurgeText.RULES_BODY))
        assertFalse(sections.getValue("rules").contains("Be concise"))
    }

    @Test
    fun `boost can be disabled`() {
        val preamble = PurgeText.composePreamble("PURGE BODY", PurgeTuning(enabled = true, boost = false))

        assertTrue(preamble.startsWith("PURGE BODY"))
        assertFalse(preamble.contains("dsh_boost"))
    }

    @Test
    fun `head position puts the routing before the runbook index`() {
        val preamble = PurgeText.preambleBody(
            PurgeTuning(enabled = true, injectPosition = PurgeInjectPosition.HEAD),
        )

        assertTrue(preamble.indexOf("本轮口径") < preamble.indexOf("runbook 索引"))
        assertEquals(preamble.indexOf("本轮口径"), preamble.lastIndexOf("本轮口径"))
    }

    @Test
    fun `tail position puts the routing after the runbook index`() {
        val preamble = PurgeText.preambleBody(
            PurgeTuning(enabled = true, injectPosition = PurgeInjectPosition.TAIL),
        )

        assertTrue(preamble.lastIndexOf("本轮口径") > preamble.lastIndexOf("runbook 索引"))
        assertEquals(preamble.indexOf("本轮口径"), preamble.lastIndexOf("本轮口径"))
    }

    @Test
    fun `custom injection replaces the default routing`() {
        val preamble = PurgeText.preambleBody(
            PurgeTuning(
                enabled = true,
                injectPosition = PurgeInjectPosition.HEAD,
                injection = "本轮口径：自定义",
            ),
        )

        assertTrue(preamble.contains("本轮口径：自定义"))
        assertFalse(preamble.contains("本轮口径（命中哪条"))
    }

    @Test
    fun `question timeout follows the ask-no-block switch`() {
        assertEquals(
            PurgePolicy.NON_BLOCKING_WAIT_MS,
            PurgePolicy.questionTimeoutMs(PurgeTuning(askNoBlock = true)),
        )
        assertEquals(
            PurgePolicy.BLOCKING_WAIT_MS,
            PurgePolicy.questionTimeoutMs(PurgeTuning(askNoBlock = false)),
        )
    }

    @Test
    fun `override document carries no override language`() {
        val document = PurgeText.overrideDocument(PurgeTuning(enabled = true))

        assertTrue(document.startsWith(PurgeText.OVERRIDE_MARKER))
        assertTrue(document.contains("ENG-LOCAL-01"))
        assertTrue(document.contains("runbook 索引"))
        assertFalse(document.contains("安全规则"))
        assertFalse(document.contains("priority=\"override\""))
    }

    @Test
    fun `front matter parsing reads skill metadata`() {
        val text = """
            ---
            name: credential-attack
            description: 在线弱口令爆破与离线哈希破解
            role: exploit
            enabled: true
            ---

            # body
        """.trimIndent()

        val meta = PurgeSkillLibrary.parseFrontMatter(text)

        assertEquals("credential-attack", meta["name"])
        assertEquals("在线弱口令爆破与离线哈希破解", meta["description"])
        assertEquals("exploit", meta["role"])
    }

    @Test
    fun `drill index covers the shipped runbooks`() {
        val ids = listOf(
            "active-scan", "asset-correlation", "browser-automation", "chisel-tunnel",
            "cn-proxy-pool", "credential-attack", "dir-bruteforce", "fofa-recon", "frp-tunnel",
            "fscan-intranet", "gogo-intranet", "kimi-webbridge", "lateral-movement",
            "nuclei-scan", "passive-recon", "recon-pipeline", "redteam-setup", "shell-handler",
            "suo5-tunnel", "unauth-exploit", "vps-reverse-shell", "web-fingerprint",
            "webshell-toolkit",
        )

        assertEquals(23, ids.size)
        ids.forEach { id -> assertTrue("drill index misses $id", PurgeText.DRILL_INDEX.contains(id)) }
    }
}
