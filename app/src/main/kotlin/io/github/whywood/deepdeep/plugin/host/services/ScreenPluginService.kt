package io.github.whywood.deepdeep.plugin.host.services

import io.github.whywood.deepdeep.plugin.api.ScreenApp
import io.github.whywood.deepdeep.plugin.api.ScreenBounds
import io.github.whywood.deepdeep.plugin.api.ScreenCapability
import io.github.whywood.deepdeep.plugin.api.ScreenNode
import io.github.whywood.deepdeep.plugin.api.ScreenObservation
import io.github.whywood.deepdeep.plugin.api.ScreenScreenshot
import io.github.whywood.deepdeep.plugin.api.ScreenService
import io.github.whywood.deepdeep.plugin.api.ScreenStatus
import io.github.whywood.deepdeep.plugin.api.ScreenTarget
import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_DEPTH
import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_NODES
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.virtualscreen.VirtualScreenFeature
import io.github.whywood.deepdeep.screen.ScreenController
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ScreenPluginService(
    private val virtual: ScreenController?,
    private val main: ScreenController?,
    private val virtualScreen: VirtualScreenFeature?,
    private val screenshotDir: File?,
    scope: CoroutineScope,
) : ScreenService {
    @Volatile
    private var cachedStatus: ScreenStatus = ScreenStatus()

    init {
        scope.launch { refreshStatus() }
    }

    override val status: ScreenStatus
        get() = cachedStatus

    override suspend fun startVirtual(): ScreenStatus {
        val feature = virtualScreen ?: throw IllegalStateException("virtual screen control is not available")
        feature.runAction(VirtualScreenFeature.ACTION_START)
        refreshStatus()
        return cachedStatus
    }

    override suspend fun stopVirtual() {
        val feature = virtualScreen ?: throw IllegalStateException("virtual screen control is not available")
        feature.runAction(VirtualScreenFeature.ACTION_STOP)
        refreshStatus()
    }

    override suspend fun observe(target: ScreenTarget, packageName: String?): ScreenObservation =
        controllerFor(target).observe(GUI_MAX_DEPTH, GUI_MAX_NODES, packageName).snapshot.toPluginObservation()

    override suspend fun screenshot(target: ScreenTarget): ScreenScreenshot {
        val capture = controllerFor(target).screenshot()
        val directory = screenshotDir ?: throw IllegalStateException("screenshot directory is not available")
        directory.mkdirs()
        val file = File(directory, "plugin-${System.currentTimeMillis()}.png")
        file.writeBytes(capture.png)
        return ScreenScreenshot(
            path = file.absolutePath,
            width = capture.width,
            height = capture.height,
            capturedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun apps(): List<ScreenApp> = controllerFor(ScreenTarget.VIRTUAL).listApps().map { app ->
        ScreenApp(packageName = app.packageName, label = app.label)
    }

    override suspend fun launch(packageName: String, target: ScreenTarget): Boolean =
        controllerFor(target).launch(packageName).ok

    override suspend fun tap(x: Int, y: Int, target: ScreenTarget): Boolean {
        controllerFor(target).tap(GuiSelector.Point(x, y), TAP_DURATION_MS)
        return true
    }

    override suspend fun tapNode(ref: String, target: ScreenTarget): Boolean {
        controllerFor(target).tap(GuiSelector.Ref(ref), TAP_DURATION_MS)
        return true
    }

    override suspend fun swipe(
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Int,
        target: ScreenTarget,
    ): Boolean {
        controllerFor(target).swipe(fromX, fromY, toX, toY, durationMs.toLong())
        return true
    }

    override suspend fun scroll(deltaY: Int, target: ScreenTarget): Boolean {
        val direction = if (deltaY >= 0) GuiScrollDirection.DOWN else GuiScrollDirection.UP
        val amount = (abs(deltaY) / SCROLL_PIXELS_PER_STEP).coerceIn(1, MAX_SCROLL_STEPS)
        controllerFor(target).scroll(direction, amount, null)
        return true
    }

    override suspend fun type(text: String, target: ScreenTarget): Boolean {
        controllerFor(target).typeText(null, text)
        return true
    }

    override suspend fun key(keyCode: String, target: ScreenTarget): Boolean {
        controllerFor(target).key(keyCode)
        return true
    }

    override suspend fun waitFor(text: String, timeoutMs: Long, target: ScreenTarget): Boolean =
        controllerFor(target).waitFor(null, text, null, timeoutMs).matched

    private fun controllerFor(target: ScreenTarget): ScreenController = when (target) {
        ScreenTarget.VIRTUAL -> virtual ?: throw IllegalStateException("screen control is not available")
        ScreenTarget.MAIN -> main ?: throw IllegalStateException("main screen control is not available")
    }

    private suspend fun refreshStatus() {
        val virtualSnapshot = runCatching { virtual?.status() }.getOrNull()
        val mainSnapshot = runCatching { main?.status() }.getOrNull()
        val lifecycleOnline = runCatching {
            virtualScreen?.status() is io.github.whywood.deepdeep.plugin.api.PluginStatus.Ready
        }.getOrDefault(false)
        val virtualAvailable = virtualSnapshot?.virtual != null || lifecycleOnline
        cachedStatus = ScreenStatus(
            virtual = ScreenCapability(
                available = virtualAvailable,
                accessibility = virtualSnapshot?.accessibilityConnected == true,
                screenshot = virtualAvailable,
                input = virtualAvailable,
            ),
            main = ScreenCapability(
                available = mainSnapshot?.main != null,
                accessibility = mainSnapshot?.accessibilityConnected == true,
                screenshot = mainSnapshot?.mainScreenshotAvailable == true,
                input = mainSnapshot?.mainInputAvailable == true,
            ),
        )
    }

    private fun GuiSnapshot.toPluginObservation(): ScreenObservation = ScreenObservation(
        packageName = packageName,
        windowTitle = windowTitle,
        nodes = nodes.map { node ->
            ScreenNode(
                ref = node.ref,
                className = node.className,
                text = node.text,
                contentDescription = node.desc,
                bounds = ScreenBounds(
                    left = node.bounds.left,
                    top = node.bounds.top,
                    right = node.bounds.right,
                    bottom = node.bounds.bottom,
                ),
                clickable = node.clickable,
            )
        },
        capturedAt = System.currentTimeMillis(),
    )

    private companion object {
        const val TAP_DURATION_MS = 60

        const val SCROLL_PIXELS_PER_STEP = 300

        const val MAX_SCROLL_STEPS = 10
    }
}
