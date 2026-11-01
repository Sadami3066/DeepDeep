package io.github.whywood.deepdeep.screen

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_DEPTH
import io.github.whywood.deepdeep.plugin.gui.GUI_MAX_NODES
import io.github.whywood.deepdeep.plugin.gui.GuiNodeTree
import io.github.whywood.deepdeep.plugin.gui.GuiScrollDirection
import io.github.whywood.deepdeep.plugin.gui.GuiSelector
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

const val SCREEN_DEFAULT_LINUX_SCREENSHOT_DIR = "/gui/screenshots"

private const val DEFAULT_TAP_MILLIS = 60
private const val MAX_TAP_MILLIS = 3000
private const val DEFAULT_SWIPE_MILLIS = 300
private const val MIN_SWIPE_MILLIS = 50L
private const val MAX_SWIPE_MILLIS = 5000L
private const val DEFAULT_SCROLL_AMOUNT = 1
private const val MAX_SCROLL_AMOUNT = 10
private const val DEFAULT_WAIT_MILLIS = 5000
private const val MIN_WAIT_MILLIS = 100
private const val MAX_WAIT_MILLIS = 60000
private const val DEFAULT_FIND_LIMIT = 20
private const val MAX_FIND_LIMIT = 50

private val SCREEN_KEYS = setOf(
    "back",
    "home",
    "recents",
    "notifications",
    "enter",
    "delete",
    "tab",
    "escape",
    "space",
    "arrow_up",
    "arrow_down",
    "arrow_left",
    "arrow_right",
    "volume_up",
    "volume_down",
)

private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

internal fun screenToolNames(): List<String> = listOf(
    "screen_status",
    "screen_observe",
    "screen_screenshot",
    "screen_tap",
    "screen_swipe",
    "screen_scroll",
    "screen_type",
    "screen_key",
    "screen_wait",
    "screen_app",
)

fun screenTools(
    controller: ScreenController,
    screenshotDir: File,
    linuxScreenshotDir: String = SCREEN_DEFAULT_LINUX_SCREENSHOT_DIR,
): List<CodingTool> = listOf(
    statusTool(controller),
    observeTool(controller),
    screenshotTool(controller, screenshotDir, linuxScreenshotDir),
    tapTool(controller),
    swipeTool(controller),
    scrollTool(controller),
    typeTool(controller),
    keyTool(controller),
    waitTool(controller),
    appTool(controller),
)

internal fun targetOf(args: JsonObject): ScreenTarget = ScreenTarget.parseOrNull(args.stringOrNull("target"))
    ?: throw IllegalArgumentException("target 只支持 main 或 virtual")

internal fun parseScreenTapSelector(args: JsonObject): GuiSelector {
    val ref = args.stringOrNull("ref")?.trim()?.takeIf { it.isNotEmpty() }
    val text = args.stringOrNull("text")?.trim()?.takeIf { it.isNotEmpty() }
    val desc = args.stringOrNull("desc")?.trim()?.takeIf { it.isNotEmpty() }
    val resourceId = args.stringOrNull("resourceId")?.trim()?.takeIf { it.isNotEmpty() }
    val className = args.stringOrNull("class")?.trim()?.takeIf { it.isNotEmpty() }
    val x = args.intOrNull("x")
    val y = args.intOrNull("y")
    val named = listOfNotNull(ref, text, desc, resourceId, className)
    if (x != null || y != null) {
        if (x == null || y == null) throw IllegalArgumentException("坐标选择需要同时提供 x 和 y")
        if (named.isNotEmpty()) {
            throw IllegalArgumentException("ref、text、desc、resourceId、class 与 x、y 不能同时使用")
        }
        if (x < 0 || y < 0) throw IllegalArgumentException("坐标 x、y 不能为负数")
        return GuiSelector.Point(x, y)
    }
    if (named.size > 1) {
        throw IllegalArgumentException("ref、text、desc、resourceId、class 只能提供一种")
    }
    return when {
        ref != null -> GuiSelector.Ref(ref)
        text != null -> GuiSelector.Text(text)
        desc != null -> GuiSelector.Desc(desc)
        resourceId != null -> GuiSelector.ResourceId(resourceId)
        className != null -> GuiSelector.ClassName(className)
        else -> throw IllegalArgumentException("需要提供 ref、text、desc、resourceId、class 或 x、y 之一作为目标")
    }
}

