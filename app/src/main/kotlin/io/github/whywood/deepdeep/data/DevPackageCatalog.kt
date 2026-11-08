package io.github.whywood.deepdeep.data

import android.content.Context
import androidx.annotation.StringRes
import io.github.whywood.deepdeep.R
import java.util.Locale

data class DevPackage(
    val id: String,
    val displayName: String,
    val sizeLabel: String,
    @StringRes val descriptionRes: Int,
    @StringRes val displayNameRes: Int? = null,
    val sizeMb: Int = 1,
)

fun DevPackage.label(context: Context): String =
    displayNameRes?.let(context::getString) ?: displayName

enum class DevArchive { TarGz, TarXz, Zip }

data class DevDownload(
    val id: String,
    @StringRes val titleRes: Int,
    val url: String,
    val sha256: String,
    val archive: DevArchive,
    val targetDir: String,
    val sizeLabel: String,
) {
    val fileName: String get() = url.substringAfterLast('/')

    fun installCommand(): String {
        val dir = DOWNLOAD_DIR
        val file = "$dir/$fileName"
        val extract = when (archive) {
            DevArchive.TarGz -> "mkdir -p $targetDir && tar -xzf $file -C $targetDir --strip-components=1"
            DevArchive.TarXz -> "mkdir -p $targetDir && tar -xJf $file -C $targetDir --strip-components=1"
            DevArchive.Zip -> "mkdir -p $targetDir && unzip -q -o $file -d $targetDir"
        }
        return "mkdir -p $dir && curl -fL --retry 3 -o $file $url && " +
            "echo \"$sha256  $file\" | sha256sum -c - && $extract && rm -f $file"
    }

    companion object {
        private const val DOWNLOAD_DIR = "/tmp/deepdeep-downloads"
    }
}

data class ApkMirror(
    val id: String,
    @StringRes val nameRes: Int,
    val baseUrl: String,
) {
    val repositoryLines: List<String>
        get() = listOf(
            "$baseUrl/${ApkMirrors.BRANCH}/main",
            "$baseUrl/${ApkMirrors.BRANCH}/community",
        )
}

object ApkMirrors {
    const val BRANCH = "v3.24"

    val official = ApkMirror(
        id = "official",
        nameRes = R.string.dev_mirror_official_name,
        baseUrl = "https://dl-cdn.alpinelinux.org/alpine",
    )
    val aliyun = ApkMirror(
        id = "aliyun",
        nameRes = R.string.dev_mirror_aliyun_name,
        baseUrl = "https://mirrors.aliyun.com/alpine",
    )
    val tuna = ApkMirror(
        id = "tuna",
        nameRes = R.string.dev_mirror_tuna_name,
        baseUrl = "https://mirrors.tuna.tsinghua.edu.cn/alpine",
    )
    val tencent = ApkMirror(
        id = "tencent",
        nameRes = R.string.dev_mirror_tencent_name,
        baseUrl = "https://mirrors.cloud.tencent.com/alpine",
    )

    val all = listOf(official, aliyun, tuna, tencent)
    val default = aliyun

    fun byId(id: String): ApkMirror? = all.firstOrNull { it.id == id }

    fun resolve(ids: Collection<String>): List<ApkMirror> = all.filter { it.id in ids }

    fun lines(ids: Collection<String>): List<String> = resolve(ids).flatMap { it.repositoryLines }

    fun defaultIds(): List<String> = listOf(default.id)
}

data class DevBundle(
    val id: String,
    @StringRes val titleRes: Int,
    val packageIds: List<String>,
    val sizeLabel: String,
    val recommended: Boolean = false,
    val postInstallCommand: String? = null,
    @StringRes val bodyRes: Int? = null,
    val downloads: List<DevDownload> = emptyList(),
    val mirrorUrls: List<String> = emptyList(),
)

data class DevBundleCustomization(
    val extras: Map<String, List<String>> = emptyMap(),
    val overlays: Map<String, List<String>> = emptyMap(),
    val mirrors: List<String> = emptyList(),
) {
    fun extrasFor(bundleId: String): List<String> = extras[bundleId].orEmpty()

    fun overlaysFor(bundleId: String): List<String> = overlays[bundleId].orEmpty()

    fun mirrorIds(): List<String> = mirrors.ifEmpty { ApkMirrors.defaultIds() }
}

