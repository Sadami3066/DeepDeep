package io.github.whywood.deepdeep.plugin.host.services

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppInfoPluginServiceTest {
    @Test
    fun exposesInjectedMetadata() = runTest {
        val service = AppInfoPluginService(
            versionName = "1.2.3",
            versionCode = 7L,
            language = { "en" },
            sdkInt = { 34 },
            deviceModel = { "Pixel" },
            installationId = { "install-1" },
            scope = this,
        )
        advanceUntilIdle()

        val info = service.info
        assertEquals("1.2.3", info.versionName)
        assertEquals(7L, info.versionCode)
        assertEquals("en", info.language)
        assertEquals(34, info.androidSdkInt)
        assertEquals("Pixel", info.deviceModel)
        assertEquals("install-1", info.installationId)
    }

    @Test
    fun installationIdFallsBackToEmptyOnFailure() = runTest {
        val service = AppInfoPluginService(
            versionName = "1.0.0",
            versionCode = 1L,
            language = { "zh" },
            sdkInt = { 29 },
            deviceModel = { "device" },
            installationId = { error("boom") },
            scope = this,
        )
        advanceUntilIdle()

        assertEquals("", service.info.installationId)
    }
}
