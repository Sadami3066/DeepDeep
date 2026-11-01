package io.github.whywood.deepdeep.plugin.micloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MiCloudSessionState {
    SIGNED_OUT,
    VERIFIED,
    EXPIRED,
    UNVERIFIED,
}

data class MiCloudSessionView(
    val state: MiCloudSessionState,
    val nickname: String?,
    val checkedAt: Long?,
)

class MiCloudSession(
    private val cookie: suspend () -> String?,
    private val probe: suspend () -> MiCloudProfile,
    private val now: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = 60_000L,
    private val expiredTtlMillis: Long = 600_000L,
) {
    private val mutex = Mutex()
    @Volatile private var cache: MiCloudSessionView? = null
    @Volatile private var lastVerified: MiCloudSessionView? = null
    @Volatile private var fingerprint: String? = null
    @Volatile private var fingerprintKnown = false

    suspend fun status(force: Boolean = false): MiCloudSessionView = mutex.withLock {
        val current = cookie()?.takeIf { it.isNotBlank() }
        if (current == null) return@withLock signedOut()
        if (fingerprintKnown && fingerprint != current) {
            cache = null
            lastVerified = null
        }
        fingerprint = current
        fingerprintKnown = true
        val cached = cache
        if (cached != null && !force && isFresh(cached)) return@withLock cached
        val view = try {
            val profile = probe()
            MiCloudSessionView(MiCloudSessionState.VERIFIED, profile.nickname, now())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: MiCloudAuthException) {
            MiCloudSessionView(
                MiCloudSessionState.EXPIRED,
                lastVerified?.nickname ?: cached?.nickname,
                now(),
            )
        } catch (_: Exception) {
            MiCloudSessionView(
                MiCloudSessionState.UNVERIFIED,
                lastVerified?.nickname,
                lastVerified?.checkedAt,
            )
        }
        cache = view
        if (view.state == MiCloudSessionState.VERIFIED) lastVerified = view
        view
    }

    fun markVerified(profile: MiCloudProfile) {
        val view = MiCloudSessionView(MiCloudSessionState.VERIFIED, profile.nickname, now())
        cache = view
        lastVerified = view
    }

    fun markExpired() {
        cache = MiCloudSessionView(
            MiCloudSessionState.EXPIRED,
            lastVerified?.nickname ?: cache?.nickname,
            now(),
        )
    }

    fun markSignedOut() {
        signedOut()
    }

    private fun signedOut(): MiCloudSessionView {
        val view = MiCloudSessionView(MiCloudSessionState.SIGNED_OUT, null, null)
        cache = view
        lastVerified = null
        fingerprint = null
        fingerprintKnown = false
        return view
    }

    private fun isFresh(view: MiCloudSessionView): Boolean {
        val checkedAt = view.checkedAt ?: return false
        val age = now() - checkedAt
        return when (view.state) {
            MiCloudSessionState.VERIFIED -> age < ttlMillis
            MiCloudSessionState.EXPIRED -> age < expiredTtlMillis
            else -> false
        }
    }
}
