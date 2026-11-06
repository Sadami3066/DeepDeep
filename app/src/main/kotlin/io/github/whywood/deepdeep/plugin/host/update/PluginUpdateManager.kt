package io.github.whywood.deepdeep.plugin.host.update

import io.github.whywood.deepdeep.plugin.host.external.DdpPackage
import io.github.whywood.deepdeep.plugin.host.external.DdpPackageException
import io.github.whywood.deepdeep.plugin.host.external.ExternalPluginRecord
import io.github.whywood.deepdeep.plugin.host.external.InstallRejection
import io.github.whywood.deepdeep.plugin.host.external.InstallResult
import io.github.whywood.deepdeep.plugin.host.external.PluginInstaller
import io.github.whywood.deepdeep.plugin.host.external.PluginPackageVerifier
import io.github.whywood.deepdeep.plugin.host.external.PublisherTrustStore
import io.github.whywood.deepdeep.plugin.host.external.VerificationCode
import io.github.whywood.deepdeep.plugin.host.external.VerificationResult
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface UpdateStageResult {
    data class Staged(
        val record: ExternalPluginRecord,
        val candidate: PluginUpdateCandidate,
    ) : UpdateStageResult

    data class Rejected(val code: UpdateRejection, val reason: String) : UpdateStageResult
}