internal fun parseObserveFilters(args: JsonObject): List<GuiSelector> {
    val filters = buildList {
        args.stringOrNull("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.Text(it)) }
        args.stringOrNull("desc")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.Desc(it)) }
        args.stringOrNull("resourceId")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.ResourceId(it)) }
        args.stringOrNull("class")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.ClassName(it)) }
    }
    if (filters.size > 1) throw IllegalArgumentException("text、desc、resourceId、class 只能提供一种")
    return filters
}

internal fun parseScreenTextSelector(args: JsonObject): GuiSelector? {
    val ref = args.stringOrNull("ref")?.trim()?.takeIf { it.isNotEmpty() }
    val fieldText = args.stringOrNull("fieldText")?.trim()?.takeIf { it.isNotEmpty() }
    if (ref != null && fieldText != null) {
        throw IllegalArgumentException("ref 与 fieldText 只能提供一种")
    }
    return when {
        ref != null -> GuiSelector.Ref(ref)
        fieldText != null -> GuiSelector.Text(fieldText)
        else -> null
    }
}

internal fun parseScrollDirection(args: JsonObject): GuiScrollDirection =
    when (args.requiredString("direction").lowercase()) {
        "up" -> GuiScrollDirection.UP
        "down" -> GuiScrollDirection.DOWN
        "left" -> GuiScrollDirection.LEFT
        "right" -> GuiScrollDirection.RIGHT
        else -> throw IllegalArgumentException("direction 只支持 up、down、left、right")
    }

internal fun parseScreenKey(args: JsonObject): String {
    val name = args.stringOrNull("key")?.trim()?.lowercase().orEmpty()
    if (name.isEmpty()) throw IllegalArgumentException("缺少参数 key")
    if (name !in SCREEN_KEYS) {
        throw IllegalArgumentException("不支持的 key：$name；可用：${SCREEN_KEYS.joinToString(", ")}")
    }
    return name
}

internal fun parseScreenLaunchPackage(args: JsonObject): String {
    val packageName = args.stringOrNull("packageName")?.trim().orEmpty()
    if (packageName.isEmpty()) throw IllegalArgumentException("缺少参数 packageName")
    if (!PACKAGE_NAME.matches(packageName)) {
        throw IllegalArgumentException("packageName 不是合法的 Android 包名：$packageName")
    }
    return packageName
}

internal fun parseScreenTypeText(args: JsonObject): String {
    val text = args.stringOrNull("text")
        ?: throw IllegalArgumentException("缺少参数 text")
    if (text.isEmpty()) throw IllegalArgumentException("text 不能为空")
    return text
}

internal fun tapDurationOf(args: JsonObject): Int {
    val duration = args.intOrNull("durationMs") ?: DEFAULT_TAP_MILLIS
    if (duration !in 0..MAX_TAP_MILLIS) {
        throw IllegalArgumentException("durationMs 需要在 0..$MAX_TAP_MILLIS 之间")
    }
    return duration
}

internal fun swipeDurationOf(args: JsonObject): Long {
    val duration = args.longOrNull("durationMs") ?: DEFAULT_SWIPE_MILLIS.toLong()
    if (duration !in MIN_SWIPE_MILLIS..MAX_SWIPE_MILLIS) {
        throw IllegalArgumentException("durationMs 需要在 $MIN_SWIPE_MILLIS..$MAX_SWIPE_MILLIS 之间")
    }
    return duration
}

internal fun scrollAmountOf(args: JsonObject): Int {
    val amount = args.intOrNull("amount") ?: DEFAULT_SCROLL_AMOUNT
    if (amount !in 1..MAX_SCROLL_AMOUNT) {
        throw IllegalArgumentException("amount 需要在 1..$MAX_SCROLL_AMOUNT 之间")
    }
    return amount
}

internal fun waitTimeoutOf(args: JsonObject): Long {
    val timeout = args.intOrNull("timeoutMs") ?: DEFAULT_WAIT_MILLIS
    if (timeout !in MIN_WAIT_MILLIS..MAX_WAIT_MILLIS) {
        throw IllegalArgumentException("timeoutMs 需要在 $MIN_WAIT_MILLIS..$MAX_WAIT_MILLIS 之间")
    }
    return timeout.toLong()
}

