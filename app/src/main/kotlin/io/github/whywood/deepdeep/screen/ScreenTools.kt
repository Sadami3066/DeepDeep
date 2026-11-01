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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

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

internal val SCREEN_KEYS = setOf(
    "back",
    "enter",
    "delete",
    "tab",
    "escape",
    "space",
    "arrow_up",
    "arrow_down",
    "arrow_left",
    "arrow_right",
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
    "screen_list_apps",
    "screen_launch",
)

fun screenTools(
    controller: ScreenController,
    screenshotDir: File,
): List<CodingTool> = listOf(
    statusTool(controller),
    observeTool(controller),
    screenshotTool(controller, screenshotDir),
    tapTool(controller),
    swipeTool(controller),
    scrollTool(controller),
    typeTool(controller),
    keyTool(controller),
    waitTool(controller),
    listAppsTool(controller),
    launchTool(controller),
)

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

internal fun observeLimitOf(args: JsonObject): Int? {
    val limit = args.intOrNull("limit") ?: return null
    if (limit !in 1..50) {
        throw IllegalArgumentException("limit 需要在 1..50 之间")
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

private val statusParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private val observeParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
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
            put("description", "Maximum nodes to return, 1-50. Omit to return all matches.")
        }
    }
}

private val screenshotParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
    }
}

private val tapParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
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
        putJsonObject("forPackage") {
            put("type", "string")
            put("description", "Wait until this package owns the current window.")
        }
        putJsonObject("forText") {
            put("type", "string")
            put("description", "Wait until this text appears in a node, a content description or the window title.")
        }
        putJsonObject("forTextGone") {
            put("type", "string")
            put("description", "Wait until this text is gone from nodes, content descriptions and the window title.")
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

private val launchParameters = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("packageName") {
            put("type", "string")
            put("description", "Android package name from screen_list_apps; opens only on the running virtual screen.")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("packageName")) })
}

private fun statusTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_status",
    description = "Check virtual screen state, recovery status and available operations. Nodes and Unicode field input require accessibility; coordinate gestures and screenshots require only the virtual screen.",
    parameters = statusParameters,
    label = "screen_status",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) { textResult(statusJson(controller.status()).toString()) }
    },
)

private fun observeTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_observe",
    description = "Read the virtual screen as nodes. Filters combine with AND. Refs belong to this screen instance and snapshot; observing again invalidates previous refs. scanned counts visited nodes; complete=false means unsearched nodes remain, even when there are no matches.",
    parameters = observeParameters,
    label = "screen_observe",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            val (maxDepth, maxNodes) = observeLimitsOf(args)
            val expectedPackage = args.stringOrNull("packageName")?.trim()?.takeIf { it.isNotEmpty() }
            val filters = parseObserveFilters(args)
            val limit = observeLimitOf(args)
            val observed = controller.observe(maxDepth, maxNodes, expectedPackage)
            textResult(observeJson(observed, filters, limit).toString())
        }
    },
)

