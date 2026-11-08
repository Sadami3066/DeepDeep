package io.github.whywood.deepdeep.ui.onboarding

import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevPackageCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingEnvironmentTest {
    @Test
    fun mergeKeepsCatalogPackageOrderAndDropsDuplicates() {
        val bundles = listOf(bundle("apkdev"), bundle("base"), bundle("office"))
        val merged = OnboardingEnvironmentInstaller.merge(bundles)
        val selected = bundles.flatMap { it.packageIds }.toSet()
        val expected = DevPackageCatalog.packages.map { it.id }.filter { it in selected }
        assertEquals(expected, merged.packageIds)
        assertEquals(merged.packageIds.distinct(), merged.packageIds)
    }

    @Test
    fun mergeCarriesPostInstallCommandAndDownloads() {
        val merged = OnboardingEnvironmentInstaller.merge(listOf(bundle("office"), bundle("apkdev")))
        assertEquals(bundle("office").postInstallCommand, merged.postInstallCommand)
        assertEquals(bundle("apkdev").downloads.map { it.id }, merged.downloads.map { it.id })
    }

    @Test
    fun mergeUsesEstimatedSizeForTheCombinedSelection() {
        val merged = OnboardingEnvironmentInstaller.merge(listOf(bundle("base"), bundle("office")))
        assertEquals(DevPackageCatalog.estimatedSizeLabel(merged.packageIds), merged.sizeLabel)
        assertEquals("onboarding_selection", merged.id)
    }

    private fun bundle(id: String): DevBundle =
        requireNotNull(DevPackageCatalog.bundleById(id)) { "missing bundle $id" }
}