internal fun filterLimitOf(args: JsonObject): Int {
    val limit = args.intOrNull("limit") ?: DEFAULT_FIND_LIMIT
    if (limit !in 1..MAX_FIND_LIMIT) {
        throw IllegalArgumentException("limit 需要在 1..$MAX_FIND_LIMIT 之间")
    }
    return limit
}

internal fun observeLimitsOf(args: JsonObject): Pair<Int, Int> {
    val maxDepth = args.intOrNull("maxDepth") ?: SCREEN_DEFAULT_MAX_DEPTH
    val maxNodes = args.intOrNull("maxNodes") ?: GUI_MAX_NODES
    if (maxDepth !in 1..GUI_MAX_DEPTH) {
        throw IllegalArgumentException("maxDepth 需要在 1..$GUI_MAX_DEPTH 之间")
    }
    if (maxNodes !in 1..GUI_MAX_NODES) {
        throw IllegalArgumentException("maxNodes 需要在 1..$GUI_MAX_NODES 之间")
    }
    return maxDepth to maxNodes
}

internal enum class ScreenAppAction { LIST, LAUNCH }

internal fun parseScreenAppAction(args: JsonObject): ScreenAppAction =
    when (val action = args.stringOrNull("action")?.trim()?.lowercase()) {
        null, "" -> throw IllegalArgumentException("缺少参数 action：list 或 launch")
        "list" -> ScreenAppAction.LIST
        "launch" -> ScreenAppAction.LAUNCH
        else -> throw IllegalArgumentException("action 只支持 list 或 launch，收到：$action")
    }

private val statusParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private val observeParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("maxDepth") {
            put("type", "integer")
            put(
                "description",
                "Maximum node depth to include, 1-$GUI_MAX_DEPTH. Defaults to $SCREEN_DEFAULT_MAX_DEPTH, " +
                    "which covers the full visible hierarchy; lower it only to trim very large trees.",
            )
        }
        putJsonObject("maxNodes") {
            put("type", "integer")
            put("description", "Maximum number of nodes to include, 1-$GUI_MAX_NODES. Defaults to $GUI_MAX_NODES.")
        }
        putJsonObject("packageName") {
            put("type", "string")
            put("description", "Optional expected package of the current window; the call fails when another app is in front.")
        }
        putJsonObject("text") {
            put("type", "string")
            put("description", "Keep only nodes whose visible text contains this value, ignoring case.")
        }
        putJsonObject("desc") {
            put("type", "string")
            put("description", "Keep only nodes whose content description contains this value, ignoring case.")
        }
        putJsonObject("resourceId") {
            put("type", "string")
            put("description", "Keep only nodes with this view id, either full form (com.app:id/ok) or short form (ok).")
        }
        putJsonObject("class") {
            put("type", "string")
            put("description", "Keep only nodes with this view class, either full form (android.widget.Button) or simple form (Button).")
        }
        putJsonObject("limit") {
            put("type", "integer")
            put("description", "Maximum matches to return, 1-$MAX_FIND_LIMIT. Defaults to $DEFAULT_FIND_LIMIT.")
        }
    }
}

private val screenshotParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
    }
}

private val tapParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("ref") {
            put("type", "string")
            put("description", "Node ref from the latest screen_observe on the same target screen.")
        }
        putJsonObject("text") {
            put("type", "string")
            put("description", "Visible text to match case-insensitively as a substring.")
        }
        putJsonObject("desc") {
            put("type", "string")
            put("description", "Content description to match case-insensitively as a substring.")
        }
        putJsonObject("resourceId") {
            put("type", "string")
            put("description", "View resource id, either full form (com.app:id/ok) or short form (ok).")
        }
        putJsonObject("class") {
            put("type", "string")
            put("description", "View class name, either full form (android.widget.Button) or simple form (Button).")
        }
        putJsonObject("x") {
            put("type", "integer")
            put("description", "Screen x coordinate in pixels; requires y and must lie inside the current screen.")
        }
        putJsonObject("y") {
            put("type", "integer")
            put("description", "Screen y coordinate in pixels; requires x and must lie inside the current screen.")
        }
        putJsonObject("durationMs") {
            put("type", "integer")
            put("description", "Press duration in milliseconds, 0-$MAX_TAP_MILLIS. Values of 400 or more are treated as a long press.")
        }
    }
}

