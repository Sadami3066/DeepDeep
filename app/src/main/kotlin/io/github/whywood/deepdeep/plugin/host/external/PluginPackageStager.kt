package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.host.PluginPaths
import io.github.whywood.deepdeep.theme.ThemePackCodec
import io.github.whywood.deepdeep.theme.ThemePackIssue
import io.github.whywood.deepdeep.theme.ThemePackResult
import java.io.File

internal data class Candidate(
    val pkg: DdpPackage,
    val manifest: PluginManifest,
    val publisher: VerifiedPublisher,
    val digests: PackageDigests,
)

internal sealed interface CandidateResult {
    data class Ok(val candidate: Candidate) : CandidateResult

    data class Rejected(
        val code: InstallRejection,
        val reason: String,
        val declaredApiLevel: Int? = null,
    ) : CandidateResult
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
                    VerificationCode.API_LEVEL_UNSUPPORTED -> InstallRejection.API_LEVEL_UNSUPPORTED

                    VerificationCode.MANIFEST_MALFORMED,
                    VerificationCode.MANIFEST_INVALID,
                    -> InstallRejection.MANIFEST_INVALID

                    else -> InstallRejection.SIGNATURE_INVALID
                },
                verification.reason,
                verification.declaredApiLevel,
            )

            is VerificationResult.Ok -> {
                validateThemes(pkg, verification.manifest)?.let { return it }
                CandidateResult.Ok(
                    Candidate(pkg, verification.manifest, verification.publisher, verification.digests),
                )
            }
        }
    }

    private fun validateThemes(pkg: DdpPackage, manifest: PluginManifest): CandidateResult.Rejected? {
        val declared = manifest.provides.themes
        val entry = pkg.entries.firstOrNull { it.path == ThemePackCodec.ENTRY }
        if (entry == null) {
            if (declared.isEmpty()) return null
            return themesRejected(
                "plugin.json declares themes ${declared.joinToString()} but ${ThemePackCodec.ENTRY} is missing",
            )
        }
        if (declared.isEmpty()) {
            return themesRejected("${ThemePackCodec.ENTRY} is present but provides.themes is empty")
        }
        if (entry.size > ThemePackCodec.MAX_BYTES) {
            return themesRejected(
                "${ThemePackCodec.ENTRY} is ${entry.size} bytes, limit is ${ThemePackCodec.MAX_BYTES} bytes",
            )
        }
        val bytes = try {
            pkg.readEntryBytes(ThemePackCodec.ENTRY)
        } catch (error: DdpPackageException) {
            return themesRejected(error.message ?: "failed to read ${ThemePackCodec.ENTRY}")
        }
        val pack = when (val result = ThemePackCodec.decode(bytes)) {
            is ThemePackResult.Invalid -> return themesRejected(describe(result.issues))
            is ThemePackResult.Valid -> result.pack
        }
        val defined = pack.themes.map { it.id }.toSet()
        val declaredSet = declared.toSet()
        val missing = declaredSet - defined
        if (missing.isNotEmpty()) {
            return themesRejected("${ThemePackCodec.ENTRY} is missing declared themes: ${missing.sorted().joinToString()}")
        }
        val undeclared = defined - declaredSet
        if (undeclared.isNotEmpty()) {
            return themesRejected(
                "${ThemePackCodec.ENTRY} defines themes not declared in provides.themes: ${undeclared.sorted().joinToString()}",
            )
        }
        return null
    }

    private fun themesRejected(reason: String): CandidateResult.Rejected =
        CandidateResult.Rejected(InstallRejection.THEMES_INVALID, "invalid plugin themes: $reason")

    private fun describe(issues: List<ThemePackIssue>): String = buildString {
        append(issues.take(4).joinToString("; ") { issue ->
            if (issue.path.isEmpty()) issue.message else "${issue.path}: ${issue.message}"
        })
        if (issues.size > 4) append(" (+${issues.size - 4} more)")
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
