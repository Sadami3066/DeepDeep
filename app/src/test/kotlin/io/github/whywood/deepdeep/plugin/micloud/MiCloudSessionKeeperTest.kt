package io.github.whywood.deepdeep.plugin.micloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiCloudSessionKeeperTest {
    @Test
    fun validSnapshotNeedsNoRecovery() = runTest {
        val verified = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val probe = FakeProbe()
        val keeper = keeper(
            snapshot = SNAPSHOT,
            jar = JAR,
            verified = verified,
            saved = saved,
            valid = setOf(SNAPSHOT),
            probe = probe,
        )

        assertTrue(keeper.recover())
        assertEquals(listOf(SNAPSHOT), verified)
        assertTrue(saved.isEmpty())
        assertEquals(0, probe.calls)
    }

    @Test
    fun expiredSnapshotRestoresFromWebViewJar() = runTest {
        val verified = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val probe = FakeProbe()
        val keeper = keeper(
            snapshot = SNAPSHOT,
            jar = JAR,
            verified = verified,
            saved = saved,
            valid = setOf(JAR),
            probe = probe,
        )

        assertTrue(keeper.recover())
        assertEquals(listOf(SNAPSHOT, JAR), verified)
        assertEquals(listOf(JAR), saved)
        assertEquals(0, probe.calls)
    }

    @Test
    fun missingSnapshotAndJarLeaveSessionExpired() = runTest {
        val verified = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val probe = FakeProbe()
        val keeper = keeper(
            snapshot = null,
            jar = null,
            verified = verified,
            saved = saved,
            valid = emptySet(),
            probe = probe,
        )

        assertFalse(keeper.recover())
        assertTrue(verified.isEmpty())
        assertTrue(saved.isEmpty())
        assertEquals(1, probe.calls)
    }

    @Test
    fun jarWithoutServiceTokenFallsBackToWebView() = runTest {
        val verified = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val probe = FakeProbe(WEB)
        val keeper = keeper(
            snapshot = null,
            jar = "userId=1",
            verified = verified,
            saved = saved,
            valid = setOf(WEB),
            probe = probe,
        )

        assertTrue(keeper.recover())
        assertEquals(listOf(WEB), verified)
        assertEquals(listOf(WEB), saved)
        assertEquals(1, probe.calls)
    }

    @Test
    fun webViewRecoveryIsThrottledToOncePerFiveMinutes() = runTest {
        var now = 0L
        val probe = FakeProbe()
        val keeper = keeper(
            snapshot = null,
            jar = null,
            verified = mutableListOf(),
            saved = mutableListOf(),
            valid = emptySet(),
            probe = probe,
            clock = { now },
        )

        assertFalse(keeper.recoverFromWebView())
        assertEquals(1, probe.calls)
        now = 5L * 60L * 1000L - 1L
        assertFalse(keeper.recoverFromWebView())
        assertEquals(1, probe.calls)
        now = 5L * 60L * 1000L
        assertFalse(keeper.recoverFromWebView())
        assertEquals(2, probe.calls)
    }

    @Test
    fun webViewProbeBaselinesOnJarToken() = runTest {
        val probe = FakeProbe(WEB)
        val keeper = keeper(
            snapshot = null,
            jar = STALE_JAR,
            verified = mutableListOf(),
            saved = mutableListOf(),
            valid = setOf(WEB),
            probe = probe,
        )

        assertTrue(keeper.recoverFromWebView())
        assertEquals(STALE_JAR, probe.previousCookie)
    }

    @Test
    fun matchingSnapshotAndJarSkipsVerification() = runTest {
        val verified = mutableListOf<String>()
        val keeper = keeper(
            snapshot = SNAPSHOT,
            jar = SNAPSHOT,
            verified = verified,
            saved = mutableListOf(),
            valid = emptySet(),
            probe = FakeProbe(),
        )

        assertFalse(keeper.recoverFromJar())
        assertTrue(verified.isEmpty())
    }

    @Test
    fun blockedRecoveryIgnoresJarToken() = runTest {
        val verified = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val probe = FakeProbe(WEB)
        val keeper = keeper(
            snapshot = null,
            jar = JAR,
            verified = verified,
            saved = saved,
            valid = setOf(JAR, WEB),
            probe = probe,
            blocked = true,
        )

        assertFalse(keeper.recoverFromJar())
        assertFalse(keeper.recoverFromWebView())
        assertFalse(keeper.recover())
        assertTrue(verified.isEmpty())
        assertTrue(saved.isEmpty())
        assertEquals(0, probe.calls)
    }

    private fun keeper(
        snapshot: String?,
        jar: String?,
        verified: MutableList<String>,
        saved: MutableList<String>,
        valid: Set<String>,
        probe: MiCloudWebCookieProbe,
        clock: () -> Long = { 0L },
        blocked: Boolean = false,
    ): MiCloudSessionKeeper = MiCloudSessionKeeper(
        readSnapshot = { snapshot },
        readJarCookie = { jar },
        verifyCookie = { cookie ->
            verified += cookie
            cookie in valid
        },
        saveCookie = { saved += it },
        webCookieProbe = probe,
        readRecoveryBlocked = { blocked },
        clock = clock,
    )

    private class FakeProbe(private val cookie: String? = null) : MiCloudWebCookieProbe {
        var calls = 0
            private set
        var previousCookie: String? = null
            private set

        override suspend fun awaitServiceTokenCookie(timeoutMs: Long, previousCookie: String?): String? {
            calls++
            this.previousCookie = previousCookie
            return cookie
        }
    }

    private companion object {
        const val SNAPSHOT = "serviceToken=snapshot; userId=1"
        const val JAR = "serviceToken=jar; userId=1"
        const val STALE_JAR = "serviceToken=stale; userId=1"
        const val WEB = "serviceToken=web; userId=1"
    }
}