private val swipeParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("x1") {
            put("type", "integer")
            put("description", "Start x coordinate in pixels.")
        }
        putJsonObject("y1") {
            put("type", "integer")
            put("description", "Start y coordinate in pixels.")
        }
        putJsonObject("x2") {
            put("type", "integer")
            put("description", "End x coordinate in pixels.")
        }
        putJsonObject("y2") {
            put("type", "integer")
            put("description", "End y coordinate in pixels.")
        }
        putJsonObject("durationMs") {
            put("type", "integer")
            put("description", "Swipe duration in milliseconds, $MIN_SWIPE_MILLIS-$MAX_SWIPE_MILLIS. Defaults to $DEFAULT_SWIPE_MILLIS.")
        }
    }
    put("required", buildJsonArray {
        add(JsonPrimitive("x1"))
        add(JsonPrimitive("y1"))
        add(JsonPrimitive("x2"))
        add(JsonPrimitive("y2"))
    })
}

private val scrollParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("direction") {
            put("type", "string")
            put(
                "description",
                "Scroll direction: down reveals later or lower content, up reveals earlier or upper content, " +
                    "right reveals content to the right, left reveals content to the left.",
            )
            putJsonArray("enum") {
                add(JsonPrimitive("up"))
                add(JsonPrimitive("down"))
                add(JsonPrimitive("left"))
                add(JsonPrimitive("right"))
            }
        }
        putJsonObject("ref") {
            put("type", "string")
            put(
                "description",
                "Optional ref of a scrollable node from the latest screen_observe on the same screen; " +
                    "defaults to the whole screen.",
            )
        }
        putJsonObject("amount") {
            put("type", "integer")
            put("description", "Number of swipe steps, 1-$MAX_SCROLL_AMOUNT. Defaults to $DEFAULT_SCROLL_AMOUNT.")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("direction")) })
}

private val typeParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("text") {
            put("type", "string")
            put("description", "Text to type. Text with non-ASCII characters needs accessibility plus a ref or fieldText.")
        }
        putJsonObject("ref") {
            put("type", "string")
            put("description", "Ref of the editable node from the latest screen_observe on the same target screen; supports any Unicode text.")
        }
        putJsonObject("fieldText") {
            put("type", "string")
            put("description", "Visible text of the editable node when no ref is known; requires accessibility.")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("text")) })
}

private val keyParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("key") {
            put("type", "string")
            put("description", "Key name: ${SCREEN_KEYS.joinToString(", ")}")
            putJsonArray("enum") { SCREEN_KEYS.forEach { add(JsonPrimitive(it)) } }
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("key")) })
}

private val waitParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("forPackage") {
            put("type", "string")
            put("description", "Wait until this package owns the current window.")
        }
        putJsonObject("forText") {
            put("type", "string")
            put("description", "Wait until this text or content description appears on screen.")
        }
        putJsonObject("forTextGone") {
            put("type", "string")
            put("description", "Wait until this text or content description disappears from the screen.")
        }
        putJsonObject("timeoutMs") {
            put("type", "integer")
            put(
                "description",
                "Maximum wait in milliseconds, $MIN_WAIT_MILLIS-$MAX_WAIT_MILLIS. Defaults to $DEFAULT_WAIT_MILLIS.",
            )
        }
    }
}

private val appParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("action") {
            put("type", "string")
            put(
                "description",
                "list returns installed apps that can be opened; launch opens packageName on the target screen.",
            )
            putJsonArray("enum") {
                add(JsonPrimitive("list"))
                add(JsonPrimitive("launch"))
            }
        }
        putJsonObject("packageName") {
            put("type", "string")
            put(
                "description",
                "Android package name of the app to open, for example com.android.settings. " +
                    "Required for launch; use action=list to find packages.",
            )
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

private fun JsonObjectBuilder.putTargetProperty() {
    putJsonObject("target") {
        put("type", "string")
        put(
            "description",
            "Which screen to operate: main is the phone screen; virtual is the background virtual screen " +
                "started with virtual_screen(action=start). Defaults to main.",
        )
        putJsonArray("enum") {
            add(JsonPrimitive(ScreenTarget.MAIN.id))
            add(JsonPrimitive(ScreenTarget.VIRTUAL.id))
        }
    }
}

private fun statusTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_status",
    description = "Check what works on the main and virtual screen right now: reading nodes, tapping, scrolling, swiping, typing, keys and screenshots. Use it when unsure which action is currently possible.",
    parameters = statusParameters,
    label = "screen_status",
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        screenTool { textResult(statusJson(controller.status()).toString()) }
    },
)