object DevPackageCatalog {
    val packages = listOf(
        DevPackage("curl", "curl", "~1 MB", R.string.dev_package_curl, sizeMb = 1),
        DevPackage("wget", "wget", "~1 MB", R.string.dev_package_wget, sizeMb = 1),
        DevPackage("ca-certificates", "CA certificates", "<1 MB", R.string.dev_package_ca_certificates, displayNameRes = R.string.dev_package_ca_certificates_name, sizeMb = 1),
        DevPackage("jq", "jq", "<1 MB", R.string.dev_package_jq, sizeMb = 1),
        DevPackage("python3", "Python 3", "~40 MB", R.string.dev_package_python3, sizeMb = 40),
        DevPackage("py3-pip", "pip", "~10 MB", R.string.dev_package_pip, sizeMb = 10),
        DevPackage("nodejs", "Node.js", "~55 MB", R.string.dev_package_nodejs, sizeMb = 55),
        DevPackage("npm", "npm", "~15 MB", R.string.dev_package_npm, sizeMb = 15),
        DevPackage("ripgrep", "ripgrep", "~3 MB", R.string.dev_package_ripgrep, sizeMb = 3),
        DevPackage("fd", "fd", "~2 MB", R.string.dev_package_fd, sizeMb = 2),
        DevPackage("ffmpeg", "FFmpeg", "~100 MB", R.string.dev_package_ffmpeg, sizeMb = 100),
        DevPackage("openssh-client", "OpenSSH", "~3 MB", R.string.dev_package_openssh, sizeMb = 3),
        DevPackage("unzip", "unzip", "<1 MB", R.string.dev_package_unzip, sizeMb = 1),
        DevPackage("zip", "zip", "<1 MB", R.string.dev_package_zip, sizeMb = 1),
        DevPackage("tree", "tree", "<1 MB", R.string.dev_package_tree, sizeMb = 1),
        DevPackage("poppler-utils", "PDF tools", "~20 MB", R.string.dev_package_pdf, displayNameRes = R.string.dev_package_pdf_name, sizeMb = 20),
        DevPackage("qpdf", "qpdf", "~8 MB", R.string.dev_package_qpdf, sizeMb = 8),
        DevPackage("py3-openpyxl", "Excel read/write", "~115 MB", R.string.dev_package_excel, displayNameRes = R.string.dev_package_excel_name, sizeMb = 115),
        DevPackage("py3-xlsxwriter", "Excel generation", "~2 MB", R.string.dev_package_excel_writer, displayNameRes = R.string.dev_package_excel_writer_name, sizeMb = 2),
        DevPackage("py3-pillow", "Image processing", "~10 MB", R.string.dev_package_image, displayNameRes = R.string.dev_package_image_name, sizeMb = 10),
        DevPackage("tesseract-ocr", "OCR", "~35 MB", R.string.dev_package_ocr, sizeMb = 35),
        DevPackage("git", "Git", "~10 MB", R.string.dev_package_git, sizeMb = 10),
        DevPackage("github-cli", "GitHub CLI", "~45 MB", R.string.dev_package_github_cli, sizeMb = 45),
        DevPackage("build-base", "GCC / make", "~235 MB", R.string.dev_package_build_base, sizeMb = 235),
        DevPackage("cmake", "CMake", "~20 MB", R.string.dev_package_cmake, sizeMb = 20),
        DevPackage("make", "make", "~2 MB", R.string.dev_package_make, sizeMb = 2),
        DevPackage("pkgconf", "pkgconf", "<1 MB", R.string.dev_package_pkgconf, sizeMb = 1),
        DevPackage("bash", "Bash", "~6 MB", R.string.dev_package_bash, sizeMb = 6),
        DevPackage("coreutils", "GNU coreutils", "~3 MB", R.string.dev_package_coreutils, sizeMb = 3),
        DevPackage("findutils", "findutils", "<1 MB", R.string.dev_package_findutils, sizeMb = 1),
        DevPackage("procps", "procps", "~1 MB", R.string.dev_package_procps, sizeMb = 1),
        DevPackage("xz", "xz", "~1 MB", R.string.dev_package_xz, sizeMb = 1),
        DevPackage("tar", "tar", "~1 MB", R.string.dev_package_tar, sizeMb = 1),
        DevPackage("gcc", "GCC", "~110 MB", R.string.dev_package_gcc, sizeMb = 110),
        DevPackage("musl-dev", "musl-dev", "~30 MB", R.string.dev_package_musl_dev, sizeMb = 30),
    )

