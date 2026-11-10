package io.github.whywood.deepdeep.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPackageIntakeTest {
    @Test
    fun detectsDdpSuffixIgnoringCase() {
        assertTrue(PluginPackageIntake.isPluginPackage("wechat-0.2.0.ddp", null))
        assertTrue(PluginPackageIntake.isPluginPackage("WECHAT.DDP", "application/octet-stream"))
        assertTrue(PluginPackageIntake.isPluginPackage(null, PluginPackageIntake.MIME))
    }

    @Test
    fun rejectsOtherFiles() {
        assertFalse(PluginPackageIntake.isPluginPackage("photo.jpg", "image/jpeg"))
        assertFalse(PluginPackageIntake.isPluginPackage("ddp", "application/octet-stream"))
        assertFalse(PluginPackageIntake.isPluginPackage(null, null))
    }
}
