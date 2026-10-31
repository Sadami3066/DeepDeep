package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiGlobalAction
import io.github.whywood.deepdeep.plugin.gui.GuiScreenshot
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshot
import io.github.whywood.deepdeep.plugin.gui.GuiSnapshotNode
import io.github.whywood.deepdeep.plugin.gui.GuiTextResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenLaunchReport
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenState

const val MAIN_DISPLAY_ID = 0

internal const val VIRTUAL_FAILURE_TRANSPORT = "transport"
internal const val VIRTUAL_FAILURE_BACKEND = "backend"
internal const val VIRTUAL_FAILURE_START = "start"

data class ScreenVirtualFailure(
    val category: String,
    val message: String,
    val recoverable: Boolean,
    val reason: String? = null,
)

internal fun ScreenVirtualFailure.hint(): String = when (category) {
    VIRTUAL_FAILURE_TRANSPORT -> "自动重连已尝试；请重新 screen_launch + screen_observe"
    VIRTUAL_FAILURE_BACKEND -> "无线调试/Root 不可用，需用户处理权限后重试"
    else -> "虚拟屏启动失败，可重试"
}

enum class ScreenTarget(val id: String) {
    MAIN("main"),
    VIRTUAL("virtual"),
    ;

    companion object {
        fun parse(value: String?): ScreenTarget = parseOrNull(value)
            ?: throw IllegalArgumentException("target 只支持 main 或 virtual")

        fun parseOrNull(value: String?): ScreenTarget? = when (value?.trim()?.lowercase()) {
            null, "", MAIN.id -> MAIN
            VIRTUAL.id -> VIRTUAL
            else -> null
        }
    }
}

data class ScreenCapture(
    val png: ByteArray,
    val width: Int,
    val height: Int,
    val backend: String,
)

data class ScreenDisplayInfo(
    val target: ScreenTarget,
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
    val adbAvailable: Boolean,
    val main: ScreenDisplayInfo,
    val virtualState: String,
    val virtual: ScreenDisplayInfo? = null,
    val virtualBackend: String? = null,
    val virtualUid: Int? = null,
    val virtualFailure: ScreenVirtualFailure? = null,
    val virtualTransportNotice: String? = null,
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

data class ScreenLaunchReport(
    val ok: Boolean,
    val message: String,
    val target: ScreenTarget,
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

    suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?)

    suspend fun setText(displayId: Int, selector: GuiSelector, text: String): GuiTextResult

    suspend fun waitFor(
        displayId: Int,
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): Boolean

    suspend fun screenshotMain(): GuiScreenshot

    suspend fun global(action: GuiGlobalAction)
}

interface ScreenAdbBackend {
    suspend fun available(): Boolean

    suspend fun observeMain(maxDepth: Int, maxNodes: Int): GuiSnapshot

    suspend fun tap(x: Int, y: Int)

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long)

    suspend fun keyEvent(keyCode: Int)

    suspend fun typeText(text: String)

    suspend fun shell(command: String): String
}

interface ScreenVirtualBackend {
    suspend fun state(): VirtualScreenState

    suspend fun tap(x: Int, y: Int): VirtualScreenResult

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): VirtualScreenResult

    suspend fun key(name: String): VirtualScreenResult

    suspend fun text(value: String): VirtualScreenResult

    suspend fun screenshot(): ByteArray?

    suspend fun launch(packageName: String): VirtualScreenLaunchReport

    fun helperUid(): Int?

    fun lastFailure(): ScreenVirtualFailure? = null

    fun lastTransportNotice(): String? = null
}

interface ScreenAppCatalog {
    fun launchableApps(): List<ScreenApp>

    fun launchOnMain(packageName: String): ScreenApp
}

interface ScreenController {
    suspend fun status(): ScreenStatusSnapshot

    suspend fun observe(
        target: ScreenTarget,
        maxDepth: Int,
        maxNodes: Int,
        expectedPackage: String?,
    ): ScreenObserveResult

    suspend fun find(target: ScreenTarget, selectors: List<GuiSelector>, limit: Int): ScreenFindResult

    suspend fun tap(target: ScreenTarget, selector: GuiSelector, durationMs: Int): String

    suspend fun swipe(target: ScreenTarget, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): String

    suspend fun scroll(
        target: ScreenTarget,
        direction: GuiScrollDirection,
        amount: Int,
        selector: GuiSelector?,
    ): String

    suspend fun typeText(target: ScreenTarget, selector: GuiSelector?, text: String): ScreenTextResult

    suspend fun key(target: ScreenTarget, name: String): String

    suspend fun waitFor(
        target: ScreenTarget,
        packageName: String?,
        text: String?,
        textGone: String?,
        timeoutMs: Long,
    ): Boolean

    suspend fun screenshot(target: ScreenTarget): ScreenCapture

    suspend fun launch(target: ScreenTarget, packageName: String): ScreenLaunchReport

    fun listApps(): List<ScreenApp>
}

internal const val BACKEND_ACCESSIBILITY = "accessibility"
internal const val BACKEND_ADB = "adb"
internal const val BACKEND_HELPER = "helper"
internal const val BACKEND_NONE = "none"

internal fun isAsciiText(text: String): Boolean =
    text.all { character -> character.code in 0x20..0x7E || character == '\n' || character == '\t' }

internal fun GuiSelector.describeSelector(): String = when (this) {
    is GuiSelector.Ref -> "ref=$ref"
    is GuiSelector.Text -> "text=「$text」"
    is GuiSelector.Desc -> "desc=「$desc」"
    is GuiSelector.ResourceId -> "resourceId=$id"
    is GuiSelector.ClassName -> "class=$name"
    is GuiSelector.Point -> "坐标 ($x, $y)"
}
