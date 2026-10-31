package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.vscreen.VirtualScreenEngine
import io.github.whywood.deepdeep.vscreen.VirtualScreenLaunchReport
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenRuntime
import io.github.whywood.deepdeep.vscreen.VirtualScreenState

class VirtualEngineBackend(
    private val engineProvider: () -> VirtualScreenEngine? = { VirtualScreenRuntime.engine() },
) : ScreenVirtualBackend {
    override suspend fun state(): VirtualScreenState = requireEngine().state.value

    override suspend fun tap(x: Int, y: Int): VirtualScreenResult = requireEngine().tap(x, y)

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): VirtualScreenResult =
        requireEngine().swipe(x1, y1, x2, y2, durationMs)

    override suspend fun key(name: String): VirtualScreenResult = requireEngine().key(name)

    override suspend fun text(value: String): VirtualScreenResult = requireEngine().text(value)

    override suspend fun screenshot(): ByteArray? = requireEngine().screenshot()

    override suspend fun launch(packageName: String): VirtualScreenLaunchReport = requireEngine().launchWithReport(packageName)

    override fun helperUid(): Int? = runCatching { requireEngine().helperUid }.getOrNull()

    private fun requireEngine(): VirtualScreenEngine = engineProvider()
        ?: throw IllegalStateException("虚拟屏引擎未就绪：请到插件页打开「虚拟屏幕」后再重试")
}