    val buildTools = listOf(
        DevDownload(
            id = "jdk",
            titleRes = R.string.dev_download_jdk,
            url = "https://cdn.azul.com/zulu/bin/zulu21.54.107-ca-jdk21.0.13-linux_musl_aarch64.tar.gz",
            sha256 = "3045ddaef70a32d47eeceb9f375667a1b3d55870b1b6ed0639790dbeb1cd5a52",
            archive = DevArchive.TarGz,
            targetDir = "/opt/jdk",
            sizeLabel = "~203 MB",
        ),
        DevDownload(
            id = "android-sdk",
            titleRes = R.string.dev_download_sdk,
            url = "https://github.com/HomuHomu833/android-sdk-custom/releases/download/36.0.1/android-sdk-aarch64-linux-musl.tar.xz",
            sha256 = "36077c5ecf3048a6d203e1e2f882e8c155ef2cd8ffff7ffdf73ac37d2013f48c",
            archive = DevArchive.TarXz,
            targetDir = "/opt/android-sdk",
            sizeLabel = "~201 MB",
        ),
        DevDownload(
            id = "android-platform",
            titleRes = R.string.dev_download_platform,
            url = "https://dl.google.com/android/repository/platform-36_r02.zip",
            sha256 = "37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e",
            archive = DevArchive.Zip,
            targetDir = "/opt/android-sdk/platforms",
            sizeLabel = "~63 MB",
        ),
        DevDownload(
            id = "gradle",
            titleRes = R.string.dev_download_gradle,
            url = "https://services.gradle.org/distributions/gradle-9.8.2-bin.zip",
            sha256 = "21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607",
            archive = DevArchive.Zip,
            targetDir = "/opt/gradle",
            sizeLabel = "~165 MB",
        ),
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
            bodyRes = R.string.dev_bundle_base_body,
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
            bodyRes = R.string.dev_bundle_office_body,
        ),
        DevBundle(
            id = "dev",
            titleRes = R.string.dev_bundle_dev,
            packageIds = listOf(
                "git", "github-cli", "build-base", "cmake", "make", "pkgconf", "openssh-client",
            ),
            sizeLabel = "~330 MB",
            bodyRes = R.string.dev_bundle_dev_body,
        ),
        DevBundle(
            id = "apkdev",
            titleRes = R.string.dev_bundle_apk_dev,
            packageIds = listOf(
                "bash", "coreutils", "findutils", "procps", "xz", "tar",
                "curl", "ca-certificates", "unzip",
                "gcc", "musl-dev",
            ),
            sizeLabel = "~160 MB",
            bodyRes = R.string.dev_bundle_apk_dev_body,
            downloads = buildTools,
        ),
    )

    private val packagesById = packages.associateBy { it.id }

    fun packageById(id: String): DevPackage? = packagesById[id]

    fun bundleById(id: String): DevBundle? = bundles.firstOrNull { it.id == id }

    fun packageIds(bundle: DevBundle): Set<String> = bundle.packageIds.toSet()

    fun estimatedSizeMb(packageIds: Collection<String>): Int =
        packageIds.sumOf { packagesById[it]?.sizeMb ?: 1 }

    fun estimatedSizeLabel(packageIds: Collection<String>): String {
        val mb = estimatedSizeMb(packageIds)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.1f GB", mb / 1024f)
        } else {
            "~$mb MB"
        }
    }

    fun composeBundle(
        bundle: DevBundle,
        extraPackageIds: Collection<String> = emptyList(),
        overlayBundleIds: Collection<String> = emptyList(),
        mirrorIds: Collection<String> = emptyList(),
    ): DevBundle {
        val overlays = overlayBundleIds
            .distinct()
            .filter { it != bundle.id }
            .mapNotNull(::bundleById)
        val extras = extraPackageIds
            .distinct()
            .filter { packagesById[it] != null }
        val merged = LinkedHashSet<String>()
        merged += bundle.packageIds
        overlays.forEach { merged += it.packageIds }
        merged += extras
        val ordered = packages.map { it.id }.filter { it in merged }
        val downloads = (bundle.downloads + overlays.flatMap { it.downloads }).distinctBy { it.id }
        return bundle.copy(
            packageIds = ordered,
            downloads = downloads,
            mirrorUrls = ApkMirrors.lines(mirrorIds),
        )
    }

    fun mirrorApplyBundle(mirrorIds: Collection<String>): DevBundle = DevBundle(
        id = "mirrors",
        titleRes = R.string.dev_mirror_apply_label,
        packageIds = emptyList(),
        sizeLabel = "",
        mirrorUrls = ApkMirrors.lines(mirrorIds),
    )
}
