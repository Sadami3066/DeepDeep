package io.github.whywood.deepdeep.host

import io.github.whywood.deepdeep.adb.AdbBridgeState
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostAccessFallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppHostAccessControllerTest {
    @Test
    fun localSelectionNeverPromotesToAdbOrRoot() = runTest {
        val fixture = Fixture(backgroundScope)
        fixture.adbState.value = AdbBridgeState(daemon = true)
        fixture.rootReady = true

        fixture.controller.refresh()
        runCurrent()

        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)
        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.selected)
    }

    @Test
    fun selectedRootFallsBackToAdbThenLocal() = runTest {
        val fixture = Fixture(backgroundScope, initial = HostAccess.ROOT, adbReady = true)
        runCurrent()

        assertEquals(HostAccess.ADB, fixture.controller.state.value.effective)
        assertEquals(HostAccessFallback.ROOT_UNAVAILABLE, fixture.controller.state.value.fallback)

        fixture.adbState.value = AdbBridgeState(daemon = false)
        runCurrent()

        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)
        assertEquals(HostAccessFallback.ROOT_UNAVAILABLE, fixture.controller.state.value.fallback)
    }

    @Test
    fun adbSelectionRequiresRunningDaemon() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.select(HostAccess.ADB)
        runCurrent()

        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)
        assertEquals(HostAccessFallback.ADB_UNAVAILABLE, fixture.controller.state.value.fallback)

        fixture.adbState.value = AdbBridgeState(daemon = true)
        runCurrent()

        assertEquals(HostAccess.ADB, fixture.controller.state.value.effective)
        assertNull(fixture.controller.state.value.fallback)
    }

    @Test
    fun rootSelectionNeedsReadyProbe() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.select(HostAccess.ROOT)
        runCurrent()

        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)
        assertEquals(HostAccessFallback.ROOT_UNAVAILABLE, fixture.controller.state.value.fallback)

        fixture.rootReady = true
        fixture.controller.refresh()
        runCurrent()

        assertEquals(HostAccess.ROOT, fixture.controller.state.value.effective)
        assertTrue(fixture.controller.state.value.rootReady)
    }

    @Test
    fun notificationsOnlyFireWhenEffectiveAccessOrSelectionChanges() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.refresh()
        assertEquals(0, fixture.notifications.size)

        fixture.adbState.value = AdbBridgeState(daemon = true)
        fixture.controller.select(HostAccess.ADB)
        runCurrent()
        assertEquals(HostAccess.ADB, fixture.controller.state.value.effective)
        assertEquals(1, fixture.notifications.size)

        fixture.controller.refresh()
        runCurrent()
        assertEquals(1, fixture.notifications.size)

        fixture.controller.select(HostAccess.ROOT)
        runCurrent()
        assertEquals(HostAccess.ADB, fixture.controller.state.value.effective)
        assertEquals(2, fixture.notifications.size)

        fixture.rootReady = true
        fixture.controller.refresh()
        runCurrent()
        assertEquals(HostAccess.ROOT, fixture.controller.state.value.effective)
        assertEquals(3, fixture.notifications.size)

        fixture.controller.select(HostAccess.LOCAL)
        runCurrent()
        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)
        assertEquals(4, fixture.notifications.size)
    }

    @Test
    fun adbConnectionChangesNotifyWhenEffectiveAccessChanges() = runTest {
        val fixture = Fixture(backgroundScope, initial = HostAccess.ADB)
        runCurrent()
        val baseline = fixture.notifications.size
        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)

        fixture.adbState.value = AdbBridgeState(daemon = true)
        runCurrent()
        assertEquals(HostAccess.ADB, fixture.controller.state.value.effective)
        assertEquals(baseline + 1, fixture.notifications.size)

        fixture.adbState.value = AdbBridgeState(daemon = false)
        runCurrent()
        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)
        assertEquals(baseline + 2, fixture.notifications.size)
    }

    @Test
    fun refreshOnlyProbesRootWhenRootIsSelected() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.refresh()
        fixture.controller.select(HostAccess.ADB)
        runCurrent()
        assertEquals(0, fixture.rootProbeCalls)

        fixture.controller.select(HostAccess.ROOT)
        runCurrent()
        assertEquals(1, fixture.rootProbeCalls)
    }

    @Test
    fun failedRootProbeFallsBackToUnavailable() = runTest {
        val fixture = Fixture(backgroundScope, initial = HostAccess.ROOT)
        fixture.rootReady = true
        fixture.controller.refresh()
        runCurrent()
        assertEquals(HostAccess.ROOT, fixture.controller.state.value.effective)

        fixture.rootProbe = { error("probe failed") }
        fixture.rootReady = false
        fixture.controller.refresh()
        runCurrent()

        assertEquals(HostAccess.LOCAL, fixture.controller.state.value.effective)
        assertFalse(fixture.controller.state.value.rootReady)
        assertEquals(HostAccessFallback.ROOT_UNAVAILABLE, fixture.controller.state.value.fallback)
    }

    @Test
    fun adbActionsAreDelegated() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.connectAdb()
        fixture.controller.startAdbPairing()

        assertEquals(1, fixture.connectRequests)
        assertEquals(1, fixture.pairingRequests)
    }

    private class Fixture(
        scope: CoroutineScope,
        initial: HostAccess = HostAccess.LOCAL,
        adbReady: Boolean = false,
    ) {
        val preferences = FakeStore(initial)
        val adbState = MutableStateFlow(AdbBridgeState(daemon = adbReady))
        val notifications = mutableListOf<Unit>()
        var connectRequests = 0
        var pairingRequests = 0
        var rootReady = false
        var rootProbeCalls = 0
        var rootProbe: suspend () -> Boolean = { rootReady }

        val controller = AppHostAccessController(
            scope = scope,
            preferences = preferences,
            rootProbe = { rootProbeCalls++; rootProbe() },
            adbStorage = adbState,
            connectAdbAction = { connectRequests++ },
            startPairingAction = { pairingRequests++ },
            openWirelessDebuggingAction = {},
            onChanged = { notifications += Unit },
        )
    }

    private class FakeStore(initial: HostAccess) : HostAccessStore {
        private val state = MutableStateFlow(initial)

        override val selected: Flow<HostAccess> = state

        override suspend fun setSelected(value: HostAccess) {
            state.value = value
        }
    }
}
