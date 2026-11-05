package io.github.whywood.deepdeep.plugin.jwxt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwxtCliTest {
    @Test
    fun installCommandLinksBinaryAndChecksVersion() {
        val command = JwxtCli.installCommand()

        assertTrue(command.contains("mkdir -p /usr/local/bin"))
        assertTrue(command.contains("chmod 755 ${JwxtCli.INSTALL_DIR}/${JwxtCli.ENTRY_RELATIVE}"))
        assertTrue(command.contains("ln -sf ${JwxtCli.INSTALL_DIR}/${JwxtCli.ENTRY_RELATIVE} ${JwxtCli.BIN_PATH}"))
        assertTrue(command.contains("${JwxtCli.BIN_PATH} --version"))
    }

    @Test
    fun installedEntryRelativeMatchesInstallLayout() {
        assertEquals("${JwxtCli.INSTALL_DIR_RELATIVE}/${JwxtCli.ENTRY_RELATIVE}", JwxtCli.INSTALLED_ENTRY_RELATIVE)
    }

    @Test
    fun commandsUseInstalledBinPath() {
        assertEquals("node --version", JwxtCli.nodeVersionCommand())
        assertEquals("${JwxtCli.BIN_PATH} --version", JwxtCli.versionCommand())
    }

    @Test
    fun loginCommandsMatchSkillFlow() {
        assertEquals("jwxt login --send-code", JwxtCli.loginSendCodeCommand())
        assertEquals("jwxt login --code 1234", JwxtCli.loginCodeCommand("1234"))
    }

    @Test
    fun credentialsContentUsesNetidAndPasswordLines() {
        assertEquals(
            "netid=milous\npassword=s3cret\n",
            JwxtCli.credentialsContent(" milous ", " s3cret "),
        )
    }

    @Test
    fun parsesNetIdFromCredentials() {
        assertEquals("milous", JwxtCli.netId("netid=milous\npassword=x\n"))
        assertEquals("milous", JwxtCli.netId("# comment\nnetid=" + "milous" + "\n"))
        assertNull(JwxtCli.netId("password=x\n"))
        assertNull(JwxtCli.netId(null))
    }

    @Test
    fun parsesNodeVersionAndMajor() {
        assertEquals("v22.20.0", JwxtCli.parseNodeVersion("v22.20.0\n"))
        assertEquals(22, JwxtCli.parseNodeMajor("v22.20.0"))
        assertEquals(18, JwxtCli.parseNodeMajor("v18.19.1"))
        assertNull(JwxtCli.parseNodeVersion("sh: node: not found"))
        assertNull(JwxtCli.parseNodeMajor("not a version"))
    }

    @Test
    fun parsesCliVersion() {
        assertEquals("1.0.0", JwxtCli.parseVersion("1.0.0\n"))
        assertEquals("1.0.0", JwxtCli.parseVersion("jwxt version 1.0.0"))
        assertNull(JwxtCli.parseVersion("command not found"))
    }

    @Test
    fun detectsLoginOnlyWhenCookiesExist() {
        assertTrue(JwxtCli.isLoggedIn("""{"capturedAt":"2026-11-01T00:00:00.000Z","cookies":[{"name":"LYSESSIONID","value":"x"}]}"""))
        assertFalse(JwxtCli.isLoggedIn("""{"cookies":[]}"""))
        assertFalse(JwxtCli.isLoggedIn("""{"cookies":"x"}"""))
        assertFalse(JwxtCli.isLoggedIn("not json"))
        assertFalse(JwxtCli.isLoggedIn(null))
        assertFalse(JwxtCli.isLoggedIn("  "))
    }

    @Test
    fun versionConstantsAreSane() {
        assertTrue(JwxtCli.VERSION.matches(Regex("""\d+\.\d+\.\d+""")))
        assertTrue(JwxtCli.MIN_NODE_MAJOR >= 18)
        assertEquals("deepdeep-jwxt", JwxtCli.SKILL_ID)
    }
}
