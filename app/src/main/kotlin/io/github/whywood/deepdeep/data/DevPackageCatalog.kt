package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R

data class DevPackage(
    val id: String,
    val displayName: String,
    val sizeLabel: String,
    @StringRes val descriptionRes: Int,
    @StringRes val displayNameRes: Int? = null,
)

fun DevPackage.label(context: Context): String =
    displayNameRes?.let(context::getString) ?: displayName

data class DevBundle(
    val id: String,
    @StringRes val titleRes: Int,
    val packageIds: List<String>,
    val sizeLabel: String,
    val recommended: Boolean = false,
    val postInstallCommand: String? = null,
)

object DevPackageCatalog {
    val packages = listOf(
        DevPackage("curl", "curl", "~1 MB", R.string.dev_package_curl),
        DevPackage("wget", "wget", "~1 MB", R.string.dev_package_wget),
        DevPackage("ca-certificates", "CA certificates", "<1 MB", R.string.dev_package_ca_certificates, displayNameRes = R.string.dev_package_ca_certificates_name),
        DevPackage("jq", "jq", "<1 MB", R.string.dev_package_jq),
        DevPackage("python3", "Python 3", "~40 MB", R.string.dev_package_python3),
        DevPackage("py3-pip", "pip", "~10 MB", R.string.dev_package_pip),
        DevPackage("nodejs", "Node.js", "~55 MB", R.string.dev_package_nodejs),
        DevPackage("npm", "npm", "~15 MB", R.string.dev_package_npm),
        DevPackage("ripgrep", "ripgrep", "~3 MB", R.string.dev_package_ripgrep),
        DevPackage("fd", "fd", "~2 MB", R.string.dev_package_fd),
        DevPackage("ffmpeg", "FFmpeg", "~100 MB", R.string.dev_package_ffmpeg),
        DevPackage("openssh-client", "OpenSSH", "~3 MB", R.string.dev_package_openssh),
        DevPackage("unzip", "unzip", "<1 MB", R.string.dev_package_unzip),
        DevPackage("zip", "zip", "<1 MB", R.string.dev_package_zip),
        DevPackage("tree", "tree", "<1 MB", R.string.dev_package_tree),
        DevPackage("poppler-utils", "PDF tools", "~20 MB", R.string.dev_package_pdf, displayNameRes = R.string.dev_package_pdf_name),
        DevPackage("qpdf", "qpdf", "~8 MB", R.string.dev_package_qpdf),
        DevPackage("py3-openpyxl", "Excel read/write", "~115 MB", R.string.dev_package_excel, displayNameRes = R.string.dev_package_excel_name),
        DevPackage("py3-xlsxwriter", "Excel generation", "~2 MB", R.string.dev_package_excel_writer, displayNameRes = R.string.dev_package_excel_writer_name),
        DevPackage("py3-pillow", "Image processing", "~10 MB", R.string.dev_package_image, displayNameRes = R.string.dev_package_image_name),
        DevPackage("tesseract-ocr", "OCR", "~35 MB", R.string.dev_package_ocr),
        DevPackage("git", "Git", "~10 MB", R.string.dev_package_git),
        DevPackage("github-cli", "GitHub CLI", "~45 MB", R.string.dev_package_github_cli),
        DevPackage("build-base", "GCC / make", "~235 MB", R.string.dev_package_build_base),
        DevPackage("cmake", "CMake", "~20 MB", R.string.dev_package_cmake),
        DevPackage("make", "make", "~2 MB", R.string.dev_package_make),
        DevPackage("pkgconf", "pkgconf", "<1 MB", R.string.dev_package_pkgconf),
    )

    val bundles = listOf(
        DevBundle(
            id = "base",
            titleRes = R.string.dev_bundle_base,
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
