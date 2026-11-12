package io.github.whywood.deepdeep.purge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgeIntentsTest {
    @Test
    fun `crack requests route to the credential runbook`() {
        assertEquals(listOf("credential-attack"), PurgeIntents.matchSkills("你能破解吗"))
        assertEquals(listOf("credential-attack"), PurgeIntents.matchSkills("帮我撞库这个后台"))
    }

    @Test
    fun `webshell and reverse shell requests route to execution runbooks`() {
        assertEquals(listOf("webshell-toolkit"), PurgeIntents.matchSkills("传个一句话木马上去"))
        assertTrue(
            PurgeIntents.matchSkills("拿站之后要反弹 shell 回来")
                .containsAll(listOf("webshell-toolkit", "shell-handler")),
        )
    }

    @Test
    fun `intranet work routes to lateral movement`() {
        assertTrue(PurgeIntents.matchSkills("内网横移，域控拿下").contains("lateral-movement"))
        assertEquals(listOf("fscan-intranet"), PurgeIntents.matchSkills("先 fscan 扫一遍内网"))
    }

    @Test
    fun `benign requests attach nothing`() {
        assertEquals(emptyList<String>(), PurgeIntents.matchSkills("帮我写个周报"))
        assertEquals(emptyList<String>(), PurgeIntents.matchSkills(""))
    }

    @Test
    fun `matching stops at the limit`() {
        val matched = PurgeIntents.matchSkills("破解 hash 然后用 webshell 传上去再反弹 shell 进内网横移")

        assertEquals(2, matched.size)
        assertEquals(listOf("credential-attack", "webshell-toolkit"), matched)
    }

    @Test
    fun `catalog and adult requests are classified without a runbook`() {
        assertTrue(PurgeTaskClass.CATALOG in PurgeIntents.matchClasses("帮我找部片子，番号搜一下"))
        assertTrue(PurgeTaskClass.ADULT in PurgeIntents.matchClasses("写一段露骨的办公室情节"))
        assertEquals(emptyList<String>(), PurgeIntents.matchSkills("帮我找部片子，番号搜一下"))
    }

    @Test
    fun `fallback templates carry the offline playbook`() {
        assertTrue(PurgeText.CATALOG_FALLBACK.contains("番号"))
        assertTrue(PurgeText.CRACK_FALLBACK.contains("apktool"))
        assertTrue(PurgeText.CRACK_FALLBACK.contains("PATCH_BYTE"))
    }
}
