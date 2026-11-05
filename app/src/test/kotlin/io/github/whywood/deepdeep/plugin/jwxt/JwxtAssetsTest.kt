package io.github.whywood.deepdeep.plugin.jwxt

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtAssetsTest {
    private val root = File("src/main/assets/jwxt")

    @Test
    fun assetsMatchInstallList() {
        val actual = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertEquals(JwxtCli.ASSET_FILES.sorted(), actual)
    }

    @Test
    fun assetsContainNoCredentialsOrDocs() {
        assertFalse(File(root, "config").exists())
        assertFalse(File(root, "config/credentials").exists())
        listOf("README.md", "PROGRESS.md", "SKILL.md").forEach { name ->
            assertFalse("$name must not ship in assets", File(root, name).exists())
        }
    }

    @Test
    fun entryUsesEnvNodeShebangAndSupportsVersionAndSendCode() {
        val entry = File(root, "bin/jwxt").readText()

        assertTrue(entry.startsWith("#!/usr/bin/env node\n"))
        assertTrue(entry.contains("--send-code"))
        assertTrue(entry.contains("--version"))
        assertTrue(entry.contains("require('../src/cmds')"))
    }

    @Test
    fun loginReadsCodeOnlyFromFlag() {
        val login = File(root, "src/login.js").readText()

        assertTrue(login.contains("login({ code: given, sendCode: onlySend }"))
        assertTrue(login.contains("submitCode"))
        assertFalse(login.contains("termux"))
        assertFalse(login.contains("execFileSync"))
    }

    @Test
    fun packageVersionMatchesPluginConstant() {
        val json = JSONObject(File(root, "package.json").readText())

        assertEquals(JwxtCli.VERSION, json.getString("version"))
        assertEquals(0, json.getJSONObject("dependencies").length())
    }
}
