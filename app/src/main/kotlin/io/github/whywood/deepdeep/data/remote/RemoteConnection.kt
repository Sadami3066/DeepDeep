package io.github.whywood.deepdeep.data.remote

import android.os.Build
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RemoteConnection(
    private val store: RemoteComputerStore,
    private val secrets: RemoteSecrets,
    private val browser: RemoteBrowserClient,
    private val client: RemoteGatewayClient = RemoteGatewayClient(),
    scope: CoroutineScope,
) {
    private val reachability = MutableStateFlow<Map<String, RemoteReachability>>(emptyMap())

    val statuses: StateFlow<List<RemoteStatus>> = combine(store.computers, reachability) { computers, states ->
        computers.map { computer -> RemoteStatus(computer, states[computer.instanceId] ?: RemoteReachability.Unknown) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun refresh() {
        val computers = store.cached()
        if (computers.isEmpty()) {
            reachability.value = emptyMap()
            return
        }
        val results = coroutineScope {
            computers.map { computer -> async { computer to probe(computer) } }.awaitAll()
        }
        val states = LinkedHashMap<String, RemoteReachability>()
        for ((computer, outcome) in results) {
            val id = learnInstallation(computer.instanceId, outcome.installationId)
            states[id] = outcome.reachability
            applyProbe(id, outcome)
        }
        reachability.value = states
    }

    suspend fun refreshComputer(instanceId: String) {
        val computer = store.cached().firstOrNull { it.instanceId == instanceId } ?: return
        val outcome = probe(computer)
        val id = learnInstallation(instanceId, outcome.installationId)
        applyProbe(id, outcome)
        reachability.value = reachability.value + (id to outcome.reachability)
    }

    suspend fun pair(link: RemotePairingLink, name: String): RemoteComputer {
        if (link.kind == RemoteEntryKind.Public) {
            val paired = runCatching { pairWithBrowser(link, name) }.getOrNull()
            if (paired != null) return paired
        }
        return pairWithNative(link, name)
    }

    private suspend fun pairWithBrowser(link: RemotePairingLink, name: String): RemoteComputer {
        val pairing = browser.pair(link.origin, link.token, deviceLabel())
        val entry = RemoteEntry(
            deviceId = pairing.deviceId ?: link.instanceId,
            origin = link.origin,
            kind = RemoteEntryKind.Public,
            deviceExpiresAt = 0L,
            sessionExpiresAt = pairing.sessionExpiresAt,
            certDerBase64 = null,
            certSha256 = null,
            lastSeenAt = System.currentTimeMillis(),
            auth = RemoteEntryAuth.Browser,
        )
        return persistPairing(entry, link.instanceId, link.instanceId, name, credentials = null)
    }

    private suspend fun pairWithNative(link: RemotePairingLink, name: String): RemoteComputer {
        val pairing = client.pair(link, deviceLabel())
        val entry = RemoteEntry(
            deviceId = pairing.deviceId,
            origin = link.origin,
            kind = link.kind,
            deviceExpiresAt = pairing.deviceExpiresAt,
            sessionExpiresAt = pairing.sessionExpiresAt,
            certDerBase64 = pairing.certDerBase64,
            certSha256 = pairing.certSha256,
            lastSeenAt = System.currentTimeMillis(),
        )
        return persistPairing(
            entry = entry,
            instanceId = pairing.instanceId,
            installationId = pairing.installationId,
            name = name,
            credentials = RemoteEntrySecrets(
                deviceToken = pairing.deviceToken,
                sessionToken = pairing.sessionToken,
                csrfToken = pairing.csrfToken,
            ),
        )
    }

    private suspend fun persistPairing(
        entry: RemoteEntry,
        instanceId: String,
        installationId: String?,
        name: String,
        credentials: RemoteEntrySecrets?,
    ): RemoteComputer {
        val previous = store.cached().firstOrNull { computer ->
            computer.instanceId == instanceId ||
                (installationId != null && computer.installationId == installationId)
        }
        store.upsert(
            RemoteComputer(
                instanceId = instanceId,
                name = name.trim().take(MAX_NAME_LENGTH),
                lastUsedAt = System.currentTimeMillis(),
                entries = listOf(entry),
                installationId = installationId,
            ),
        )
        if (credentials != null) secrets.save(entry.deviceId, credentials)
        previous?.entries.orEmpty()
            .filter { old -> old.origin == entry.origin && old.deviceId != entry.deviceId }
            .forEach { old ->
                if (old.auth == RemoteEntryAuth.Browser) {
                    browser.clearCookies(old.origin)
                } else {
                    secrets.remove(old.deviceId)
                }
            }
        val machine = installationId ?: instanceId
        val computer = store.cached().firstOrNull { it.machineId() == machine }
            ?: store.cached().first { it.instanceId == instanceId }
        reachability.value = reachability.value + (
            computer.instanceId to when (entry.kind) {
                RemoteEntryKind.Lan -> RemoteReachability.OnlineLan
                RemoteEntryKind.Public -> RemoteReachability.OnlinePublic
            }
            )
        return computer
    }

    suspend fun rename(instanceId: String, name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        store.rename(instanceId, trimmed)
    }

    suspend fun remove(instanceId: String) {
        val computer = store.cached().firstOrNull { it.instanceId == instanceId } ?: return
        computer.entries.forEach { entry ->
            if (entry.auth == RemoteEntryAuth.Browser) {
                browser.clearCookies(entry.origin)
            } else {
                secrets.remove(entry.deviceId)
            }
        }
        store.remove(instanceId)
        reachability.value = reachability.value - instanceId
    }

    suspend fun connect(instanceId: String): RemoteSession {
        val computer = store.cached().firstOrNull { it.instanceId == instanceId }
            ?: throw RemoteGatewayException(RemoteGatewayFailure.BadResponse)
        reachability.value[instanceId]?.blockingFailure()?.let { failure ->
            throw RemoteGatewayException(failure)
        }
        var failure: RemoteGatewayFailure? = null
        for (entry in connectOrder(computer)) {
            if (entry.auth == RemoteEntryAuth.Browser) {
                try {
                    ensureBrowserSession(entry)
                    store.touch(instanceId)
                    return RemoteSession(computer, entry, "${entry.origin.trimEnd('/')}/")
                } catch (error: RemoteGatewayException) {
                    if (failure == null) failure = error.failure
                }
                continue
            }
            val tokens = secrets.read(entry.deviceId) ?: continue
            try {
                val currentEntry = if (sessionExpired(entry, tokens)) {
                    renewSession(instanceId, entry, tokens)
                } else {
                    entry
                }
                val currentTokens = secrets.read(currentEntry.deviceId) ?: continue
                val sessionToken = currentTokens.sessionToken ?: continue
                val csrfToken = currentTokens.csrfToken ?: continue
                writeCookies(currentEntry.origin, sessionToken, csrfToken, currentEntry.sessionExpiresAt)
                store.touch(instanceId)
                return RemoteSession(computer, currentEntry, "${currentEntry.origin.trimEnd('/')}/")
            } catch (error: RemoteGatewayException) {
                if (failure == null) failure = error.failure
            }
        }
        throw RemoteGatewayException(failure ?: RemoteGatewayFailure.Unreachable)
    }

    private fun sessionExpired(entry: RemoteEntry, tokens: RemoteEntrySecrets): Boolean {
        val now = System.currentTimeMillis()
        return tokens.sessionToken == null ||
            tokens.csrfToken == null ||
            entry.sessionExpiresAt - now <= RENEW_WINDOW_MS
    }

    private suspend fun renewSession(
        instanceId: String,
        entry: RemoteEntry,
        tokens: RemoteEntrySecrets,
    ): RemoteEntry {
        val renewal = client.renew(
            origin = entry.origin,
            certDerBase64 = entry.certDerBase64,
            deviceToken = tokens.deviceToken,
            remote = entry.kind == RemoteEntryKind.Public,
        )
        val updated = entry.copy(
            sessionExpiresAt = renewal.sessionExpiresAt,
            lastSeenAt = System.currentTimeMillis(),
        )
        secrets.save(
            entry.deviceId,
            tokens.copy(sessionToken = renewal.sessionToken, csrfToken = renewal.csrfToken),
        )
        store.update(instanceId) { computer ->
            computer.copy(
                entries = computer.entries.map { item -> if (item.origin == entry.origin) updated else item },
            )
        }
        return updated
    }

    private suspend fun probe(computer: RemoteComputer): ProbeOutcome {
        var state: RemoteReachability? = null
        var installationId = computer.installationId
        for (entry in probeOrder(computer)) {
            if (entry.auth == RemoteEntryAuth.Browser) {
                when (browser.state(entry.origin)) {
                    RemoteBrowserState.Online -> return ProbeOutcome(
                        reachability = RemoteReachability.OnlinePublic,
                        origin = entry.origin,
                        lastSeenAt = System.currentTimeMillis(),
                        installationId = installationId,
                    )
                    RemoteBrowserState.NeedsPairing -> {
                        state = mergeState(state, RemoteReachability.NeedsPairing)
                        continue
                    }
                    RemoteBrowserState.Offline -> {
                        state = mergeState(state, RemoteReachability.Offline)
                        continue
                    }
                }
            }
            val tokens = secrets.read(entry.deviceId) ?: continue
            try {
                val probe = client.probe(
                    origin = entry.origin,
                    certDerBase64 = entry.certDerBase64,
                    deviceToken = tokens.deviceToken,
                    remote = entry.kind == RemoteEntryKind.Public,
                )
                installationId = probe.installationId ?: installationId
                val online = if (probe.deviceExpiresAt > System.currentTimeMillis()) {
                    when (entry.kind) {
                        RemoteEntryKind.Lan -> RemoteReachability.OnlineLan
                        RemoteEntryKind.Public -> RemoteReachability.OnlinePublic
                    }
                } else {
                    RemoteReachability.NeedsPairing
                }
                return ProbeOutcome(
                    reachability = online,
                    origin = entry.origin,
                    deviceExpiresAt = probe.deviceExpiresAt,
                    lastSeenAt = System.currentTimeMillis(),
                    installationId = installationId,
                )
            } catch (error: RemoteGatewayException) {
                state = mergeState(state, stateFor(error))
            }
        }
        return ProbeOutcome(reachability = state ?: RemoteReachability.Offline, installationId = installationId)
    }

    private suspend fun ensureBrowserSession(entry: RemoteEntry) {
        if (entry.sessionExpiresAt - System.currentTimeMillis() > RENEW_WINDOW_MS) return
        when (browser.state(entry.origin)) {
            RemoteBrowserState.Online -> return
            RemoteBrowserState.NeedsPairing -> {
                if (!browser.renew(entry.origin)) throw RemoteGatewayException(RemoteGatewayFailure.Unauthorized)
                if (browser.state(entry.origin) != RemoteBrowserState.Online) {
                    throw RemoteGatewayException(RemoteGatewayFailure.Unauthorized)
                }
            }
            RemoteBrowserState.Offline -> throw RemoteGatewayException(RemoteGatewayFailure.Unreachable)
        }
    }

    private suspend fun learnInstallation(instanceId: String, installationId: String?): String {
        if (installationId == null) return instanceId
        val computer = store.cached().firstOrNull { it.instanceId == instanceId } ?: return instanceId
        if (computer.installationId == installationId) return instanceId
        return store.learnInstallation(instanceId, installationId)?.instanceId ?: instanceId
    }

    private suspend fun applyProbe(instanceId: String, outcome: ProbeOutcome) {
        val origin = outcome.origin ?: return
        store.update(instanceId) { computer ->
            computer.copy(
                entries = computer.entries.map { entry ->
                    if (entry.origin != origin) {
                        entry
                    } else {
                        entry.copy(
                            deviceExpiresAt = outcome.deviceExpiresAt ?: entry.deviceExpiresAt,
                            lastSeenAt = outcome.lastSeenAt ?: entry.lastSeenAt,
                        )
                    }
                },
            )
        }
    }

    private fun connectOrder(computer: RemoteComputer): List<RemoteEntry> {
        val publicFirst = reachability.value[computer.instanceId] == RemoteReachability.OnlinePublic
        return computer.entries.sortedWith(
            compareBy { entry ->
                if (publicFirst) entry.kind != RemoteEntryKind.Public else entry.kind == RemoteEntryKind.Public
            },
        )
    }

    private fun probeOrder(computer: RemoteComputer): List<RemoteEntry> =
        computer.entries.sortedBy { entry -> entry.kind == RemoteEntryKind.Public }

    private fun stateFor(error: RemoteGatewayException): RemoteReachability = when (error.failure) {
        RemoteGatewayFailure.Unauthorized, RemoteGatewayFailure.Forbidden -> RemoteReachability.NeedsPairing
        RemoteGatewayFailure.CertificateRejected -> RemoteReachability.CertChanged
        else -> RemoteReachability.Offline
    }

    private fun mergeState(current: RemoteReachability?, next: RemoteReachability): RemoteReachability {
        if (current == null) return next
        val priority = listOf(
            RemoteReachability.NeedsPairing,
            RemoteReachability.CertChanged,
            RemoteReachability.Offline,
        )
        return if (priority.indexOf(next) < priority.indexOf(current)) next else current
    }

    private fun writeCookies(
        origin: String,
        sessionToken: String,
        csrfToken: String,
        sessionExpiresAt: Long,
    ) {
        val maxAge = ((sessionExpiresAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        val manager = CookieManager.getInstance()
        val target = "${origin.trimEnd('/')}/"
        listOf(
            "dsh_ma_session=$sessionToken; Path=/; Max-Age=$maxAge; HttpOnly; Secure; SameSite=Strict",
            "dsh_ma_csrf=$csrfToken; Path=/; Max-Age=$maxAge; Secure; SameSite=Strict",
            "__Host-dsh_ma_session=$sessionToken; Path=/; Max-Age=$maxAge; HttpOnly; Secure; SameSite=Strict",
            "__Host-dsh_ma_csrf=$csrfToken; Path=/; Max-Age=$maxAge; Secure; SameSite=Strict",
        ).forEach { cookie -> manager.setCookie(target, cookie) }
        manager.flush()
    }

    private fun deviceLabel(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val label = when {
            model.isEmpty() -> manufacturer
            manufacturer.isEmpty() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
        return label.ifBlank { "DeepDeep" }.take(MAX_LABEL_LENGTH)
    }

    private data class ProbeOutcome(
        val reachability: RemoteReachability,
        val origin: String? = null,
        val deviceExpiresAt: Long? = null,
        val lastSeenAt: Long? = null,
        val installationId: String? = null,
    )

    private companion object {
        const val RENEW_WINDOW_MS = 5 * 60 * 1000L
        const val MAX_NAME_LENGTH = 32
        const val MAX_LABEL_LENGTH = 64
    }
}

internal fun RemoteReachability.blockingFailure(): RemoteGatewayFailure? = when (this) {
    RemoteReachability.NeedsPairing -> RemoteGatewayFailure.Unauthorized
    RemoteReachability.CertChanged -> RemoteGatewayFailure.CertificateRejected
    else -> null
}
