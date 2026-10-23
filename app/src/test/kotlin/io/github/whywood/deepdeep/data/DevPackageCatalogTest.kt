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
    fun bundlesReferenceKnownPackages() {
        assertEquals(3, DevPackageCatalog.bundles.size)
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
}
