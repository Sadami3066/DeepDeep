package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.HOST_UI_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.api.PluginVersion
import io.github.whywood.deepdeep.plugin.host.PluginPaths
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class InstallRejection {
    PACKAGE_INVALID,
    SIGNATURE_INVALID,
    MANIFEST_INVALID,
    ID_CONFLICT,
    VERSION_CONFLICT,
    PUBLISHER_CONFLICT,
    TOOL_CONFLICT,
    PENDING_CONFLICT,
    STORAGE_ERROR,
}

internal sealed interface InspectionResult {
    data class Ready(
        val manifest: PluginManifest,
        val publisher: VerifiedPublisher,
        val digests: PackageDigests,
        val trusted: TrustedPublisher?,
        val installed: ExternalPluginRecord?,
    ) : InspectionResult

    data class Rejected(val code: InstallRejection, val reason: String) : InspectionResult
}

internal sealed interface InstallResult {
    data class Installed(val record: ExternalPluginRecord) : InstallResult

    data class Staged(val record: ExternalPluginRecord) : InstallResult

    data class AlreadyInstalled(val record: ExternalPluginRecord) : InstallResult

    data class NeedsPublisherConfirmation(
        val manifest: PluginManifest,
        val publisher: VerifiedPublisher,
        val digests: PackageDigests,
    ) : InstallResult

    data class Rejected(val code: InstallRejection, val reason: String) : InstallResult
}

internal sealed interface BundleState {
    data class Ready(
        val pluginId: String,
        val version: String,
        val bundleFile: File,
        val bundleSha256: String,
    ) : BundleState

    data class Missing(val reason: String) : BundleState

    data object Absent : BundleState
}

internal sealed interface CommitResult {
    data class Committed(val record: ExternalPluginRecord) : CommitResult

    data class Rejected(val reason: String) : CommitResult
}

