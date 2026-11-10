package io.github.whywood.deepdeep.host

import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostAccessController
import io.github.whywood.deepdeep.runtime.host.HostEnvironment
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider

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
