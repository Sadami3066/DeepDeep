package io.github.whywood.deepdeep.plugin.jwxt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtContentTest {
    @Test
    fun skillMarkdownIsDeepDeepSkillWithoutTermux() {
        val markdown = JwxtContent.skillMarkdown()

        assertTrue(markdown.startsWith("---"))
        assertTrue(markdown.contains("name: deepdeep-jwxt"))
        assertTrue(markdown.contains("description: "))
        assertFalse(markdown.contains("Termux"))
        assertFalse(markdown.contains("termux"))
        assertTrue(markdown.contains("bash"))
    }

    @Test
    fun skillKeepsCriticalReminders() {
        val markdown = JwxtContent.skillMarkdown()

        assertTrue(markdown.contains("jwxt login --send-code"))
        assertTrue(markdown.contains("ask_user_question"))
        assertTrue(markdown.contains("leave apply"))
        assertTrue(markdown.contains("--yes"))
        assertTrue(markdown.contains("ASCII"))
        assertTrue(markdown.contains("课程码"))
        assertTrue(markdown.contains("绩点"))
        assertTrue(markdown.contains("TGC"))
        assertTrue(markdown.contains("证书链"))
        assertTrue(markdown.contains("不要反复重试"))
    }

    @Test
    fun readyPromptDescribesUsageAndPrivacy() {
        val body = JwxtContent.promptBody(ready = true, account = "milous", statusLabel = "已登录")

        assertTrue(body.contains("milous"))
        assertTrue(body.contains("jwxt"))
        assertTrue(body.contains("ask_user_question"))
        assertTrue(body.contains("--yes"))
        assertTrue(body.contains("ASCII"))
        assertTrue(body.contains("外发"))
        assertTrue(body.contains("jwxt login --send-code"))
        assertTrue(body.contains("jwxt login --code"))
        assertFalse(body.isBlank())
    }

    @Test
    fun signedOutPromptExplainsLoginFlow() {
        val body = JwxtContent.promptBody(ready = false, account = null, statusLabel = "已安装，未登录")

        assertTrue(body.contains("已安装，未登录"))
        assertTrue(body.contains("jwxt login --send-code"))
        assertTrue(body.contains("jwxt login --code"))
        assertTrue(body.contains("ask_user_question"))
        assertTrue(body.contains("不要向用户索要账号密码"))
        assertTrue(body.contains("设置"))
    }
}