private fun screenshotTool(
    controller: ScreenController,
    screenshotDir: File,
): CodingTool = CodingTool(
    name = "screen_screenshot",
    description = "Save the virtual screen as a PNG. Returns one absolute path usable by file tools, shell commands and present, plus width, height and size.",
    parameters = screenshotParameters,
    label = "screen_screenshot",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            val capture = controller.screenshot()
            if (capture.png.isEmpty()) throw IllegalStateException("截图内容为空，请稍后重试")
            if (!screenshotDir.isDirectory && !screenshotDir.mkdirs()) {
                throw IllegalStateException("截图目录不可写：${screenshotDir.absolutePath}")
            }
            val fileName = "screen-${System.currentTimeMillis()}.png"
            val file = File(screenshotDir, fileName)
            file.writeBytes(capture.png)
            textResult(
                buildJsonObject {
                    put("path", file.absolutePath)
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
        screenTool(args) {
            val selector = parseScreenTapSelector(args)
            val durationMs = tapDurationOf(args)
            textResult(controller.tap(selector, durationMs))
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
        screenTool(args) {
            val x1 = args.requiredInt("x1")
            val y1 = args.requiredInt("y1")
            val x2 = args.requiredInt("x2")
            val y2 = args.requiredInt("y2")
            if (listOf(x1, y1, x2, y2).any { it < 0 }) throw IllegalArgumentException("坐标不能为负数")
            val durationMs = swipeDurationOf(args)
            textResult(controller.swipe(x1, y1, x2, y2, durationMs))
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
        screenTool(args) {
            val direction = parseScrollDirection(args)
            val amount = scrollAmountOf(args)
            val ref = args.stringOrNull("ref")?.trim()?.takeIf { it.isNotEmpty() }
            textResult(controller.scroll(direction, amount, ref?.let { GuiSelector.Ref(it) }))
        }
    },
)

private fun typeTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_type",
    description = "Type text into the focused field or a named editable field. Use it when a text field needs input; non-ASCII text needs accessibility plus a ref or fieldText, while ASCII-only text can go directly through the virtual screen helper.",
    parameters = typeParameters,
    label = "screen_type",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            val text = parseScreenTypeText(args)
            val selector = parseScreenTextSelector(args)
            val outcome = controller.typeText(selector, text)
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
    description = "Press a supported virtual screen key: back, enter, delete, tab, escape, space or arrow keys.",
    parameters = keyParameters,
    label = "screen_key",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            val key = parseScreenKey(args)
            textResult(controller.key(key))
        }
    },
)

private fun waitTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_wait",
    description = "Wait until a package or text appears on a screen, or a text disappears, and report whether it happened before the timeout with the current window. Use it when waiting for a screen transition or loading state to finish.",
    parameters = waitParameters,
    label = "screen_wait",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            val packageName = args.stringOrNull("forPackage")?.trim()?.takeIf { it.isNotEmpty() }
            val text = args.stringOrNull("forText")?.trim()?.takeIf { it.isNotEmpty() }
            val textGone = args.stringOrNull("forTextGone")?.trim()?.takeIf { it.isNotEmpty() }
            if (packageName == null && text == null && textGone == null) {
                throw IllegalArgumentException("需要提供 forPackage、forText 或 forTextGone 至少一个条件")
            }
            val timeoutMs = waitTimeoutOf(args)
            val startedAt = System.currentTimeMillis()
            val outcome = controller.waitFor(packageName, text, textGone, timeoutMs)
            val elapsedMs = (System.currentTimeMillis() - startedAt).toInt()
            textResult(
                buildJsonObject {
                    put("ok", outcome.matched)
                    put("elapsedMs", elapsedMs)
                    put("timeoutMs", timeoutMs)
                    putJsonObject("current") {
                        outcome.packageName?.let { put("package", it) }
                        outcome.windowTitle?.let { put("title", it) }
                    }
                    if (!outcome.matched) {
                        put("reason", "timeout")
                        put("hint", "等待超时：界面没有出现预期变化，请用 screen_observe 判断当前状态后再决定下一步")
                    }
                }.toString(),
            )
        }
    },
)

private fun listAppsTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_list_apps",
    description = "List installed apps that can be opened. Does not require a running virtual screen.",
    parameters = statusParameters,
    label = "screen_list_apps",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            require(args.isEmpty()) { "screen_list_apps does not accept arguments" }
            val apps = controller.listApps()
            textResult(buildJsonObject {
                put("count", apps.size)
                putJsonArray("apps") {
                    apps.forEach { app -> add(buildJsonObject {
                        put("package", app.packageName)
                        put("label", app.label)
                        if (app.component.isNotEmpty()) put("component", app.component)
                    }) }
                }
            }.toString())
        }
    },
)

private fun launchTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_launch",
    description = "Open an app on the running virtual screen by package name. Fails if it is in use on the phone screen; never moves that app or falls back to the phone screen.",
    parameters = launchParameters,
    label = "screen_launch",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            require(args.keys.all { it == "packageName" }) { "screen_launch only accepts packageName" }
            val report = controller.launch(parseScreenLaunchPackage(args))
            textResult(buildJsonObject {
                put("ok", report.ok)
                put("package", report.packageName)
                put("message", report.message)
                put("onExpectedScreen", report.landedOnExpectedDisplay)
                if (!report.ok || !report.landedOnExpectedDisplay) put("hint", "请检查 screen_status；屏幕离线时先 virtual_screen(action=start)，再 screen_launch")
            }.toString(), isError = !report.ok || !report.landedOnExpectedDisplay)
        }
    },
)