private fun observeTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_observe",
    description = "Read a screen snapshot with refs, class, text, desc, bounds and flags. Use it to inspect the current screen or find a node by one of text, desc, resourceId or class; the result reports count, total, scanned and truncated.",
    parameters = observeParameters,
    label = "screen_observe",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val (maxDepth, maxNodes) = observeLimitsOf(args)
            val expectedPackage = args.stringOrNull("packageName")?.trim()?.takeIf { it.isNotEmpty() }
            val filters = parseObserveFilters(args)
            val limit = if (filters.isEmpty()) DEFAULT_FIND_LIMIT else filterLimitOf(args)
            val observed = controller.observe(target, maxDepth, maxNodes, expectedPackage)
            textResult(observeJson(observed, filters, limit).toString())
        }
    },
)

private fun screenshotTool(
    controller: ScreenController,
    screenshotDir: File,
    linuxScreenshotDir: String,
): CodingTool = CodingTool(
    name = "screen_screenshot",
    description = "Save a screen as a PNG and report its /gui path, host path, width and height. Use it when visual layout matters and the node tree cannot answer the question.",
    parameters = screenshotParameters,
    label = "screen_screenshot",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val capture = controller.screenshot(target)
            if (capture.png.isEmpty()) throw IllegalStateException("截图内容为空，请稍后重试")
            if (!screenshotDir.isDirectory && !screenshotDir.mkdirs()) {
                throw IllegalStateException("截图目录不可写：${screenshotDir.absolutePath}")
            }
            val fileName = "screen-${System.currentTimeMillis()}.png"
            val file = File(screenshotDir, fileName)
            file.writeBytes(capture.png)
            val base = linuxScreenshotDir.trimEnd('/').ifEmpty { SCREEN_DEFAULT_LINUX_SCREENSHOT_DIR }
            textResult(
                buildJsonObject {
                    put("target", target.id)
                    put("path", "$base/$fileName")
                    put("hostPath", file.absolutePath)
                    put("width", capture.width)
                    put("height", capture.height)
                    put("sizeBytes", capture.png.size)
                }.toString(),
            )
        }
    },
)

private fun tapTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_tap",
    description = "Tap or long-press a node or a point on a screen. Use it to activate, open or long-press a visible control; provide exactly one of ref, text, desc, resourceId, class, or both x and y.",
    parameters = tapParameters,
    label = "screen_tap",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val selector = parseScreenTapSelector(args)
            val durationMs = tapDurationOf(args)
            textResult(controller.tap(target, selector, durationMs))
        }
    },
)

private fun swipeTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_swipe",
    description = "Swipe between two points on a screen. Use it for gestures such as dismissing a card; coordinates are screen pixels and both points must lie inside the current screen.",
    parameters = swipeParameters,
    label = "screen_swipe",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val x1 = args.requiredInt("x1")
            val y1 = args.requiredInt("y1")
            val x2 = args.requiredInt("x2")
            val y2 = args.requiredInt("y2")
            if (listOf(x1, y1, x2, y2).any { it < 0 }) throw IllegalArgumentException("坐标不能为负数")
            val durationMs = swipeDurationOf(args)
            textResult(controller.swipe(target, x1, y1, x2, y2, durationMs))
        }
    },
)

private fun scrollTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_scroll",
    description = "Scroll a screen or a scrollable node in one direction. Use it to reveal content outside the current view; down or up reveals later or earlier content, right or left reveals horizontal content, with an optional number of steps.",
    parameters = scrollParameters,
    label = "screen_scroll",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val direction = parseScrollDirection(args)
            val amount = scrollAmountOf(args)
            val ref = args.stringOrNull("ref")?.trim()?.takeIf { it.isNotEmpty() }
            textResult(controller.scroll(target, direction, amount, ref?.let { GuiSelector.Ref(it) }))
        }
    },
)

