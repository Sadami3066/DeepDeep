package io.github.whywood.superpi.data.relay

enum class RelayEntryKind {
    Lan,
    Public,
}

data class RelayEntry(
    val kind: RelayEntryKind,
    val origin: String,
)

data class RelayEnrollment(
    val deviceId: String,
    val token: String,
    val accountId: String,
    val entries: List<RelayEntry>,
    val fingerprint: String?,
)

data class RelayPairCode(
    val code: String,
    val expiresAt: Long,
    val entries: List<RelayEntry>,
    val fingerprint: String?,
)

data class RelayAccount(
    val origin: String,
    val fingerprint: String?,
    val caPem: String?,
    val deviceId: String,
    val deviceName: String,
    val accountId: String,
    val enrolledAt: Long,
)

enum class RelayFailure {
    Unreachable,
    CertificateRejected,
    FingerprintMismatch,
    Unauthorized,
    BadResponse,
}

class RelayException(
    val failure: RelayFailure,
    message: String = failure.name,
    cause: Throwable? = null,
) : Exception(message, cause)
