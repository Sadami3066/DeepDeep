package io.github.whywood.deepdeep.data.remote

data class RemoteComputer(
    val instanceId: String,
    val name: String,
    val lastUsedAt: Long,
    val entries: List<RemoteEntry>,
    val installationId: String? = null,
)

internal fun RemoteComputer.machineId(): String = installationId ?: instanceId

enum class RemoteEntryKind {
    Lan,
    Public,
}

enum class RemoteEntryAuth {
    Native,
    Browser,
}

data class RemoteEntry(
    val deviceId: String,
    val origin: String,
    val kind: RemoteEntryKind,
    val deviceExpiresAt: Long,
    val sessionExpiresAt: Long,
    val certDerBase64: String?,
    val certSha256: String?,
    val lastSeenAt: Long,
    val auth: RemoteEntryAuth = RemoteEntryAuth.Native,
)

data class RemoteEntrySecrets(
    val deviceToken: String,
    val sessionToken: String?,
    val csrfToken: String?,
)

enum class RemoteReachability {
    Unknown,
    OnlineLan,
    OnlinePublic,
    Offline,
    NeedsPairing,
    CertChanged,
}

data class RemoteStatus(
    val computer: RemoteComputer,
    val reachability: RemoteReachability,
)

data class RemoteSession(
    val computer: RemoteComputer,
    val entry: RemoteEntry,
    val startUrl: String,
)
