package io.github.whywood.deepdeep.screen

import com.wangzan101.mika.core.visual.FrameGrid

import io.github.whywood.deepdeep.vscreen.VirtualScreenEngine
import io.github.whywood.deepdeep.vscreen.VirtualScreenFailureCategory
import io.github.whywood.deepdeep.vscreen.VirtualScreenLaunchReport
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenRuntime
import io.github.whywood.deepdeep.vscreen.VirtualScreenState

class VirtualEngineBackend(
    private val engineProvider: () -> VirtualScreenEngine? = { VirtualScreenRuntime.engine() },
) : ScreenVirtualBackend {
    override suspend fun state(): VirtualScreenState = requireEngine().state.value

    override fun effectFrameGrid(): FrameGrid? {
        val frame = (engineProvider()?.state?.value as? VirtualScreenState.Online)?.frame ?: return null
        return runCatching { FrameGrid.sample(frame.width, frame.height, frame::getPixel) }.getOrNull()
    }

    override fun previewForEffect(display: ScreenDisplayInfo) =
        (engineProvider()?.state?.value as? VirtualScreenState.Online)
            ?.takeIf { it.displayId == display.displayId && it.instanceId == display.instanceId }
            ?.frame

    override suspend fun tap(x: Int, y: Int, durationMs: Int): VirtualScreenResult = requireEngine().tap(x, y, durationMs)

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): VirtualScreenResult =
        requireEngine().swipe(x1, y1, x2, y2, durationMs)

    override suspend fun key(name: String): VirtualScreenResult = requireEngine().key(name)

    override suspend fun text(value: String): VirtualScreenResult = requireEngine().text(value)

    override suspend fun screenshot(): ByteArray? = requireEngine().screenshot()

    override suspend fun launch(packageName: String): VirtualScreenLaunchReport = requireEngine().launchWithReport(packageName)

    override fun reconnecting(): Boolean = engineProvider()?.reconnecting == true

    override fun helperUid(): Int? = runCatching { requireEngine().helperUid }.getOrNull()

    override fun lastFailure(): ScreenVirtualFailure? = runCatching {
        requireEngine().lastFailure?.let { failure ->
            ScreenVirtualFailure(
                category = when (failure.category) {
                    VirtualScreenFailureCategory.TRANSPORT -> VIRTUAL_FAILURE_TRANSPORT
                    VirtualScreenFailureCategory.BACKEND -> VIRTUAL_FAILURE_BACKEND
                    VirtualScreenFailureCategory.START -> VIRTUAL_FAILURE_START
                },
                message = failure.message,
                recoverable = failure.recoverable,
                reason = failure.reason,
            )
        }
    }.getOrNull()

    override fun lastTransportNotice(): String? = runCatching { requireEngine().lastTransportNotice }.getOrNull()

    private fun requireEngine(): VirtualScreenEngine = engineProvider()
        ?: throw IllegalStateException("Virtual screen engine is not ready; open the virtual screen plugin page and retry")
}
