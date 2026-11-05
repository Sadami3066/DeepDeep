package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginPaths
import java.io.File

internal data class Candidate(
    val pkg: DdpPackage,
    val manifest: PluginManifest,
    val publisher: VerifiedPublisher,
    val digests: PackageDigests,
)

internal sealed interface CandidateResult {
    data class Ok(val candidate: Candidate) : CandidateResult

    data class Rejected(val code: InstallRejection, val reason: String) : CandidateResult
}

internal class PluginPackageStager(
    private val pluginsRoot: File,
    private val hostApiLevel: Int = PLUGIN_API_LEVEL,
    private val hostVersion: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun readCandidate(packageFile: File): CandidateResult {
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

    fun stage(candidate: Candidate, versionRoot: File): File {
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

    fun bundleState(pluginId: String, version: String, installPath: String, expectedSha256: String): BundleState {
        val bundleFile = File(File(installPath, PluginPaths.BUNDLE_DIR), BUNDLE_FILE)
        if (!bundleFile.isFile) return BundleState.Missing("plugin bundle is missing: ${bundleFile.path}")
        val actual = runCatching { DdpSigning.sha256Hex(bundleFile) }.getOrNull()
            ?: return BundleState.Missing("plugin bundle is unreadable: ${bundleFile.path}")
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            return BundleState.Missing("plugin bundle sha256 mismatch: ${bundleFile.path}")
        }
        return BundleState.Ready(pluginId, version, bundleFile, expectedSha256)
    }

    fun pendingInstallPath(pluginId: String, version: String): String =
        PluginPaths.versionRoot(pluginsRoot, pluginId, version).absolutePath

    fun pruneAbandonedVersion(pluginId: String, path: String?, keep: Set<String>) {
        if (path == null || path in keep) return
        val root = PluginPaths.pluginRoot(pluginsRoot, pluginId).absolutePath
        if (!path.startsWith("$root/")) return
        runCatching { File(path).deleteRecursively() }
    }

    fun describe(state: BundleState): String = when (state) {
        is BundleState.Ready -> "ready"
        is BundleState.Missing -> state.reason
        BundleState.Absent -> "absent"
    }

    companion object {
        const val BUNDLE_FILE: String = "plugin.dpp"
    }
}
