package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginVersion
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord

internal data class UpdatePolicy(
    val hostApiLevel: Int = PLUGIN_API_LEVEL,
    val hostVersion: String? = null,
    val maxPackageBytes: Long = PluginUpdateDefaults.MAX_PACKAGE_BYTES,
)

internal data class PluginUpdateCandidate(
    val entry: PluginIndexEntry,
    val installedVersion: String,
    val installedKeyId: String,
)

internal enum class UpdateRejection {
    INVALID_ENTRY,
    UNKNOWN_PLUGIN,
    NOT_NEWER,
    PENDING_EXISTS,
    PUBLISHER_MISMATCH,
    PUBLISHER_UNTRUSTED,
    API_LEVEL_UNSUPPORTED,
    HOST_VERSION_UNSUPPORTED,
    PACKAGE_TOO_LARGE,
    DOWNLOAD_FAILED,
    SIZE_MISMATCH,
    SHA256_MISMATCH,
    PACKAGE_INVALID,
    SIGNATURE_INVALID,
    MANIFEST_INVALID,
    ID_MISMATCH,
    VERSION_MISMATCH,
    INSTALL_REJECTED,
    STORAGE_ERROR,
}

internal sealed interface CandidateCheck {
    data object Ok : CandidateCheck

    data class Rejected(val code: UpdateRejection, val reason: String) : CandidateCheck
}

internal object PluginUpdateSelector {
    fun select(
        index: PluginIndex,
        installed: Map<String, ExternalPluginRecord>,
        policy: UpdatePolicy = UpdatePolicy(),
    ): List<PluginUpdateCandidate> {
        val candidates = ArrayList<PluginUpdateCandidate>(index.plugins.size)
        index.plugins.forEach { entry ->
            val record = installed[entry.id] ?: return@forEach
            if (checkUpdate(record, entry) !is CandidateCheck.Ok) return@forEach
            if (ensureAcceptable(entry, policy) !is CandidateCheck.Ok) return@forEach
            candidates += PluginUpdateCandidate(
                entry = entry,
                installedVersion = record.version,
                installedKeyId = record.publisherKeyId,
            )
        }
        return candidates.sortedBy { it.entry.id }
    }

    fun checkUpdate(record: ExternalPluginRecord, entry: PluginIndexEntry): CandidateCheck {
        if (record.pendingVersion != null) {
            return CandidateCheck.Rejected(
                UpdateRejection.PENDING_EXISTS,
                "plugin '${record.id}' already has a pending update to ${record.pendingVersion}",
            )
        }
        if (!record.publisherKeyId.equals(entry.publisherKeyId, ignoreCase = true)) {
            return CandidateCheck.Rejected(
                UpdateRejection.PUBLISHER_MISMATCH,
                "plugin '${record.id}' was installed by ${record.publisherKeyId}, index entry is signed by ${entry.publisherKeyId}",
            )
        }
        val comparison = PluginVersion.compare(entry.version, record.version)
            ?: return CandidateCheck.Rejected(
                UpdateRejection.INVALID_ENTRY,
                "cannot compare version ${entry.version} with installed ${record.version}",
            )
        if (comparison <= 0) {
            return CandidateCheck.Rejected(
                UpdateRejection.NOT_NEWER,
                "version ${entry.version} is not newer than installed ${record.version}",
            )
        }
        return CandidateCheck.Ok
    }

    fun ensureAcceptable(entry: PluginIndexEntry, policy: UpdatePolicy = UpdatePolicy()): CandidateCheck {
        if (entry.apiLevel !in 1..policy.hostApiLevel) {
            return CandidateCheck.Rejected(
                UpdateRejection.API_LEVEL_UNSUPPORTED,
                "plugin '${entry.id}' requires apiLevel ${entry.apiLevel}, host supports ${policy.hostApiLevel}",
            )
        }
        if (entry.size <= 0 || entry.size > policy.maxPackageBytes) {
            return CandidateCheck.Rejected(
                UpdateRejection.PACKAGE_TOO_LARGE,
                "plugin '${entry.id}' declares ${entry.size} bytes, limit is ${policy.maxPackageBytes}",
            )
        }
        val minHostVersion = entry.minHostVersion ?: return CandidateCheck.Ok
        val minimum = PluginVersion.parse(minHostVersion)
            ?: return CandidateCheck.Rejected(
                UpdateRejection.INVALID_ENTRY,
                "invalid minHostVersion: $minHostVersion",
            )
        val host = policy.hostVersion?.let { PluginVersion.parse(it) }
            ?: return CandidateCheck.Rejected(
                UpdateRejection.HOST_VERSION_UNSUPPORTED,
                "plugin '${entry.id}' requires host >= $minHostVersion, host version is unknown",
            )
        if (host < minimum) {
            return CandidateCheck.Rejected(
                UpdateRejection.HOST_VERSION_UNSUPPORTED,
                "plugin '${entry.id}' requires host >= $minHostVersion, host is ${policy.hostVersion}",
            )
        }
        return CandidateCheck.Ok
    }
}
