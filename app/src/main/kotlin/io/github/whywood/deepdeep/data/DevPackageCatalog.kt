package io.github.whywood.deepdeep.data

import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R

data class DevPackage(
    val id: String,
    val displayName: String,
    val sizeLabel: String,
)

data class DevBundle(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val packageIds: List<String>,
    val sizeLabel: String,
    val recommended: Boolean = false,
)

object DevPackageCatalog {
    val packages = listOf(
        DevPackage("curl", "curl", "~1 MB"),
        DevPackage("wget", "wget", "~1 MB"),
        DevPackage("ca-certificates", "CA 证书", "<1 MB"),
        DevPackage("jq", "jq", "<1 MB"),
        DevPackage("python3", "Python 3", "~40 MB"),
        DevPackage("py3-pip", "pip", "~10 MB"),
        DevPackage("nodejs", "Node.js", "~55 MB"),
        DevPackage("npm", "npm", "~15 MB"),
        DevPackage("git", "Git", "~10 MB"),
        DevPackage("openssh-client", "OpenSSH", "~2 MB"),
        DevPackage("ripgrep", "ripgrep", "~3 MB"),
        DevPackage("fd", "fd", "~2 MB"),
        DevPackage("tree", "tree", "<1 MB"),
        DevPackage("build-base", "编译工具链", "~230 MB"),
        DevPackage("cmake", "CMake", "~20 MB"),
        DevPackage("pkgconf", "pkgconf", "<1 MB"),
    )

    val bundles = listOf(
        DevBundle(
            id = "network",
            titleRes = R.string.dev_bundle_network,
            subtitleRes = R.string.dev_bundle_network_body,
            packageIds = listOf("curl", "wget", "ca-certificates", "jq"),
            sizeLabel = "~3 MB",
        ),
        DevBundle(
            id = "python",
            titleRes = R.string.dev_bundle_python,
            subtitleRes = R.string.dev_bundle_python_body,
            packageIds = listOf("python3", "py3-pip"),
            sizeLabel = "~50 MB",
        ),
        DevBundle(
            id = "node",
            titleRes = R.string.dev_bundle_node,
            subtitleRes = R.string.dev_bundle_node_body,
            packageIds = listOf("nodejs", "npm"),
            sizeLabel = "~70 MB",
        ),
        DevBundle(
            id = "git",
            titleRes = R.string.dev_bundle_git,
            subtitleRes = R.string.dev_bundle_git_body,
            packageIds = listOf("git", "openssh-client"),
            sizeLabel = "~12 MB",
        ),
        DevBundle(
            id = "search",
            titleRes = R.string.dev_bundle_search,
            subtitleRes = R.string.dev_bundle_search_body,
            packageIds = listOf("ripgrep", "fd", "tree"),
            sizeLabel = "~5 MB",
        ),
        DevBundle(
            id = "build",
            titleRes = R.string.dev_bundle_build,
            subtitleRes = R.string.dev_bundle_build_body,
            packageIds = listOf("build-base", "cmake", "pkgconf"),
            sizeLabel = "~250 MB",
        ),
        DevBundle(
            id = "fullstack",
            titleRes = R.string.dev_bundle_fullstack,
            subtitleRes = R.string.dev_bundle_fullstack_body,
            packageIds = listOf(
                "curl", "wget", "ca-certificates", "jq",
                "python3", "py3-pip",
                "nodejs", "npm",
                "git", "openssh-client",
                "ripgrep", "fd", "tree",
            ),
            sizeLabel = "~140 MB",
            recommended = true,
        ),
    )

    private val packagesById = packages.associateBy { it.id }

    fun packageById(id: String): DevPackage? = packagesById[id]

    fun bundleById(id: String): DevBundle? = bundles.firstOrNull { it.id == id }

    fun packageIds(bundle: DevBundle): Set<String> = bundle.packageIds.toSet()
}
