package io.github.whywood.deepdeep.plugin.wecom

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WecomCliTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun commandsUseStableBinaryAndPaths() {
        assertTrue(WecomCli.versionCommand().contains("wecom-cli --version"))
        assertTrue(WecomCli.authStatusCommand().contains("auth show --status"))
        assertTrue(WecomCli.authShowCommand().contains("auth show"))
        assertTrue(WecomCli.authInitCommand().contains("auth init --noninteractive --no-browser"))
        assertTrue(WecomCli.authInitCommand().contains("--output-qrcode ${WecomCli.QR_GUEST_PATH}"))
        assertTrue(WecomCli.authInitCommand().contains("HOME=/root"))
        assertTrue(WecomCli.logoutCommand().contains(WecomCli.CREDENTIALS_PATH))
        assertTrue(WecomCli.uninstallCommand().contains(WecomCli.INSTALL_PATH))
        assertTrue(WecomCli.uninstallCommand().contains(WecomCli.CONFIG_DIR))
        assertTrue(WecomCli.extractCommand().contains(WecomCli.PACKAGE_BINARY))
        assertTrue(WecomCli.installCommand().contains(WecomCli.INSTALL_PATH))
        assertTrue(WecomCli.restoreCommand().contains(WecomCli.INSTALL_BACKUP_PATH))
    }

    @Test
    fun downloadSourcesMatchVersion() {
        assertTrue(WecomCli.DOWNLOAD_URLS.first().startsWith("https://registry.npmjs.org/"))
        assertTrue(WecomCli.DOWNLOAD_URLS.last().startsWith("https://registry.npmmirror.com/"))
        WecomCli.DOWNLOAD_URLS.forEach { url ->
            assertTrue(url, url.endsWith("cli-linux-arm64-${WecomCli.VERSION}.tgz"))
        }
        assertEquals(64, WecomCli.ARCHIVE_SHA256.length)
    }

    @Test
    fun parsesVersion() {
        val output = "wecom-cli 1.3.4 (npm 2026-03-24T12:00:00Z abcdef123)\n"
        assertEquals("1.3.4", WecomCli.parseVersion(output))
        assertEquals("1.3.4", WecomCli.parseVersion("\u001B[32mwecom-cli 1.3.4 (npm build)\u001B[0m\n"))
        assertNull(WecomCli.parseVersion("wecom-cli: command not found"))
    }

    @Test
    fun parsesAuthStatus() {
        assertEquals(true, WecomCli.parseAuthStatus("authorized\n"))
        assertEquals(false, WecomCli.parseAuthStatus("unauthorized\n"))
        assertEquals(true, WecomCli.parseAuthStatus("Status: authorized\nBot ID: bot-1\n"))
        assertEquals(false, WecomCli.parseAuthStatus("[warn] stale cache\nStatus: unauthorized\n"))
        assertNull(WecomCli.parseAuthStatus("some other output"))
    }

    @Test
    fun parsesBotId() {
        assertEquals("bot-123", WecomCli.parseBotId("Status: authorized\nBot ID: bot-123\n"))
        assertEquals("bot_456", WecomCli.parseBotId("\u001B[32mBot ID:\u001B[0m bot_456\n"))
        assertNull(WecomCli.parseBotId("Status: unauthorized\n"))
    }

    @Test
    fun parsesQrPageUrl() {
        val output = """
            正在获取二维码...
            请打开二维码链接扫码: 
            https://work.weixin.qq.com/ai/qc/gen?code=abc123&state=xyz
            也可以使用企业微信扫描以下二维码：
            等待扫码中...
        """.trimIndent()
        assertEquals(
            "https://work.weixin.qq.com/ai/qc/gen?code=abc123&state=xyz",
            WecomCli.parseQrPageUrl(output),
        )
        assertNull(WecomCli.parseQrPageUrl("https://example.com/other"))
        assertNull(WecomCli.parseQrPageUrl("等待扫码中..."))
    }

    @Test
    fun detectsCompletedQrPng() {
        val complete = folder.newFile("complete.png").apply {
            writeBytes(PNG_HEADER + byteArrayOf(0x01, 0x02) + PNG_TRAILER)
        }
        assertTrue(WecomCli.isCompleteQrCode(complete))
        val headerOnly = folder.newFile("header.png").apply {
            writeBytes(PNG_HEADER + byteArrayOf(0x01, 0x02))
        }
        assertFalse(WecomCli.isCompleteQrCode(headerOnly))
        val text = folder.newFile("text.png").apply { writeText("not a png at all, definitely long enough") }
        assertFalse(WecomCli.isCompleteQrCode(text))
        assertFalse(WecomCli.isCompleteQrCode(File(folder.root, "missing.png")))
    }

    private companion object {
        val PNG_HEADER = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
        val PNG_TRAILER = byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x00,
            0x49,
            0x45,
            0x4E,
            0x44,
            0xAE.toByte(),
            0x42,
            0x60,
            0x82.toByte(),
        )
    }
}
