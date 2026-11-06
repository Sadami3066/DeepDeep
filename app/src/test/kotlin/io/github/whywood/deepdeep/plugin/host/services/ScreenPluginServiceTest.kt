package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.screen.GuiScrollDirection
import io.github.whywood.deepdeep.screen.GuiSelector
import io.github.whywood.deepdeep.screen.GuiSnapshot
import io.github.whywood.deepdeep.screen.ScreenCapture
import io.github.whywood.deepdeep.screen.ScreenController
import io.github.whywood.deepdeep.screen.ScreenFindResult
import io.github.whywood.deepdeep.screen.ScreenLaunchReport
import io.github.whywood.deepdeep.screen.ScreenObserveResult
import io.github.whywood.deepdeep.screen.ScreenStatusSnapshot
import io.github.whywood.deepdeep.screen.ScreenTextResult
import io.github.whywood.deepdeep.screen.ScreenWaitOutcome
import io.github.whywood.deepdeep.screen.ScreenApp as ScreenAppEntry
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenPluginServiceTest {
    @Test
    fun statusFollowsBackendChangesWithoutServiceCalls() = runTest {
        val controller = FakeScreenController(offlineSnapshot())
        val service = ScreenPluginService(
            virtual = controller,
            main = null,
            virtualScreen = null,
            screenshotDir = null,
            scope = backgroundScope,
            refreshIntervalMs = 1_000L,
        )
        runCurrent()
        assertFalse(service.status.virtual.available)
        controller.snapshot = onlineSnapshot()
        runCurrent()
        assertFalse(service.status.virtual.available)

        advanceTimeBy(1_001)
        runCurrent()

        assertTrue(service.status.virtual.available)
        assertTrue(controller.statusCalls.get() >= 2)
    }

    @Test
    fun backendPollingStartsOnFirstStatusRead() = runTest {
        val controller = FakeScreenController(offlineSnapshot())
        ScreenPluginService(
            virtual = controller,
            main = null,
            virtualScreen = null,
            screenshotDir = null,
            scope = backgroundScope,
            refreshIntervalMs = 1_000L,
        )
        runCurrent()
        val afterInit = controller.statusCalls.get()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(afterInit, controller.statusCalls.get())
    }

    private class FakeScreenController(
        var snapshot: ScreenStatusSnapshot,
    ) : ScreenController {
        val statusCalls = AtomicInteger()

        override suspend fun status(): ScreenStatusSnapshot {
            statusCalls.incrementAndGet()
            return snapshot
        }

        override suspend fun observe(maxDepth: Int, maxNodes: Int, expectedPackage: String?): ScreenObserveResult =
            error("unused")

        override suspend fun find(selectors: List<GuiSelector>, limit: Int): ScreenFindResult = error("unused")

        override suspend fun tap(selector: GuiSelector, durationMs: Int): String = error("unused")

        override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String = error("unused")

        override suspend fun scroll(direction: GuiScrollDirection, amount: Int, selector: GuiSelector?): String =
            error("unused")

        override suspend fun typeText(selector: GuiSelector?, text: String): ScreenTextResult = error("unused")

        override suspend fun key(name: String): String = error("unused")

        override suspend fun waitFor(
            packageName: String?,
            text: String?,
            textGone: String?,
            timeoutMs: Long,
        ): ScreenWaitOutcome = error("unused")

        override suspend fun screenshot(): ScreenCapture = error("unused")

        override suspend fun launch(packageName: String): ScreenLaunchReport = error("unused")

        override suspend fun listApps(): List<ScreenAppEntry> = error("unused")
    }

    private fun offlineSnapshot(): ScreenStatusSnapshot = ScreenStatusSnapshot(
        accessibilityConnected = false,
        accessibilityEnabled = false,
        virtualState = "offline",
    )

    private fun onlineSnapshot(): ScreenStatusSnapshot = ScreenStatusSnapshot(
        accessibilityConnected = true,
        accessibilityEnabled = true,
        virtualState = "online",
        virtual = io.github.whywood.deepdeep.screen.ScreenDisplayInfo(7, 1080, 1920, "helper"),
    )
}
