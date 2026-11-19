package io.github.whywood.superpi.host

import io.github.whywood.superpi.runtime.host.HostAccess
import io.github.whywood.superpi.runtime.host.HostAccessController
import io.github.whywood.superpi.runtime.host.HostEnvironment
import io.github.whywood.superpi.runtime.host.HostEnvironmentProvider

class AppHostEnvironmentProvider(
    private val accessController: HostAccessController,
    private val local: HostEnvironment,
    private val adb: HostEnvironment,
    private val root: HostEnvironment,
) : HostEnvironmentProvider {
    override fun current(): HostEnvironment = when (accessController.state.value.effective) {
        HostAccess.LOCAL -> local
        HostAccess.ADB -> adb
        HostAccess.ROOT -> root
    }
}
