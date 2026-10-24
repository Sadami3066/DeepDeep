package io.github.whywood.deepdeep.plugin.feishu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeishuCliTest {
    @Test
    fun commandsUseStableBinaryAndDomains() {
        assertTrue(FeishuCli.versionCommand().contains("lark-cli --version"))
        assertTrue(FeishuCli.statusCommand().contains("auth status --json"))
        assertTrue(FeishuCli.statusCommand(verify = true).contains("--verify"))
        assertTrue(FeishuCli.loginStartCommand().contains("auth login --domain"))
        assertTrue(FeishuCli.loginStartCommand().contains("--domain all"))
        assertTrue(FeishuCli.configInitCommand().contains("config init --new --brand feishu"))
        assertTrue(FeishuCli.logoutCommand().contains("auth logout"))
        assertTrue(FeishuCli.uninstallCommand().contains(FeishuCli.INSTALL_PATH))
        assertTrue(FeishuCli.uninstallCommand().contains(FeishuCli.CONFIG_DIR))
    }

    @Test
    fun deviceCodeIsShellQuoted() {
        val command = FeishuCli.loginPollCommand("a'b")
        assertTrue(command.contains("'a'\\''b'"))
    }

    @Test
    fun parsesVersion() {
        assertEquals("1.0.98", FeishuCli.parseVersion("lark-cli version 1.0.98\n"))
        assertNull(FeishuCli.parseVersion("command not found"))
    }

    @Test
    fun extractsConfigInitUrl() {
        val output = """
            > 请在浏览器中打开以下链接完成应用创建：
              https://open.feishu.cn/page/cli?user_code=ABCDEF&from=cli
              （等待中）
        """.trimIndent()
        assertEquals(
            "https://open.feishu.cn/page/cli?user_code=ABCDEF&from=cli",
            FeishuCli.parseConfigInitUrl(output),
        )
        assertNull(FeishuCli.parseConfigInitUrl("no link here"))
    }

    @Test
    fun parsesLoginChallengeFromNoisyOutput() {
        val output = """
            [lark-cli] [WARN] some warning
            {"verification_url":"https://open.feishu.cn/page/cli?user_code=XYZ","device_code":"dc_123","expires_in":600}
        """.trimIndent()
        val challenge = FeishuCli.parseLoginChallenge(output)
        assertNotNull(challenge)
        assertEquals("https://open.feishu.cn/page/cli?user_code=XYZ", challenge?.verificationUrl)
        assertEquals("dc_123", challenge?.deviceCode)
    }

    @Test
    fun parsesConnectedStatus() {
        val output = """
            {"appId":"cli_abc","brand":"feishu","defaultAs":"auto","dpopMode":"preferred",
             "identities":{"bot":{"status":"ready","available":true},
             "user":{"status":"ready","available":true,"userName":"张三","openId":"ou_x",
             "tokenStatus":"ready","scope":"im:message:readonly calendar:calendar:readonly"}},
             "identity":"user","verified":true}
        """.trimIndent()
        val status = FeishuCli.parseAuthStatus(output)
        assertNotNull(status)
        assertTrue(status!!.appConfigured)
        assertTrue(status.userReady)
        assertEquals("cli_abc", status.appId)
        assertEquals("张三", status.account)
        assertEquals(2, status.scopeCount)
    }

    @Test
    fun parsesNotConfiguredStatus() {
        val output = """
            {"appId":"","identity":"none",
             "identities":{"user":{"status":"not_configured","available":false,"message":"not signed in"}}}
        """.trimIndent()
        val status = FeishuCli.parseAuthStatus(output)
        assertNotNull(status)
        assertFalse(status!!.appConfigured)
        assertFalse(status.userReady)
        assertNull(status.appId)
        assertNull(status.account)
    }

    @Test
    fun malformedStatusReturnsNull() {
        assertNull(FeishuCli.parseAuthStatus("lark-cli: command not found"))
    }

    @Test
    fun parsesPartialLoginResultWithMissingScopes() {
        val output = """
            [lark-cli] [WARN] failed to load cached requested scopes
            {"event":"authorization_complete","user_open_id":"ou_x","user_name":"张三",
             "scope":"im:message:readonly docs:document:export",
             "requested":["im:message:readonly","docs:document:export","sheets:spreadsheet:readonly","base:app:readonly"],
             "newly_granted":["im:message:readonly","docs:document:export"],"already_granted":[],
             "missing":["sheets:spreadsheet:readonly","base:app:readonly"],
             "granted":["im:message:readonly","docs:document:export"],
             "warning":{"type":"missing_scope","hint":"缺少权限：sheets:spreadsheet:readonly base:app:readonly"}}
        """.trimIndent()
        val result = FeishuCli.parseLoginResult(output)
        assertNotNull(result)
        assertEquals("张三", result!!.account)
        assertEquals(listOf("sheets:spreadsheet:readonly", "base:app:readonly"), result.missingScopes)
        assertEquals(2, result.grantedCount)
        assertTrue(result.warning!!.contains("缺少权限"))
    }

    @Test
    fun parsesCleanLoginResult() {
        val output = """
            {"event":"authorization_complete","user_open_id":"ou_x","user_name":"张三",
             "scope":"im:message:readonly","requested":["im:message:readonly"],
             "newly_granted":["im:message:readonly"],"already_granted":[],
             "missing":[],"granted":["im:message:readonly"]}
        """.trimIndent()
        val result = FeishuCli.parseLoginResult(output)
        assertNotNull(result)
        assertEquals("张三", result!!.account)
        assertTrue(result.missingScopes.isEmpty())
        assertEquals(1, result.grantedCount)
        assertNull(result.warning)
    }

    @Test
    fun parsesFinalLoginResultFromNoisyOutput() {
        val output = """
            {"event":"device_authorization","verification_url":"https://open.feishu.cn/page/cli?user_code=XYZ","device_code":"dc_123"}
            {"event":"authorization_complete","user_name":"张三","missing":["base:app:readonly"],"granted":["im:message:readonly"]}
        """.trimIndent()
        val result = FeishuCli.parseLoginResult(output)
        assertNotNull(result)
        assertEquals("张三", result!!.account)
        assertEquals(listOf("base:app:readonly"), result.missingScopes)
    }

    @Test
    fun nonCompletedLoginOutputIsNotAResult() {
        assertNull(FeishuCli.parseLoginResult("""{"event":"authorization_failed","error":"access_denied"}"""))
        assertNull(FeishuCli.parseLoginResult("lark-cli: command not found"))
    }
}