private fun typeTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_type",
    description = "Type text into the focused field or a named editable field. Use it when a text field needs input; non-ASCII text needs accessibility plus a ref or fieldText, while ASCII-only text can go directly through wireless debugging (ADB).",
    parameters = typeParameters,
    label = "screen_type",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val text = parseScreenTypeText(args)
            val selector = parseScreenTextSelector(args)
            val outcome = controller.typeText(target, selector, text)
            textResult(
                buildJsonObject {
                    put("ok", true)
                    put("verified", outcome.verified)
                    put("text", outcome.text)
                    if (!outcome.verified) {
                        put("hint", "未验证写入结果：请重新 screen_observe 确认输入框内容")
                    }
                }.toString(),
            )
        }
    },
)

private fun keyTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_key",
    description = "Press a key on a screen: back, home, recents, notifications, enter, delete, tab, escape, space, arrow keys or volume keys. Use it for navigation and dismissal when tapping a node is not reliable.",
    parameters = keyParameters,
    label = "screen_key",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val key = parseScreenKey(args)
            textResult(controller.key(target, key))
        }
    },
)

private fun waitTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_wait",
    description = "Wait until a package or text appears on a screen, or until a text disappears, and report whether it happened before the timeout. Use it when waiting for a screen transition or a loading state to finish.",
    parameters = waitParameters,
    label = "screen_wait",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val packageName = args.stringOrNull("forPackage")?.trim()?.takeIf { it.isNotEmpty() }
            val text = args.stringOrNull("forText")?.trim()?.takeIf { it.isNotEmpty() }
            val textGone = args.stringOrNull("forTextGone")?.trim()?.takeIf { it.isNotEmpty() }
            if (packageName == null && text == null && textGone == null) {
                throw IllegalArgumentException("需要提供 forPackage、forText 或 forTextGone 至少一个条件")
            }
            val timeoutMs = waitTimeoutOf(args)
            val startedAt = System.currentTimeMillis()
            val matched = controller.waitFor(target, packageName, text, textGone, timeoutMs)
            val elapsedMs = (System.currentTimeMillis() - startedAt).toInt()
            textResult(
                buildJsonObject {
                    put("ok", matched)
                    put("elapsedMs", elapsedMs)
                    if (!matched) {
                        put("reason", "timeout")
                        put("timeoutMs", timeoutMs)
                        put("hint", "等待超时：界面没有出现预期变化，请重新 screen_observe 判断当前状态")
                    }
                }.toString(),
            )
        }
    },
)

private fun appTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_app",
    description = "List installed apps that can be opened, or open one on the main or virtual screen by package name. Use it when the package name is unknown or an app should be opened; a launch reports whether the app reached the expected screen.",
    parameters = appParameters,
    label = "screen_app",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            when (parseScreenAppAction(args)) {
                ScreenAppAction.LIST -> {
                    val installed = controller.listApps()
                    textResult(
                        buildJsonObject {
                            put("target", target.id)
                            put("count", installed.size)
                            put(
                                "apps",
                                buildJsonArray {
                                    installed.forEach { app ->
                                        add(
                                            buildJsonObject {
                                                put("package", app.packageName)
                                                put("label", app.label)
                                                if (app.component.isNotEmpty()) put("component", app.component)
                                            },
                                        )
                                    }
                                },
                            )
                        }.toString(),
                    )
                }

                ScreenAppAction.LAUNCH -> {
                    val packageName = parseScreenLaunchPackage(args)
                    val report = controller.launch(target, packageName)
                    val onExpectedScreen = report.landedOnExpectedDisplay
                    textResult(
                        buildJsonObject {
                            put("ok", report.ok)
                            put("package", report.packageName)
                            put("message", report.message)
                            put("onExpectedScreen", onExpectedScreen)
                            when {
                                !report.ok -> put(
                                    "hint",
                                    "启动失败：请确认应用已安装且目标屏幕可用；需要时重新 screen_observe 查看当前页面",
                                )

                                !onExpectedScreen -> put(
                                    "hint",
                                    "应用已启动，但可能没有显示在目标屏幕：请重新 screen_observe 确认真实页面后再操作",
                                )
                            }
                        }.toString(),
                        isError = !report.ok,
                    )
                }
            }
        }
    },
)

