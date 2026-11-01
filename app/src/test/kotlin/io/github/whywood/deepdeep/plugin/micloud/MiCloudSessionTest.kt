package io.github.whywood.deepdeep.plugin.micloud

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiCloudSessionTest {
    private var clock = 1_000L
    private var cookie: String? = "userId=1; serviceToken=token"
    private var probes = 0
    private var failure: Exception? = null

    @Test
    fun missingCookieIsSignedOut() = runTest {
        val session = session()
        cookie = "   "
        assertEquals(MiCloudSessionState.SIGNED_OUT, session.status().state)
        cookie = null
        val view = session.status()
        assertEquals(MiCloudSessionState.SIGNED_OUT, view.state)
        assertNull(view.nickname)
        assertNull(view.checkedAt)
        assertEquals(0, probes)
    }

    @Test
    fun successfulProbeIsVerified() = runTest {
        val view = session().status()
        assertEquals(MiCloudSessionState.VERIFIED, view.state)
        assertEquals("小明", view.nickname)
        assertEquals(clock, view.checkedAt)
        assertEquals(1, probes)
    }

    @Test
    fun authFailureIsExpired() = runTest {
        failure = MiCloudAuthException("expired")
        val view = session().status()
        assertEquals(MiCloudSessionState.EXPIRED, view.state)
        assertEquals(clock, view.checkedAt)
    }

    @Test
    fun networkFailureIsUnverifiedAndKeepsLastNickname() = runTest {
        val session = session()
        session.status()
        clock += 70_000
        failure = IOException("offline")
        val view = session.status()
        assertEquals(MiCloudSessionState.UNVERIFIED, view.state)
        assertEquals("小明", view.nickname)
        assertEquals(1_000L, view.checkedAt)
        assertEquals(2, probes)
    }

    @Test
    fun networkFailureWithoutHistoryIsUnverified() = runTest {
        failure = IOException("offline")
        val view = session().status()
        assertEquals(MiCloudSessionState.UNVERIFIED, view.state)
        assertNull(view.nickname)
        assertNull(view.checkedAt)
    }

    @Test
    fun verifiedCacheSkipsProbeWithinTtl() = runTest {
        val session = session()
        session.status()
        clock += 30_000
        assertEquals(MiCloudSessionState.VERIFIED, session.status().state)
        assertEquals(1, probes)
        clock += 40_000
        session.status()
        assertEquals(2, probes)
    }

    @Test
    fun expiredCacheSkipsProbeWithinWindow() = runTest {
        val session = session()
        failure = MiCloudAuthException("expired")
        session.status()
        clock += 120_000
        failure = null
        assertEquals(MiCloudSessionState.EXPIRED, session.status().state)
        assertEquals(1, probes)
        clock += 600_000
        assertEquals(MiCloudSessionState.VERIFIED, session.status().state)
        assertEquals(2, probes)
    }

    @Test
    fun cookieChangeInvalidatesCache() = runTest {
        val session = session()
        session.status()
        cookie = "userId=2; serviceToken=other"
        assertEquals(MiCloudSessionState.VERIFIED, session.status().state)
        assertEquals(2, probes)
    }

    @Test
    fun markExpiredTakesEffectImmediately() = runTest {
        val session = session()
        session.status()
        session.markExpired()
        val view = session.status()
        assertEquals(MiCloudSessionState.EXPIRED, view.state)
        assertEquals(1, probes)
    }

    @Test
    fun markVerifiedTakesEffectImmediately() = runTest {
        val session = session()
        session.status()
        clock += 120_000
        session.markVerified(MiCloudProfile("小红", "2", null))
        val view = session.status()
        assertEquals(MiCloudSessionState.VERIFIED, view.state)
        assertEquals("小红", view.nickname)
        assertEquals(clock, view.checkedAt)
        assertEquals(1, probes)
    }

    @Test
    fun markSignedOutTakesEffectImmediately() = runTest {
        val session = session()
        session.status()
        cookie = null
        session.markSignedOut()
        assertEquals(MiCloudSessionState.SIGNED_OUT, session.status().state)
        assertEquals(1, probes)
    }

    @Test
    fun concurrentStatusProbesOnce() = runTest {
        val session = MiCloudSession(
            cookie = { "userId=1; serviceToken=token" },
            probe = {
                probes += 1
                delay(100)
                MiCloudProfile("小明", "1", null)
            },
            now = { clock },
        )
        val first = async { session.status() }
        val second = async { session.status() }
        assertEquals(MiCloudSessionState.VERIFIED, first.await().state)
        assertEquals(MiCloudSessionState.VERIFIED, second.await().state)
        assertEquals(1, probes)
    }

    private fun session(
        ttlMillis: Long = 60_000L,
        expiredTtlMillis: Long = 600_000L,
    ): MiCloudSession = MiCloudSession(
        cookie = { cookie },
        probe = {
            probes += 1
            failure?.let { throw it }
            MiCloudProfile("小明", "1", null)
        },
        now = { clock },
        ttlMillis = ttlMillis,
        expiredTtlMillis = expiredTtlMillis,
    )
}