internal fun statusJson(status: ScreenStatusSnapshot): JsonObject = buildJsonObject {
    val connected = status.accessibilityConnected
    val enabled = status.accessibilityEnabled
    val accessibilityState = when {
        connected -> "connected"
        enabled -> "disconnected"
        else -> "disabled"
    }
    put("accessibility", accessibilityState)
    put("reconnecting", status.reconnecting)
    when (accessibilityState) {
        "disconnected" -> put(
            "hint",
            "无障碍开关已开启但服务未连接：先稍等重试，仍未连接时请到系统设置关闭再开启；虚拟屏在线时坐标操作与截图仍可用",
        )

        "disabled" -> put(
            "hint",
            "无障碍服务未开启：请到权限设置开启 DeepDeep 无障碍；开启后可读取节点与按节点操作",
        )
    }
    val virtualInfo = status.virtual
    val virtualOnline = status.virtualState == "online" && virtualInfo != null
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
            put("keys", buildJsonArray { SCREEN_KEYS.forEach { add(JsonPrimitive(it)) } })
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
                    put("hint", if (status.reconnecting) "正在重新连接，请稍后检查 screen_status" else virtualFailureHint(failure.category))
                }
            }
        }
    }
}

internal fun observeJson(
    observed: ScreenObserveResult,
    filters: List<GuiSelector> = emptyList(),
    limit: Int? = null,
): JsonObject = buildJsonObject {
    observed.snapshot.packageName?.let { put("package", it) }
    observed.snapshot.windowTitle?.let { put("title", it) }
    val observedNodes = observed.snapshot.nodes
    val total = if (filters.isEmpty()) {
        observedNodes
    } else {
        observedNodes.filter { node -> GuiNodeTree.matchesAll(node, filters) }
    }
    val shown = if (limit == null) total else total.take(limit)
    put("count", shown.size)
    if (!observed.snapshot.truncated) put("total", total.size)
    put("complete", !observed.snapshot.truncated)
    put("scanned", observed.snapshot.scanned)
    put("truncated", observed.snapshot.truncated || shown.size < total.size)
    put("filtered", observed.filteredNodes)
    put("nodes", buildJsonArray { shown.forEach { node -> add(GuiNodeTree.nodeJson(node)) } })
    val hints = buildList {
        if (observed.snapshot.truncated) {
            add("扫描未穷尽，不能据此断言目标不存在；可提高 maxDepth/maxNodes，过滤条件只筛选已扫描节点")
        }
        if (shown.size < total.size) add("返回数量达到 limit；可提高或省略 limit")
        if (filters.isNotEmpty() && total.isEmpty()) {
            add(
                "未命中：已检索 ${observed.snapshot.scanned} 个节点；text 只匹配可见文本、desc 只匹配内容描述，" +
                    "可分别尝试或改用 resourceId/class 后重试",
            )
        }
    }
    if (hints.isNotEmpty()) put("hint", hints.joinToString("；"))
}

internal fun virtualFailureJsonCategory(category: String): String = when (category) {
    VIRTUAL_FAILURE_TRANSPORT -> "connection"
    VIRTUAL_FAILURE_BACKEND -> "privilege"
    else -> "start"
}

internal fun virtualFailureHint(category: String): String = when (category) {
    VIRTUAL_FAILURE_TRANSPORT -> "连接中断后未能自动恢复：请先 virtual_screen(action=start)，再 screen_launch 并 screen_observe 确认当前页面"
    VIRTUAL_FAILURE_BACKEND -> "无线调试（ADB）或 Root 当前不可用：请在权限设置里检查后重试"
    else -> "虚拟屏启动失败：请稍后重试"
}

private suspend fun screenTool(args: JsonObject, block: suspend () -> AgentToolResult): AgentToolResult = try {
    require(!args.containsKey("target")) { "target 已移除；GUI 仅支持虚拟屏。请删除 target 后重试" }
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
