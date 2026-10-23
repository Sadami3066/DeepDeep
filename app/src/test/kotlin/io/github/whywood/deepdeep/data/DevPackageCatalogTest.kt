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
        assertEquals(7, DevPackageCatalog.bundles.size)
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
    fun fullstackCoversTheNonBuildBundles() {
        val fullstack = DevPackageCatalog.bundleById("fullstack")!!
        val expected = DevPackageCatalog.bundles
            .filter { it.id != "build" && it.id != "fullstack" }
            .flatMap { it.packageIds }
            .toSet()
        assertTrue(fullstack.packageIds.containsAll(expected))
        assertTrue("fullstack must not include the heavy build toolchain", "build-base" !in fullstack.packageIds)
    }
}
