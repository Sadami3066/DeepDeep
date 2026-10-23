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
    val postInstallCommand: String? = null,
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
        DevPackage("ripgrep", "ripgrep", "~3 MB"),
        DevPackage("fd", "fd", "~2 MB"),
        DevPackage("ffmpeg", "FFmpeg", "~100 MB"),
        DevPackage("openssh-client", "OpenSSH", "~3 MB"),
        DevPackage("unzip", "unzip", "<1 MB"),
        DevPackage("zip", "zip", "<1 MB"),
        DevPackage("tree", "tree", "<1 MB"),
        DevPackage("poppler-utils", "PDF 工具", "~20 MB"),
        DevPackage("qpdf", "qpdf", "~8 MB"),
        DevPackage("py3-openpyxl", "Excel 读写", "~115 MB"),
        DevPackage("py3-xlsxwriter", "Excel 生成", "~2 MB"),
        DevPackage("py3-pillow", "图像处理", "~10 MB"),
        DevPackage("tesseract-ocr", "OCR", "~35 MB"),
        DevPackage("git", "Git", "~10 MB"),
        DevPackage("github-cli", "GitHub CLI", "~45 MB"),
        DevPackage("build-base", "GCC / make", "~235 MB"),
        DevPackage("cmake", "CMake", "~20 MB"),
        DevPackage("make", "make", "~2 MB"),
        DevPackage("pkgconf", "pkgconf", "<1 MB"),
    )

    val bundles = listOf(
        DevBundle(
            id = "base",
            titleRes = R.string.dev_bundle_base,
            subtitleRes = R.string.dev_bundle_base_body,
            packageIds = listOf(
                "curl", "wget", "ca-certificates", "jq",
                "python3", "py3-pip",
                "nodejs", "npm",
                "ripgrep", "fd", "ffmpeg", "openssh-client",
                "unzip", "zip", "tree",
            ),
            sizeLabel = "~180 MB",
            recommended = true,
        ),
        DevBundle(
            id = "office",
            titleRes = R.string.dev_bundle_office,
            subtitleRes = R.string.dev_bundle_office_body,
            packageIds = listOf(
                "poppler-utils", "qpdf",
                "python3", "py3-pip",
                "py3-openpyxl", "py3-xlsxwriter", "py3-pillow", "tesseract-ocr",
            ),
            sizeLabel = "~160 MB",
            postInstallCommand = "pip install --no-cache-dir --break-system-packages python-docx python-pptx pdfplumber",
        ),
        DevBundle(
            id = "dev",
            titleRes = R.string.dev_bundle_dev,
            subtitleRes = R.string.dev_bundle_dev_body,
            packageIds = listOf(
                "git", "github-cli", "build-base", "cmake", "make", "pkgconf", "openssh-client",
            ),
            sizeLabel = "~330 MB",
        ),
    )

    private val packagesById = packages.associateBy { it.id }

    fun packageById(id: String): DevPackage? = packagesById[id]

    fun bundleById(id: String): DevBundle? = bundles.firstOrNull { it.id == id }

    fun packageIds(bundle: DevBundle): Set<String> = bundle.packageIds.toSet()
}