internal class PluginInstaller(
    private val pluginsRoot: File,
    private val trustStore: PublisherTrustStore,
    private val recordStore: ExternalPluginRecordStore,
    private val hostApiLevel: Int = PLUGIN_API_LEVEL,
    private val hostVersion: String? = null,
    private val reservedPluginIds: () -> Set<String> = { emptySet() },
    private val reservedToolNames: () -> Set<String> = { emptySet() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun inspect(packageFile: File): InspectionResult = withContext(ioDispatcher) {
        when (val result = readCandidate(packageFile)) {
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
        val candidate = when (val result = readCandidate(packageFile)) {
            is CandidateResult.Rejected -> return@withContext InstallResult.Rejected(result.code, result.reason)
            is CandidateResult.Ok -> result.candidate
        }
        val manifest = candidate.manifest
        val pluginId = manifest.id
        if (pluginId in reservedPluginIds()) {
            return@withContext InstallResult.Rejected(InstallRejection.ID_CONFLICT, "plugin id '$pluginId' is reserved")
        }
        val toolConflicts = manifest.provides.tools.intersect(reservedToolNames())
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
        val phase = if (enabled) {
            if (record.phase == PluginPhase.DISABLED) PluginPhase.INSTALLED else record.phase
        } else {
            PluginPhase.DISABLED
        }
        recordStore.upsert(record.copy(enabled = enabled, phase = phase))
        true
    }

    suspend fun updateSkills(pluginId: String, skillIds: List<String>): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        recordStore.upsert(record.copy(skillIds = skillIds))
        true
    }

    suspend fun markPhase(pluginId: String, phase: PluginPhase, error: String? = null): Boolean =
        withContext(ioDispatcher) {
            val record = recordStore.get(pluginId) ?: return@withContext false
            recordStore.upsert(record.copy(phase = phase, lastError = error))
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
        bundleState(pluginId, record.version, record.installPath, record.activeBundleSha256)
    }

    suspend fun pendingBundle(pluginId: String): BundleState = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext BundleState.Absent
        val pendingVersion = record.pendingVersion ?: return@withContext BundleState.Absent
        val pendingSha256 = record.pendingBundleSha256
            ?: return@withContext BundleState.Missing("pending bundle sha256 is missing")
        bundleState(pluginId, pendingVersion, pendingInstallPath(pluginId, pendingVersion), pendingSha256)
    }

    suspend fun commitPending(pluginId: String): CommitResult = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext CommitResult.Rejected("plugin is not installed")
        val pendingVersion = record.pendingVersion
            ?: return@withContext CommitResult.Rejected("plugin has no pending update")
        val pendingSha256 = record.pendingBundleSha256
            ?: return@withContext CommitResult.Rejected("pending bundle sha256 is missing")
        val pendingPath = pendingInstallPath(pluginId, pendingVersion)
        val pendingBundle = bundleState(pluginId, pendingVersion, pendingPath, pendingSha256)
        if (pendingBundle !is BundleState.Ready) {
            return@withContext CommitResult.Rejected("pending bundle failed verification: ${describe(pendingBundle)}")
        }
        val updated = record.copy(
            version = pendingVersion,
            installPath = pendingPath,
            activeBundleSha256 = pendingSha256,
            pendingVersion = null,
            pendingBundleSha256 = null,
            phase = PluginPhase.INSTALLED,
            lastError = null,
        )
        recordStore.upsert(updated)
        CommitResult.Committed(updated)
    }

    suspend fun discardPending(pluginId: String, error: String): Boolean = withContext(ioDispatcher) {
        val record = recordStore.get(pluginId) ?: return@withContext false
        val pendingVersion = record.pendingVersion ?: return@withContext false
        recordStore.upsert(
            record.copy(
                pendingVersion = null,
                pendingBundleSha256 = null,
                phase = PluginPhase.DEGRADED,
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
            stage(candidate, versionRoot)
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
            source = PluginSource.EXTERNAL,
            installPath = versionRoot.absolutePath,
            activeBundleSha256 = candidate.digests.bundleSha256,
            size = candidate.digests.size,
            publisherKeyId = candidate.publisher.keyId,
            apiLevel = candidate.manifest.apiLevel,
            uiApiLevel = candidate.manifest.ui?.apiLevel ?: HOST_UI_API_LEVEL,
            phase = PluginPhase.INSTALLED,
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
            stage(candidate, versionRoot)
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
            phase = PluginPhase.PENDING_RESTART,
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

    private fun stage(candidate: Candidate, versionRoot: File): File {
        val parent = versionRoot.parentFile ?: throw IllegalStateException("invalid plugin path: ${versionRoot.path}")
        parent.mkdirs()
        val staging = File(parent, ".staging-${versionRoot.name}-${clock()}")
        staging.deleteRecursively()
        var installed = false
        try {
            val bundleDir = File(staging, PluginPaths.BUNDLE_DIR).apply { mkdirs() }
            val assetsDir = File(staging, PluginPaths.ASSETS_DIR).apply { mkdirs() }
            val libDir = File(staging, PluginPaths.LIB_DIR).apply { mkdirs() }
            candidate.pkg.copyBundleTo(File(bundleDir, BUNDLE_FILE))
            candidate.pkg.extractAssets(assetsDir)
            candidate.pkg.extractLib(libDir)
            if (versionRoot.exists()) versionRoot.deleteRecursively()
            if (!staging.renameTo(versionRoot)) {
                staging.copyRecursively(versionRoot, overwrite = true)
                staging.deleteRecursively()
            }
            installed = true
            return versionRoot
        } finally {
            if (!installed) runCatching { staging.deleteRecursively() }
        }
    }

    private fun readCandidate(packageFile: File): CandidateResult {
        val pkg = try {
            DdpPackage.read(packageFile)
        } catch (error: DdpPackageException) {
            return CandidateResult.Rejected(
                InstallRejection.PACKAGE_INVALID,
                error.message ?: "invalid plugin package",
            )
        }
        return when (val verification = PluginPackageVerifier.verify(pkg, hostApiLevel, hostVersion)) {
            is VerificationResult.Rejected -> CandidateResult.Rejected(
                when (verification.code) {
                    VerificationCode.MANIFEST_MALFORMED,
                    VerificationCode.MANIFEST_INVALID,
                    -> InstallRejection.MANIFEST_INVALID

                    else -> InstallRejection.SIGNATURE_INVALID
                },
                verification.reason,
            )

            is VerificationResult.Ok -> CandidateResult.Ok(
                Candidate(pkg, verification.manifest, verification.publisher, verification.digests),
            )
        }
    }

    private fun bundleState(pluginId: String, version: String, installPath: String, expectedSha256: String): BundleState {
        val bundleFile = File(File(installPath, PluginPaths.BUNDLE_DIR), BUNDLE_FILE)
        if (!bundleFile.isFile) return BundleState.Missing("plugin bundle is missing: ${bundleFile.path}")
        val actual = runCatching { DdpSigning.sha256Hex(bundleFile) }.getOrNull()
            ?: return BundleState.Missing("plugin bundle is unreadable: ${bundleFile.path}")
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            return BundleState.Missing("plugin bundle sha256 mismatch: ${bundleFile.path}")
        }
        return BundleState.Ready(pluginId, version, bundleFile, expectedSha256)
    }

    private fun pendingInstallPath(pluginId: String, version: String): String =
        PluginPaths.versionRoot(pluginsRoot, pluginId, version).absolutePath

    private fun describe(state: BundleState): String = when (state) {
        is BundleState.Ready -> "ready"
        is BundleState.Missing -> state.reason
        BundleState.Absent -> "absent"
    }

    private data class Candidate(
        val pkg: DdpPackage,
        val manifest: PluginManifest,
        val publisher: VerifiedPublisher,
        val digests: PackageDigests,
    )

    private sealed interface CandidateResult {
        data class Ok(val candidate: Candidate) : CandidateResult

        data class Rejected(val code: InstallRejection, val reason: String) : CandidateResult
    }

    private companion object {
        const val BUNDLE_FILE = "plugin.dpp"
    }
}
