package io.github.whywood.deepdeep.data.remote

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class RemoteComputerStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val _computers = MutableStateFlow(readComputers())

    val computers: StateFlow<List<RemoteComputer>> = _computers.asStateFlow()

    fun cached(): List<RemoteComputer> = _computers.value

    suspend fun save(computers: List<RemoteComputer>): List<RemoteComputer> = mutex.withLock {
        withContext(ioDispatcher) {
            val normalized = normalizeComputers(computers)
            write(normalized)
            _computers.value = normalized
            normalized
        }
    }

    suspend fun upsert(computer: RemoteComputer): List<RemoteComputer> {
        val current = cached()
        val matches = current.filter { item ->
            item.instanceId == computer.instanceId || sameMachine(item, computer)
        }
        val merged = if (matches.isEmpty()) {
            current + computer
        } else {
            val target = matches.first()
            val combined = combine(target, computer, matches.drop(1))
            current.filterNot { item -> matches.any { match -> match.instanceId == item.instanceId } } + combined
        }
        return save(merged)
    }

    suspend fun learnInstallation(computerId: String, installationId: String): RemoteComputer? {
        val normalized = normalizeInstallationId(installationId) ?: return null
        val current = cached()
        val target = current.firstOrNull { it.instanceId == computerId } ?: return null
        if (target.installationId == normalized) return target
        val others = current.filter { item ->
            item.instanceId != computerId && item.installationId == normalized
        }
        val merged = combine(target, target.copy(installationId = normalized), others)
        val next = current.filterNot { item ->
            item.instanceId == computerId || others.any { other -> other.instanceId == item.instanceId }
        } + merged
        save(next)
        return merged
    }

    suspend fun update(instanceId: String, transform: (RemoteComputer) -> RemoteComputer): List<RemoteComputer> =
        save(cached().map { if (it.instanceId == instanceId) transform(it) else it })

    suspend fun rename(instanceId: String, name: String): List<RemoteComputer> =
        update(instanceId) { it.copy(name = name) }

    suspend fun touch(instanceId: String, at: Long = System.currentTimeMillis()): List<RemoteComputer> =
        update(instanceId) { it.copy(lastUsedAt = maxOf(it.lastUsedAt, at)) }

    suspend fun remove(instanceId: String): List<RemoteComputer> =
        save(cached().filterNot { it.instanceId == instanceId })

    private fun write(computers: List<RemoteComputer>) {
        val root = buildJsonObject {
            put(
                "computers",
                buildJsonArray {
                    computers.forEach { computer ->
                        add(
                            buildJsonObject {
                                put("instanceId", computer.instanceId)
                                computer.installationId?.let { put("installationId", it) }
                                put("name", computer.name)
                                put("lastUsedAt", computer.lastUsedAt)
                                put(
                                    "entries",
                                    buildJsonArray {
                                        computer.entries.forEach { entry ->
                                            add(
                                                buildJsonObject {
                                                    put("deviceId", entry.deviceId)
                                                    put("origin", entry.origin)
                                                    put("kind", if (entry.kind == RemoteEntryKind.Lan) "lan" else "public")
                                                    put("deviceExpiresAt", entry.deviceExpiresAt)
                                                    put("sessionExpiresAt", entry.sessionExpiresAt)
                                                    put("lastSeenAt", entry.lastSeenAt)
                                                    entry.certDerBase64?.let { put("certDerBase64", it) }
                                                    entry.certSha256?.let { put("certSha256", it) }
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(root.toString())
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readComputers(): List<RemoteComputer> {
        if (!file.isFile) return emptyList()
        val root = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull() ?: return emptyList()
        val array = runCatching { root["computers"]?.jsonArray }.getOrNull() ?: return emptyList()
        return normalizeComputers(
            array.mapNotNull { element ->
                runCatching {
                    val item = element.jsonObject
                    RemoteComputer(
                        instanceId = item["instanceId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        lastUsedAt = item["lastUsedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                        installationId = item["installationId"]?.jsonPrimitive?.contentOrNull,
                        entries = item["entries"]?.jsonArray.orEmptyJsonArray().mapNotNull { entryElement ->
                            runCatching {
                                val entry = entryElement.jsonObject
                                val origin = entry["origin"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val kind = entry["kind"]?.jsonPrimitive?.contentOrNull
                                RemoteEntry(
                                    deviceId = entry["deviceId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                    origin = origin,
                                    kind = parseKind(kind, origin),
                                    deviceExpiresAt = entry["deviceExpiresAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                                    sessionExpiresAt = entry["sessionExpiresAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                                    certDerBase64 = entry["certDerBase64"]?.jsonPrimitive?.contentOrNull,
                                    certSha256 = entry["certSha256"]?.jsonPrimitive?.contentOrNull,
                                    lastSeenAt = entry["lastSeenAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                                )
                            }.getOrNull()
                        },
                    )
                }.getOrNull()
            },
        )
    }
}

internal fun normalizeComputers(computers: List<RemoteComputer>): List<RemoteComputer> {
    val normalized = ArrayList<RemoteComputer>()
    val seen = LinkedHashSet<String>()
    for (computer in computers) {
        val instanceId = computer.instanceId.trim()
        if (instanceId.isEmpty() || instanceId.length > MAX_INSTANCE_ID_LENGTH) continue
        val installationId = normalizeInstallationId(computer.installationId)
        val entries = normalizeEntries(computer.entries)
        if (entries.isEmpty()) continue
        val item = RemoteComputer(
            instanceId = instanceId,
            name = computer.name.trim().take(MAX_NAME_LENGTH),
            lastUsedAt = computer.lastUsedAt,
            entries = entries,
            installationId = installationId,
        )
        val machine = item.machineId()
        if (!seen.add(machine)) {
            val index = normalized.indexOfFirst { existing -> existing.machineId() == machine }
            if (index >= 0) normalized[index] = combine(normalized[index], item, emptyList())
            continue
        }
        normalized += item
    }
    return normalized.take(MAX_COMPUTERS)
}

internal fun normalizeEntries(entries: List<RemoteEntry>): List<RemoteEntry> {
    val seen = LinkedHashSet<String>()
    return entries.mapNotNull { entry ->
        val origin = entry.origin.trim().trimEnd('/')
        if (origin.isEmpty() || origin.length > MAX_ORIGIN_LENGTH) return@mapNotNull null
        if (!origin.startsWith("https://")) return@mapNotNull null
        if (originHost(origin) == null) return@mapNotNull null
        if (!seen.add(origin)) return@mapNotNull null
        val deviceId = entry.deviceId.trim()
        if (deviceId.isEmpty() || deviceId.length > MAX_DEVICE_ID_LENGTH) return@mapNotNull null
        entry.copy(
            deviceId = deviceId,
            origin = origin,
            kind = parseKind(if (entry.kind == RemoteEntryKind.Lan) "lan" else "public", origin),
            certDerBase64 = entry.certDerBase64?.takeIf { it.isNotBlank() },
            certSha256 = entry.certSha256?.takeIf { it.isNotBlank() },
        )
    }.take(MAX_ENTRIES)
}

internal fun mergeEntries(existing: List<RemoteEntry>, incoming: List<RemoteEntry>): List<RemoteEntry> {
    val incomingByOrigin = incoming.associateBy { it.origin }
    val replaced = existing.map { entry -> incomingByOrigin[entry.origin] ?: entry }
    val existingOrigins = existing.mapTo(LinkedHashSet()) { it.origin }
    return replaced + incoming.filterNot { it.origin in existingOrigins }
}

internal fun combine(
    target: RemoteComputer,
    incoming: RemoteComputer,
    extras: List<RemoteComputer>,
): RemoteComputer {
    val all = listOf(incoming) + extras
    val installationId = target.installationId
        ?: all.firstNotNullOfOrNull { it.installationId }
    val entries = all.fold(target.entries) { acc, computer -> mergeEntries(acc, computer.entries) }
    val lastUsedAt = all.fold(target.lastUsedAt) { acc, computer -> maxOf(acc, computer.lastUsedAt) }
    return target.copy(
        name = target.name.ifBlank { incoming.name },
        lastUsedAt = lastUsedAt,
        entries = entries,
        installationId = installationId,
    )
}

private fun sameMachine(left: RemoteComputer, right: RemoteComputer): Boolean =
    left.installationId != null && left.installationId == right.installationId

private fun normalizeInstallationId(raw: String?): String? {
    val value = raw?.trim()?.lowercase() ?: return null
    if (value.length != INSTALLATION_ID_LENGTH) return null
    if (value.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
    return value
}

private fun parseKind(raw: String?, origin: String): RemoteEntryKind {
    when (raw) {
        "lan" -> return RemoteEntryKind.Lan
        "public" -> return RemoteEntryKind.Public
    }
    val host = originHost(origin) ?: return RemoteEntryKind.Public
    return if (isLanHost(host)) RemoteEntryKind.Lan else RemoteEntryKind.Public
}

private fun List<kotlinx.serialization.json.JsonElement>?.orEmptyJsonArray() = this ?: emptyList()

private const val MAX_COMPUTERS = 20
private const val MAX_ENTRIES = 4
private const val MAX_NAME_LENGTH = 32
private const val MAX_INSTANCE_ID_LENGTH = 128
private const val MAX_DEVICE_ID_LENGTH = 128
private const val MAX_ORIGIN_LENGTH = 2048
private const val INSTALLATION_ID_LENGTH = 64
