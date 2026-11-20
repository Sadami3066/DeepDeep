package io.github.whywood.superpi.data.relay

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RelayError {
    InvalidLink,
    MissingCode,
    MissingFingerprint,
    FingerprintMismatch,
    Unreachable,
    Unauthorized,
    BadResponse,
}

data class RelayUiState(
    val account: RelayAccount? = null,
    val signingIn: Boolean = false,
    val error: RelayError? = null,
)

class RelayController(
    private val store: RelayAccountStore,
    private val enroller: RelayEnroller,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(RelayUiState())
    val state: StateFlow<RelayUiState> = _state.asStateFlow()

    init {
        scope.launch {
            store.account.collect { account ->
                _state.update { state -> state.copy(account = account) }
            }
        }
    }

    fun signIn(link: String, loginSecret: String?, deviceName: String) {
        if (_state.value.signingIn) return
        val parsed = RelayPairingLink.parse(link) ?: run {
            fail(RelayError.InvalidLink)
            return
        }
        val secret = loginSecret?.trim()?.takeIf { it.isNotEmpty() }
        if (parsed.pairCode == null && secret == null) {
            fail(RelayError.MissingCode)
            return
        }
        val secure = parsed.origin.startsWith("https://")
        if (secure && parsed.fingerprint == null) {
            fail(RelayError.MissingFingerprint)
            return
        }
        _state.update { state -> state.copy(signingIn = true, error = null) }
        scope.launch {
            try {
                val caPem = if (secure) fetchCa(parsed) else null
                val enrollment = enroller.enroll(
                    origin = parsed.origin,
                    caPem = caPem,
                    deviceName = deviceName.trim().ifEmpty { DEFAULT_DEVICE_NAME },
                    pairCode = parsed.pairCode,
                    loginSecret = secret,
                )
                val fingerprint = parsed.fingerprint ?: enrollment.fingerprint
                if (secure && enrollment.fingerprint != null && fingerprint != parsed.fingerprint) {
                    throw RelayException(RelayFailure.FingerprintMismatch)
                }
                val account = RelayAccount(
                    origin = parsed.origin,
                    fingerprint = fingerprint,
                    caPem = caPem,
                    deviceId = enrollment.deviceId,
                    deviceName = deviceName.trim().ifEmpty { DEFAULT_DEVICE_NAME },
                    accountId = enrollment.accountId,
                    enrolledAt = clock(),
                )
                store.save(account, enrollment.token)
                _state.update { state ->
                    state.copy(account = account, signingIn = false, error = null)
                }
            } catch (error: RelayException) {
                fail(error.failure.toRelayError())
            } catch (error: IOException) {
                fail(RelayError.Unreachable)
            }
        }
    }

    fun signOut() {
        _state.update { state -> state.copy(account = null, error = null) }
        scope.launch { store.clear() }
    }

    fun dismissError() {
        _state.update { state -> state.copy(error = null) }
    }

    private suspend fun fetchCa(parsed: RelayPairingInfo): String {
        val caPem = enroller.fetchCa(parsed.origin)
            ?: throw RelayException(RelayFailure.BadResponse)
        val actual = enroller.fingerprintOf(caPem)
            ?: throw RelayException(RelayFailure.BadResponse)
        if (actual != parsed.fingerprint) {
            throw RelayException(RelayFailure.FingerprintMismatch)
        }
        return caPem
    }

    private fun fail(error: RelayError) {
        _state.update { state -> state.copy(signingIn = false, error = error) }
    }

    private fun RelayFailure.toRelayError(): RelayError = when (this) {
        RelayFailure.Unreachable -> RelayError.Unreachable
        RelayFailure.CertificateRejected -> RelayError.FingerprintMismatch
        RelayFailure.FingerprintMismatch -> RelayError.FingerprintMismatch
        RelayFailure.Unauthorized -> RelayError.Unauthorized
        RelayFailure.BadResponse -> RelayError.BadResponse
    }

    private companion object {
        private const val DEFAULT_DEVICE_NAME = "Android"
    }
}
