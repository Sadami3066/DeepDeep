package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshotNode
import io.github.whywood.deepdeep.plugin.gui.GuiTextResult
import io.github.whywood.deepdeep.plugin.gui.GuiWaitOutcome
import io.github.whywood.deepdeep.vscreen.VirtualScreenLaunchReport
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenState

internal const val VIRTUAL_FAILURE_TRANSPORT = "transport"
internal const val VIRTUAL_FAILURE_BACKEND = "backend"
internal const val VIRTUAL_FAILURE_START = "start"

data class ScreenVirtualFailure(
    val category: String,
    val message: String,
    val recoverable: Boolean,
    val reason: String? = null,
)

data class ScreenCapture(
    val png: ByteArray,
    val width: Int,
    val height: Int,
    val backend: String,
)

data class ScreenDisplayInfo(
    val displayId: Int,
    val width: Int,
    val height: Int,
    val backend: String,
    val nodeBackend: String = backend,
    val inputBackend: String = backend,
)

data class ScreenStatusSnapshot(
    val accessibilityConnected: Boolean,
    val accessibilityEnabled: Boolean,
    val virtualState: String,
    val virtual: ScreenDisplayInfo? = null,
    val virtualBackend: String? = null,
    val virtualUid: Int? = null,
    val virtualFailure: ScreenVirtualFailure? = null,
    val virtualTransportNotice: String? = null,
    val reconnecting: Boolean = false,
    val main: ScreenDisplayInfo? = null,
    val mainInputAvailable: Boolean = false,
    val mainScreenshotAvailable: Boolean = false,
    val mainKeys: List<String> = emptyList(),
)

data class ScreenObserveResult(
    val backend: String,
    val snapshot: GuiSnapshot,
    val filteredNodes: Int = 0,
)

data class ScreenFindResult(
    val backend: String,
    val snapshot: GuiSnapshot,
    val matches: List<GuiSnapshotNode>,
)

data class ScreenTextResult(
    val backend: String,
    val text: String,
    val verified: Boolean,
)

data class ScreenWaitOutcome(
    val matched: Boolean,
    val packageName: String? = null,
    val windowTitle: String? = null,
)

data class ScreenLaunchReport(
    val ok: Boolean,
    val message: String,
    val packageName: String,
    val displayId: Int = INVALID_DISPLAY,
    val taskDisplayId: Int = INVALID_DISPLAY,
    val landedOnMain: Boolean = false,
    val backend: String = "",
) {
    val landedOnExpectedDisplay: Boolean
        get() = taskDisplayId == displayId

    companion object {
        const val INVALID_DISPLAY = -1
    }
}

data class ScreenApp(
    val packageName: String,
    val label: String,
    val component: String = "",
)

interface ScreenAccessibilityBackend {
    val connected: Boolean

    suspend fun observe(displayId: Int, maxDepth: Int, maxNodes: Int): GuiSnapshot

    suspend fun tap(displayId: Int, selector: GuiSelector, durationMs: Int)

    fun invalidateSnapshots()

    fun bounds(displayId: Int, selector: GuiSelector): io.github.whywood.deepdeep.plugin.gui.GuiBounds

    suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult

    suspend fun waitFor(
        displayId: Int,
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): GuiWaitOutcome

}

interface ScreenVirtualBackend {
    suspend fun state(): VirtualScreenState

    suspend fun tap(x: Int, y: Int, durationMs: Int = 60): VirtualScreenResult

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): VirtualScreenResult

    suspend fun key(name: String): VirtualScreenResult

    suspend fun text(value: String): VirtualScreenResult

    suspend fun screenshot(): ByteArray?

    suspend fun launch(packageName: String): VirtualScreenLaunchReport

    fun helperUid(): Int?

    fun lastFailure(): ScreenVirtualFailure? = null

    fun lastTransportNotice(): String? = null

    fun reconnecting(): Boolean = false
}

interface ScreenAppCatalog {
    fun launchableApps(): List<ScreenApp>

    fun app(packageName: String): ScreenApp = launchableApps().firstOrNull { it.packageName == packageName }
        ?: error("App not found: $packageName")

    fun launchOnMain(packageName: String): ScreenApp = error("Main screen app launch is unavailable")

}

interface ScreenController {
    suspend fun status(): ScreenStatusSnapshot

    suspend fun observe(
        maxDepth: Int,
        maxNodes: Int,
        expectedPackage: String?,
    ): ScreenObserveResult

    suspend fun find(selectors: List<GuiSelector>, limit: Int): ScreenFindResult

    suspend fun tap(selector: GuiSelector, durationMs: Int): String

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String

    suspend fun scroll(
        direction: GuiScrollDirection,
        amount: Int,
        selector: GuiSelector?,
    ): String

    suspend fun typeText(selector: GuiSelector?, text: String): ScreenTextResult

    suspend fun key(name: String): String

    suspend fun waitFor(
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): ScreenWaitOutcome

    suspend fun screenshot(): ScreenCapture

    suspend fun launch(packageName: String): ScreenLaunchReport

    suspend fun listApps(): List<ScreenApp>
}

internal const val BACKEND_ACCESSIBILITY = "accessibility"
internal const val BACKEND_ADB = "adb"
internal const val BACKEND_HELPER = "helper"
internal const val BACKEND_NONE = "none"

internal fun isAsciiText(text: String): Boolean =
    text.all { character -> character.code in 0x20..0x7E || character == '\n' || character == '\t' }

internal fun GuiSelector.describeSelector(): String = when (this) {
    is GuiSelector.Ref -> "ref=$ref"
    is GuiSelector.Text -> "text=\"$text\""
    is GuiSelector.Desc -> "desc=\"$desc\""
    is GuiSelector.ResourceId -> "resourceId=$id"
    is GuiSelector.ClassName -> "class=$name"
    is GuiSelector.Point -> "point ($x, $y)"
}
