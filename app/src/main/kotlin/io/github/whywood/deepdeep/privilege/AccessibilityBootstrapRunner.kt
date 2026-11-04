package io.github.whywood.deepdeep.privilege

import android.util.Log
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.runtime.RootCommandResult
import io.github.whywood.deepdeep.service.GuiAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

internal class AccessibilityBootstrapRunner(
    private val component: String,
    private val exec: suspend (command: String, timeoutMillis: Long) -> RootCommandResult,
    private val isEnabled: () -> Boolean,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var lastAttemptAt = 0L
    private var retryAt = 0L
    private var failures = 0

    fun onPrivilegeReady() {
        scope.launch { ensure(fresh = true) }
    }

    private suspend fun ensure(fresh: Boolean = false) {
        if (!mutex.tryLock()) return
        try {
            if (fresh) retryAt = 0L
            val now = System.currentTimeMillis()
            if (now < retryAt || now - lastAttemptAt < MIN_ATTEMPT_INTERVAL_MS) return
            if (!isEnabled()) return
            lastAttemptAt = now
            val snapshot = readSettings() ?: run {
                fail(READ_FAILED)
                return
            }
            val plan = planAccessibilityBootstrap(component, snapshot.enabledServices, snapshot.accessibilityEnabled)
            val done = when {
                plan == null -> true
                plan.services != null -> enable(plan)
                else -> rebindIfCrashed()
            }
            if (done) reset()
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun enable(plan: AccessibilityBootstrapPlan): Boolean {
        if (!runCommand(accessibilityWriteCommand(plan))) {
            fail(WRITE_FAILED)
            return false
        }
        delay(VERIFY_DELAY_MS)
        val verified = readSettings()
        if (verified == null || !containsService(verified)) {
            fail(VERIFY_FAILED)
            return false
        }
        Log.i(TAG, "enabled accessibility service $component")
        return true
    }

    private suspend fun rebindIfCrashed(): Boolean {
        delay(REBIND_SETTLE_MS)
        val settled = readSettings() ?: run {
            fail(READ_FAILED)
            return false
        }
        if (settled.accessibilityEnabled == 1) return true
        if (!containsService(settled)) return true
        val plan = planAccessibilityRebind(component, settled.enabledServices) ?: return true
        if (!runCommand(accessibilityRebindCommand(plan))) {
            fail(WRITE_FAILED)
            return false
        }
        Log.i(TAG, "re-enabled accessibility service $component")
        return true
    }

    private suspend fun readSettings(): AccessibilitySettingsSnapshot? {
        val result = runCatching { exec(accessibilityReadCommand(), EXEC_TIMEOUT_MS) }.getOrNull() ?: return null
        if (result.exitCode != 0) return null
        return parseAccessibilitySettings(result.stdout)
    }

    private suspend fun runCommand(command: String): Boolean =
        runCatching { exec(command, EXEC_TIMEOUT_MS) }.getOrNull()?.exitCode == 0

    private fun containsService(snapshot: AccessibilitySettingsSnapshot): Boolean =
        planAccessibilityBootstrap(component, snapshot.enabledServices, 1) == null

    private fun reset() {
        failures = 0
        retryAt = 0L
    }

    private fun fail(reason: String) {
        failures += 1
        Log.w(TAG, "accessibility bootstrap $reason (attempt $failures)")
        val delayMillis = RETRY_DELAYS_MS.getOrNull(failures - 1) ?: return
        retryAt = System.currentTimeMillis() + delayMillis
        scope.launch {
            delay(delayMillis)
            ensure()
        }
    }

    private companion object {
        const val TAG = "AccessibilityBootstrap"
        const val MIN_ATTEMPT_INTERVAL_MS = 15_000L
        const val EXEC_TIMEOUT_MS = 12_000L
        const val VERIFY_DELAY_MS = 1_000L
        const val REBIND_SETTLE_MS = 2_000L
        const val READ_FAILED = "read failed"
        const val WRITE_FAILED = "write failed"
        const val VERIFY_FAILED = "write not visible"
        val RETRY_DELAYS_MS = longArrayOf(30_000L, 120_000L)
    }
}

internal fun accessibilityServiceComponent(): String =
    "${BuildConfig.APPLICATION_ID}/${GuiAccessibilityService::class.java.name}"
