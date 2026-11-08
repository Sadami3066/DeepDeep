package io.github.whywood.deepdeep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DevPackageCatalogTest {
    @Test
    fun packageIdsAreUnique() {
        val ids = DevPackageCatalog.packages.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun everyPackageExplainsItsPurpose() {
        DevPackageCatalog.packages.forEach { packageInfo ->
            assertTrue("package ${packageInfo.id} has no description", packageInfo.descriptionRes != 0)
        }
    }

    @Test
    fun bundlesReferenceKnownPackages() {
        assertEquals(4, DevPackageCatalog.bundles.size)
        val known = DevPackageCatalog.packages.map { it.id }.toSet()
        DevPackageCatalog.bundles.forEach { bundle ->
            assertTrue("bundle ${bundle.id} is empty", bundle.packageIds.isNotEmpty())
            assertTrue(
                "bundle ${bundle.id} references unknown package",
                known.containsAll(bundle.packageIds),
            )
            assertEquals(bundle.packageIds.size, bundle.packageIds.distinct().size)
        }
    }

    @Test
    fun baseBundleCoversRuntimeBasics() {
        val base = DevPackageCatalog.bundleById("base")!!
        assertTrue(
            base.packageIds.containsAll(
                listOf(
                    "curl", "wget", "python3", "py3-pip", "nodejs", "npm",
                    "ripgrep", "fd", "ffmpeg", "openssh-client",
                ),
            ),
        )
    }

    @Test
    fun officeBundleCoversDocumentsAndExcel() {
        val office = DevPackageCatalog.bundleById("office")!!
        assertTrue(office.packageIds.containsAll(listOf("poppler-utils", "py3-openpyxl", "tesseract-ocr")))
        val command = office.postInstallCommand.orEmpty()
        assertTrue(command.isNotBlank())
        assertTrue(command.contains("python-docx"))
        assertTrue(command.contains("python-pptx"))
    }

    @Test
    fun devBundleCoversBuildAndGitHub() {
        val dev = DevPackageCatalog.bundleById("dev")!!
        assertTrue(dev.packageIds.containsAll(listOf("git", "github-cli", "build-base", "make")))
    }

    @Test
    fun apkDevBundleCoversBuildTools() {
        val apkDev = DevPackageCatalog.bundleById("apkdev")!!
        assertTrue(
            apkDev.packageIds.containsAll(
                listOf("bash", "coreutils", "findutils", "procps", "xz", "tar", "gcc", "musl-dev"),
            ),
        )
        assertEquals(4, apkDev.downloads.size)
        apkDev.downloads.forEach { download ->
            assertTrue("download ${download.id} url", download.url.startsWith("https://"))
            assertEquals(64, download.sha256.length)
            assertTrue(download.installCommand().contains("sha256sum -c -"))
        }
    }

    @Test
    fun composeBundleMergesExtrasOverlaysAndDownloads() {
        val base = DevPackageCatalog.bundleById("base")!!
        val composed = DevPackageCatalog.composeBundle(
            base,
            extraPackageIds = listOf("tesseract-ocr", "not-a-package", "tesseract-ocr"),
            overlayBundleIds = listOf("dev", "base"),
        )
        val catalogOrder = DevPackageCatalog.packages.map { it.id }
        assertEquals(composed.packageIds.sortedBy(catalogOrder::indexOf), composed.packageIds)
        assertTrue(composed.packageIds.contains("tesseract-ocr"))
        assertTrue(composed.packageIds.containsAll(listOf("git", "github-cli")))
        assertTrue(composed.packageIds.none { it == "not-a-package" })
        assertEquals(composed.packageIds.size, composed.packageIds.distinct().size)
        assertTrue(composed.downloads.isEmpty())
    }

    @Test
    fun composeApkDevBundleKeepsBuildToolsAndMirrors() {
        val apkDev = DevPackageCatalog.bundleById("apkdev")!!
        val composed = DevPackageCatalog.composeBundle(
            apkDev,
            extraPackageIds = listOf("git"),
            mirrorIds = listOf("tuna", "official"),
        )
        assertEquals(4, composed.downloads.size)
        assertTrue(composed.packageIds.contains("git"))
        assertEquals(
            listOf(
                "https://dl-cdn.alpinelinux.org/alpine/v3.24/main",
                "https://dl-cdn.alpinelinux.org/alpine/v3.24/community",
                "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.24/main",
                "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.24/community",
            ),
            composed.mirrorUrls,
        )
    }

    @Test
    fun apkMirrorsCoverDefaultAndAllSources() {
        assertEquals(
            listOf("official", "aliyun", "tuna", "tencent"),
            ApkMirrors.all.map { it.id },
        )
        assertEquals(listOf("aliyun"), ApkMirrors.defaultIds())
        assertEquals(2, ApkMirrors.lines(listOf("aliyun")).size)
        assertEquals(8, ApkMirrors.lines(ApkMirrors.all.map { it.id }).size)
        assertTrue(ApkMirrors.lines(listOf("nope")).isEmpty())
    }

    @Test
    fun estimatedSizeLabelSumsCatalogSizes() {
        assertEquals("~3 MB", DevPackageCatalog.estimatedSizeLabel(listOf("curl", "wget", "jq")))
        assertEquals(
            "~140 MB",
            DevPackageCatalog.estimatedSizeLabel(listOf("gcc", "musl-dev")),
        )
    }
}
