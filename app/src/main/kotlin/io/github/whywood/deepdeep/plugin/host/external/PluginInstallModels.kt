package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.PluginManifest
import java.io.File

internal enum class InstallRejection {
    PACKAGE_INVALID,
    SIGNATURE_INVALID,
    MANIFEST_INVALID,
    API_LEVEL_UNSUPPORTED,
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

    data class Rejected(
        val code: InstallRejection,
        val reason: String,
        val declaredApiLevel: Int? = null,
    ) : InspectionResult
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

    data class Rejected(
        val code: InstallRejection,
        val reason: String,
        val declaredApiLevel: Int? = null,
    ) : InstallResult
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
