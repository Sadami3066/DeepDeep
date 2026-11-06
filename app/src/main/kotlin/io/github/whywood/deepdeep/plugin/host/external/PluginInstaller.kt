package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.HOST_UI_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginVersion
import io.github.whywood.deepdeep.plugin.host.HostToolNames
import io.github.whywood.deepdeep.plugin.host.PluginPaths
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PluginInstaller(
    internal val pluginsRoot: File,
    private val trustStore: PublisherTrustStore,
    internal val recordStore: ExternalPluginRecordStore,
    hostApiLevel: Int = PLUGIN_API_LEVEL,
    hostVersion: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val stager = PluginPackageStager(pluginsRoot, hostApiLevel, hostVersion, clock)
    private val clock = clock

    suspend fun inspect(packageFile: File): InspectionResult = withContext(ioDispatcher) {
        when (val result = stager.readCandidate(packageFile)) {
            is CandidateResult.Rejected -> InspectionResult.Rejected(result.code, result.reason)
            is CandidateResult.Ok -> {
                val candidate = result.candidate
                InspectionResult.Ready(
                    manifest = candidate.manifest,
                    publisher = candidate.publisher,
                    digests = candidate.digests,
                    trusted = trustStore.trusted(candidate.publisher.keyId),
                    installed = recordStore.get(candidate.manifest.id),
                )
            }
        }
    }

    suspend fun install(packageFile: File, acceptPublisher: Boolean = false): InstallResult = withContext(ioDispatcher) {
        val candidate = when (val result = stager.readCandidate(packageFile)) {
            is CandidateResult.Rejected -> return@withContext InstallResult.Rejected(result.code, result.reason)
            is CandidateResult.Ok -> result.candidate
        }
        val manifest = candidate.manifest
        val pluginId = manifest.id
        val toolConflicts = manifest.provides.tools.filter(HostToolNames::isReserved)
        if (toolConflicts.isNotEmpty()) {
            return@withContext InstallResult.Rejected(
                InstallRejection.TOOL_CONFLICT,
                "tool names are already in use: ${toolConflicts.sorted().joinToString()}",
            )
        }
        val trusted = trustStore.trusted(candidate.publisher.keyId)
        if (trusted != null && trusted.publicKey != candidate.publisher.publicKey) {
            return@withContext InstallResult.Rejected(
                InstallRejection.PUBLISHER_CONFLICT,
                "trusted publisher ${candidate.publisher.keyId} uses a different public key",
            )
        }
        val existing = recordStore.get(pluginId)
        if (existing != null && existing.publisherKeyId != candidate.publisher.keyId) {
            return@withContext InstallResult.Rejected(
                InstallRejection.PUBLISHER_CONFLICT,
                "plugin '$pluginId' was installed by a different publisher key",
            )
        }
        if (existing == null) {
            if (trusted == null && !acceptPublisher) {
                return@withContext InstallResult.NeedsPublisherConfirmation(
                    manifest,
                    candidate.publisher,
                    candidate.digests,
                )
            }
            installFresh(candidate)
        } else {
            installUpdate(
                existing = existing,
                candidate = candidate,
                publisherTrusted = trusted != null,
                publisherAccepted = trusted != null || acceptPublisher,
            )
        }
    }

    suspend fun uninstall(pluginId: String): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        recordStore.remove(record.id)
        runCatching { PluginPaths.pluginRoot(pluginsRoot, record.id).deleteRecursively() }
        true
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        recordStore.upsert(record.copy(enabled = enabled))
        true
    }

    suspend fun updateSkills(pluginId: String, skillIds: List<String>): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        recordStore.upsert(record.copy(skillIds = skillIds))
        true
    }

    suspend fun markFailure(pluginId: String, error: String?): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        recordStore.upsert(record.copy(lastError = error))
        true
    }

    suspend fun record(pluginId: String): ExternalPluginRecord? = withContext(ioDispatcher) {
        recordStore.get(pluginId)
    }

    suspend fun records(): List<ExternalPluginRecord> = withContext(ioDispatcher) {
        recordStore.records()
    }

    suspend fun activeBundle(pluginId: String): BundleState = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext BundleState.Absent
        stager.bundleState(pluginId, record.version, record.installPath, record.activeBundleSha256)
    }

    suspend fun pendingBundle(pluginId: String): BundleState = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext BundleState.Absent
        val pendingVersion = record.pendingVersion ?: return@withContext BundleState.Absent
        val pendingSha256 = record.pendingBundleSha256
            ?: return@withContext BundleState.Missing("pending bundle sha256 is missing")
        stager.bundleState(pluginId, pendingVersion, stager.pendingInstallPath(pluginId, pendingVersion), pendingSha256)
    }

    suspend fun commitPending(pluginId: String): CommitResult = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext CommitResult.Rejected("plugin is not installed")
        val pendingVersion = record.pendingVersion
            ?: return@withContext CommitResult.Rejected("plugin has no pending update")
        val pendingSha256 = record.pendingBundleSha256
            ?: return@withContext CommitResult.Rejected("pending bundle sha256 is missing")
        val pendingPath = stager.pendingInstallPath(pluginId, pendingVersion)
        val pendingBundle = stager.bundleState(pluginId, pendingVersion, pendingPath, pendingSha256)
        if (pendingBundle !is BundleState.Ready) {
            return@withContext CommitResult.Rejected(
                "pending bundle failed verification: ${stager.describe(pendingBundle)}",
            )
        }
        val updated = record.copy(
            version = pendingVersion,
            installPath = pendingPath,
            activeBundleSha256 = pendingSha256,
            pendingVersion = null,
            pendingBundleSha256 = null,
            previousVersion = record.version,
            previousInstallPath = record.installPath,
            previousBundleSha256 = record.activeBundleSha256,
            lastError = null,
        )
        recordStore.upsert(updated)
        stager.pruneAbandonedVersion(
            pluginId = pluginId,
            path = record.previousInstallPath,
            keep = setOf(updated.installPath, updated.previousInstallPath.orEmpty()),
        )
        CommitResult.Committed(updated)
    }

    suspend fun discardPending(pluginId: String, error: String): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        val pendingVersion = record.pendingVersion ?: return@withContext false
        recordStore.upsert(
            record.copy(
                pendingVersion = null,
                pendingBundleSha256 = null,
                lastError = error,
            ),
        )
        runCatching {
            PluginPaths.versionRoot(pluginsRoot, pluginId, pendingVersion).deleteRecursively()
        }
        true
    }

    private suspend fun installFresh(candidate: Candidate): InstallResult {
        val pluginId = candidate.manifest.id
        val version = candidate.manifest.version
        val versionRoot = PluginPaths.versionRoot(pluginsRoot, pluginId, version)
        val staged = try {
            stager.stage(candidate, versionRoot)
        } catch (error: Exception) {
            return InstallResult.Rejected(InstallRejection.STORAGE_ERROR, "failed to stage plugin: ${error.message}")
        }
        val trustResult = try {
            trustStore.trust(
                TrustedPublisher(
                    keyId = candidate.publisher.keyId,
                    name = candidate.publisher.name ?: pluginId,
                    publicKey = candidate.publisher.publicKey,
                    firstSeenAt = clock(),
                ),
            )
        } catch (error: Exception) {
            runCatching { staged.deleteRecursively() }
            return InstallResult.Rejected(InstallRejection.STORAGE_ERROR, "failed to record publisher: ${error.message}")
        }
        if (trustResult is TrustResult.Conflict) {
            runCatching { staged.deleteRecursively() }
            return InstallResult.Rejected(
                InstallRejection.PUBLISHER_CONFLICT,
                "publisher key ${candidate.publisher.keyId} is already trusted with another public key",
            )
        }
        val record = ExternalPluginRecord(
            id = pluginId,
            version = version,
            installedAt = clock(),
            enabled = candidate.manifest.defaultEnabled,
            installPath = versionRoot.absolutePath,
            activeBundleSha256 = candidate.digests.bundleSha256,
            size = candidate.digests.size,
            publisherKeyId = candidate.publisher.keyId,
            apiLevel = candidate.manifest.apiLevel,
            uiApiLevel = candidate.manifest.ui?.apiLevel ?: HOST_UI_API_LEVEL,
        )
        return try {
            recordStore.upsert(record)
            InstallResult.Installed(record)
        } catch (error: Exception) {
            runCatching { staged.deleteRecursively() }
            InstallResult.Rejected(InstallRejection.STORAGE_ERROR, "failed to record plugin: ${error.message}")
        }
    }

    private suspend fun installUpdate(
        existing: ExternalPluginRecord,
        candidate: Candidate,
        publisherTrusted: Boolean,
        publisherAccepted: Boolean,
    ): InstallResult {
        if (existing.pendingVersion != null) {
            return InstallResult.Rejected(
                InstallRejection.PENDING_CONFLICT,
                "plugin has a pending update to ${existing.pendingVersion}",
            )
        }
        if (existing.version == candidate.manifest.version) {
            if (existing.activeBundleSha256.equals(candidate.digests.bundleSha256, ignoreCase = true)) {
                return InstallResult.AlreadyInstalled(existing)
            }
            return InstallResult.Rejected(
                InstallRejection.VERSION_CONFLICT,
                "version ${candidate.manifest.version} is already installed with different content",
            )
        }
        val comparison = PluginVersion.compare(candidate.manifest.version, existing.version)
        if (comparison == null) {
            return InstallResult.Rejected(
                InstallRejection.VERSION_CONFLICT,
                "cannot compare version ${candidate.manifest.version} with ${existing.version}",
            )
        }
        if (comparison <= 0) {
            return InstallResult.Rejected(
                InstallRejection.VERSION_CONFLICT,
                "version ${candidate.manifest.version} is not newer than ${existing.version}",
            )
        }
        if (!publisherAccepted) {
            return InstallResult.NeedsPublisherConfirmation(
                candidate.manifest,
                candidate.publisher,
                candidate.digests,
            )
        }
        val versionRoot = PluginPaths.versionRoot(pluginsRoot, existing.id, candidate.manifest.version)
        try {
            stager.stage(candidate, versionRoot)
        } catch (error: Exception) {
            return InstallResult.Rejected(InstallRejection.STORAGE_ERROR, "failed to stage plugin: ${error.message}")
        }
        if (!publisherTrusted) {
            val trustResult = try {
                trustStore.trust(
                    TrustedPublisher(
                        keyId = candidate.publisher.keyId,
                        name = candidate.publisher.name ?: existing.id,
                        publicKey = candidate.publisher.publicKey,
                        firstSeenAt = clock(),
                    ),
                )
            } catch (error: Exception) {
                runCatching { versionRoot.deleteRecursively() }
                return InstallResult.Rejected(InstallRejection.STORAGE_ERROR, "failed to record publisher: ${error.message}")
            }
            if (trustResult is TrustResult.Conflict) {
                runCatching { versionRoot.deleteRecursively() }
                return InstallResult.Rejected(
                    InstallRejection.PUBLISHER_CONFLICT,
                    "publisher key ${candidate.publisher.keyId} is already trusted with another public key",
                )
            }
        }
        val updated = existing.copy(
            pendingVersion = candidate.manifest.version,
            pendingBundleSha256 = candidate.digests.bundleSha256,
            lastError = null,
        )
        return try {
            recordStore.upsert(updated)
            InstallResult.Staged(updated)
        } catch (error: Exception) {
            runCatching { versionRoot.deleteRecursively() }
            InstallResult.Rejected(InstallRejection.STORAGE_ERROR, "failed to record pending update: ${error.message}")
        }
    }
}