internal fun statusJson(status: ScreenStatusSnapshot): JsonObject = buildJsonObject {
    val connected = status.accessibilityConnected
    val enabled = status.accessibilityEnabled
    val accessibilityState = when {
        connected -> "connected"
        enabled -> "rebinding"
        else -> "disabled"
    }
    put("accessibility", accessibilityState)
    when (accessibilityState) {
        "rebinding" -> put(
            "hint",
            "无障碍服务正在重新绑定：请等待几秒后重试；期间坐标点击、滑动与截图仍可用",
        )

        "disabled" -> put(
            "hint",
            "无障碍服务未开启：请到权限设置开启 DeepDeep 无障碍；开启后可读取节点、按节点操作与截图",
        )
    }
    val virtualInfo = status.virtual
    val virtualOnline = status.virtualState == "online" && virtualInfo != null
    val adb = status.adbAvailable
    val readNodes = connected || (adb && !virtualOnline)
    putJsonObject("main") {
        put("width", status.main.width)
        put("height", status.main.height)
        put("readNodes", readNodes)
        put("tap", when {
            readNodes -> "node"
            adb || connected -> "point"
            else -> "none"
        })
        put("scroll", adb || connected)
        put("swipe", adb)
        put("typeText", when {
            connected -> "unicode-with-field"
            adb -> "ascii"
            else -> "none"
        })
        put("key", when {
            adb -> "all"
            connected -> "system"
            else -> "none"
        })
        put("screenshot", connected)
    }
    putJsonObject("virtual") {
        put("state", status.virtualState)
        if (virtualOnline) {
            val info = virtualInfo!!
            put("width", info.width)
            put("height", info.height)
            put("readNodes", connected)
            put("tap", if (connected) "node" else "point")
            put("scroll", true)
            put("swipe", true)
            put("typeText", if (connected) "unicode-with-field" else "ascii")
            put("key", "all")
            put("screenshot", true)
        } else {
            put("readNodes", false)
            put("tap", "none")
            put("scroll", false)
            put("swipe", false)
            put("typeText", "none")
            put("key", "none")
            put("screenshot", false)
        }
        status.virtualTransportNotice?.takeIf { it.isNotBlank() }?.let { put("notice", it) }
        if (status.virtualState == "failed") {
            status.virtualFailure?.let { failure ->
                putJsonObject("failure") {
                    put("category", virtualFailureJsonCategory(failure.category))
                    put("message", failure.message)
                    put("hint", virtualFailureHint(failure.category))
                }
            }
        }
    }
}

internal fun observeJson(
    observed: ScreenObserveResult,
    filters: List<GuiSelector> = emptyList(),
    limit: Int = DEFAULT_FIND_LIMIT,
): JsonObject = buildJsonObject {
    observed.snapshot.packageName?.let { put("package", it) }
    observed.snapshot.windowTitle?.let { put("title", it) }
    val scanned = observed.snapshot.nodes
    val total = if (filters.isEmpty()) {
        scanned
    } else {
        scanned.filter { node -> GuiNodeTree.matchesAll(node, filters) }
    }
    val shown = if (filters.isEmpty()) total else total.take(limit)
    put("count", shown.size)
    put("total", total.size)
    put("scanned", scanned.size)
    put("truncated", observed.snapshot.truncated)
    put("filtered", observed.filteredNodes)
    put("nodes", buildJsonArray { shown.forEach { node -> add(GuiNodeTree.nodeJson(node)) } })
    if (filters.isNotEmpty() && total.isEmpty()) {
        put(
            "hint",
            "未命中：已检索 ${scanned.size} 个节点；text 只匹配可见文本、desc 只匹配内容描述，" +
                "可分别尝试或改用 resourceId/class 后重试",
        )
    }
}

internal fun virtualFailureJsonCategory(category: String): String = when (category) {
    VIRTUAL_FAILURE_TRANSPORT -> "connection"
    VIRTUAL_FAILURE_BACKEND -> "privilege"
    else -> "start"
}

internal fun virtualFailureHint(category: String): String = when (category) {
    VIRTUAL_FAILURE_TRANSPORT -> "连接中断后未能自动恢复：请重新 screen_app action=launch 启动应用，再 screen_observe 确认当前页面"
    VIRTUAL_FAILURE_BACKEND -> "无线调试（ADB）或 Root 当前不可用：请在权限设置里检查后重试"
    else -> "虚拟屏启动失败：请稍后重试"
}

private suspend fun screenTool(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "屏幕操作失败", isError = true)
}

private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("缺少参数 $name")

private fun JsonObject.requiredInt(name: String): Int =
    intOrNull(name) ?: throw IllegalArgumentException("缺少参数 $name")
