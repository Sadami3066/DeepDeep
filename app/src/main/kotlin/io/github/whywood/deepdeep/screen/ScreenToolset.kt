package io.github.whywood.deepdeep.screen

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.service.GuiAccessibilityState
import io.github.whywood.deepdeep.vscreen.VirtualScreenRuntime
import java.io.File

class ScreenToolset(
    context: Context,
    private val screenshotDir: File,
    mainShell: MainScreenShell?,
    private val actions: GuiActions = GuiAccessibilityActions(),
) {
    private val appContext = context.applicationContext

    val virtual: ScreenController = ScreenRouter(
        accessibility = AccessibilityScreenBackend(actions),
        virtual = VirtualEngineBackend { VirtualScreenRuntime.engine() },
        apps = AndroidScreenAppCatalog(appContext),
        accessibilityEnabled = { GuiAccessibilityState.isEnabled(appContext) },
    )

    val main: ScreenController = MainScreenController(
        actions = actions,
        apps = AndroidScreenAppCatalog(appContext),
        displaySize = {
            val manager = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val metrics = DisplayMetrics()
            checkNotNull(manager?.getDisplay(0)) { "Main screen is unavailable" }.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        },
        virtualStatus = { virtual.status() },
        shell = mainShell,
        screenshotSupported = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R },
    )

    fun tools(): List<CodingTool> = screenTools(
        controller = virtual,
        screenshotDir = screenshotDir,
        mainController = main,
        onVirtualActivity = { VirtualScreenRuntime.engine()?.touch() },
    )
}
