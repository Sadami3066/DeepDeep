package io.github.whywood.deepdeep.ui.onboarding

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevPackageCatalog
import io.github.whywood.deepdeep.data.DevPackageManager
import io.github.whywood.deepdeep.shell.LinuxEnvironment
import io.github.whywood.deepdeep.shell.LinuxInstallState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal object OnboardingEnvironmentInstaller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queued = AtomicBoolean(false)

    fun enqueue(manager: DevPackageManager, linux: LinuxEnvironment, bundles: List<DevBundle>) {
        if (bundles.isEmpty()) return
        if (!queued.compareAndSet(false, true)) return
        scope.launch {
            if (!linux.isSupported) return@launch
            withTimeoutOrNull(READY_TIMEOUT_MS) {
                linux.state.first { it is LinuxInstallState.Installed }
                manager.state.first { !it.busy && !it.checking }
                true
            } ?: return@launch
            manager.clearError()
            manager.installBundle(merge(bundles))
        }
    }

    internal fun merge(bundles: List<DevBundle>): DevBundle {
        val packageIds = bundles.flatMapTo(LinkedHashSet()) { it.packageIds }
        return DevBundle(
            id = "onboarding_selection",
            titleRes = R.string.onboarding_environment_install_label,
            packageIds = DevPackageCatalog.packages.map { it.id }.filter { it in packageIds },
            sizeLabel = DevPackageCatalog.estimatedSizeLabel(packageIds),
            postInstallCommand = bundles.mapNotNull { it.postInstallCommand }
                .joinToString("\n")
                .ifBlank { null },
            downloads = bundles.flatMap { it.downloads }.distinctBy { it.id },
        )
    }

    private const val READY_TIMEOUT_MS = 10 * 60_000L
}