internal class PluginUpdateManager(
    private val installer: PluginInstaller,
    private val trustStore: PublisherTrustStore,
    private val downloader: PluginPackageDownloader,
    private val policy: UpdatePolicy = UpdatePolicy(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val stageMutex = Mutex()

    suspend fun downloadAndStage(
        candidate: PluginUpdateCandidate,
        onProgress: (Int?) -> Unit = {},
    ): UpdateStageResult = stageMutex.withLock {
        withContext(ioDispatcher) {
            val entry = candidate.entry
            val record = installer.record(entry.id)
                ?: return@withContext rejected(
                    UpdateRejection.UNKNOWN_PLUGIN,
                    "plugin '${entry.id}' is not installed",
                )
            when (val check = PluginUpdateSelector.checkUpdate(record, entry)) {
                is CandidateCheck.Rejected -> return@withContext UpdateStageResult.Rejected(check.code, check.reason)
                CandidateCheck.Ok -> Unit
            }
            when (val acceptable = PluginUpdateSelector.ensureAcceptable(entry, policy)) {
                is CandidateCheck.Rejected ->
                    return@withContext UpdateStageResult.Rejected(acceptable.code, acceptable.reason)

                CandidateCheck.Ok -> Unit
            }
            trustStore.trusted(entry.publisherKeyId)
                ?: return@withContext rejected(
                    UpdateRejection.PUBLISHER_UNTRUSTED,
                    "publisher ${entry.publisherKeyId} is not trusted; install or confirm it first",
                )
            val download = when (val result = downloader.download(entry, onProgress)) {
                is DownloadResult.Rejected -> return@withContext UpdateStageResult.Rejected(result.code, result.reason)
                is DownloadResult.Ready -> result
            }
            try {
                when (val verified = verifyPackage(download.file, entry, download.sha256)) {
                    is CandidateCheck.Rejected ->
                        return@withContext UpdateStageResult.Rejected(verified.code, verified.reason)

                    CandidateCheck.Ok -> Unit
                }
                when (val result = installer.install(download.file, acceptPublisher = false)) {
                    is InstallResult.Staged -> {
                        onProgress(100)
                        UpdateStageResult.Staged(result.record, candidate)
                    }

                    is InstallResult.AlreadyInstalled -> rejected(
                        UpdateRejection.NOT_NEWER,
                        "plugin '${entry.id}' is already at ${entry.version}",
                    )

                    is InstallResult.NeedsPublisherConfirmation -> rejected(
                        UpdateRejection.PUBLISHER_UNTRUSTED,
                        "publisher ${entry.publisherKeyId} requires confirmation",
                    )

                    is InstallResult.Installed -> rejected(
                        UpdateRejection.INSTALL_REJECTED,
                        "plugin '${entry.id}' was not installed before staging",
                    )

                    is InstallResult.Rejected -> UpdateStageResult.Rejected(
                        mapInstallRejection(result.code),
                        result.reason,
                    )
                }
            } finally {
                download.file.delete()
            }
        }
    }

    private fun verifyPackage(file: File, entry: PluginIndexEntry, sha256: String): CandidateCheck {
        val pkg = try {
            DdpPackage.read(file, maxBytes = policy.maxPackageBytes)
        } catch (error: DdpPackageException) {
            return CandidateCheck.Rejected(
                UpdateRejection.PACKAGE_INVALID,
                error.message ?: "invalid plugin package",
            )
        } catch (error: Exception) {
            return CandidateCheck.Rejected(
                UpdateRejection.PACKAGE_INVALID,
                error.message ?: error.javaClass.simpleName,
            )
        }
        val accepted = when (val verification = PluginPackageVerifier.verify(pkg, policy.hostApiLevel, policy.hostVersion)) {
            is VerificationResult.Rejected -> return CandidateCheck.Rejected(
                mapVerificationRejection(verification.code),
                verification.reason,
            )

            is VerificationResult.Ok -> verification
        }
        val manifest = accepted.manifest
        if (manifest.id != entry.id) {
            return CandidateCheck.Rejected(
                UpdateRejection.ID_MISMATCH,
                "package manifest id ${manifest.id} does not match index entry ${entry.id}",
            )
        }
        if (manifest.version != entry.version) {
            return CandidateCheck.Rejected(
                UpdateRejection.VERSION_MISMATCH,
                "package version ${manifest.version} does not match index entry ${entry.version}",
            )
        }
        if (!accepted.publisher.keyId.equals(entry.publisherKeyId, ignoreCase = true)) {
            return CandidateCheck.Rejected(
                UpdateRejection.PUBLISHER_MISMATCH,
                "package is signed by ${accepted.publisher.keyId}, index entry declares ${entry.publisherKeyId}",
            )
        }
        if (!accepted.digests.bundleSha256.equals(sha256, ignoreCase = true)) {
            return CandidateCheck.Rejected(
                UpdateRejection.SHA256_MISMATCH,
                "verified package sha256 does not match the downloaded file",
            )
        }
        return CandidateCheck.Ok
    }

    private fun mapVerificationRejection(code: VerificationCode): UpdateRejection = when (code) {
        VerificationCode.MANIFEST_MALFORMED,
        VerificationCode.MANIFEST_INVALID,
        -> UpdateRejection.MANIFEST_INVALID

        else -> UpdateRejection.SIGNATURE_INVALID
    }

    private fun mapInstallRejection(code: InstallRejection): UpdateRejection = when (code) {
        InstallRejection.PACKAGE_INVALID -> UpdateRejection.PACKAGE_INVALID
        InstallRejection.SIGNATURE_INVALID -> UpdateRejection.SIGNATURE_INVALID
        InstallRejection.MANIFEST_INVALID -> UpdateRejection.MANIFEST_INVALID
        InstallRejection.PUBLISHER_CONFLICT -> UpdateRejection.PUBLISHER_MISMATCH
        InstallRejection.VERSION_CONFLICT -> UpdateRejection.NOT_NEWER
        InstallRejection.PENDING_CONFLICT -> UpdateRejection.PENDING_EXISTS
        InstallRejection.STORAGE_ERROR -> UpdateRejection.STORAGE_ERROR
        InstallRejection.TOOL_CONFLICT,
        -> UpdateRejection.INSTALL_REJECTED
    }

    private fun rejected(code: UpdateRejection, reason: String): UpdateStageResult.Rejected =
        UpdateStageResult.Rejected(code, reason)
}
