package io.github.whywood.deepdeep.screen

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import io.github.whywood.deepdeep.plugin.gui.GUI_DEFAULT_MAX_DEPTH
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
private const val MAX_SCROLL_STEPS = 10

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
    "screen_find",
    "screen_screenshot",
    "screen_tap",
    "screen_swipe",
    "screen_scroll",
    "screen_type",
    "screen_key",
    "screen_wait",
    "screen_launch",
    "screen_list_apps",
)

fun screenTools(
    controller: ScreenController,
    screenshotDir: File,
    linuxScreenshotDir: String = SCREEN_DEFAULT_LINUX_SCREENSHOT_DIR,
): List<CodingTool> = listOf(
    statusTool(controller),
    observeTool(controller),
    findTool(controller),
    screenshotTool(controller, screenshotDir, linuxScreenshotDir),
    tapTool(controller),
    swipeTool(controller),
    scrollTool(controller),
    typeTool(controller),
    keyTool(controller),
    waitTool(controller),
    launchTool(controller),
    listAppsTool(controller),
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

internal fun parseScreenFindSelectors(args: JsonObject): List<GuiSelector> {
    val selectors = buildList {
        args.stringOrNull("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.Text(it)) }
        args.stringOrNull("desc")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.Desc(it)) }
        args.stringOrNull("resourceId")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.ResourceId(it)) }
        args.stringOrNull("class")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.ClassName(it)) }
    }
    if (selectors.isEmpty()) {
        throw IllegalArgumentException("需要提供 text、desc、resourceId、class 至少一个条件")
    }
    return selectors
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

internal fun findLimitOf(args: JsonObject): Int {
    val limit = args.intOrNull("limit") ?: DEFAULT_FIND_LIMIT
    if (limit !in 1..MAX_FIND_LIMIT) {
        throw IllegalArgumentException("limit 需要在 1..$MAX_FIND_LIMIT 之间")
    }
    return limit
}

internal fun observeLimitsOf(args: JsonObject): Pair<Int, Int> {
    val maxDepth = args.intOrNull("maxDepth") ?: GUI_DEFAULT_MAX_DEPTH
    val maxNodes = args.intOrNull("maxNodes") ?: GUI_MAX_NODES
    if (maxDepth !in 1..GUI_MAX_DEPTH) {
        throw IllegalArgumentException("maxDepth 需要在 1..$GUI_MAX_DEPTH 之间")
    }
    if (maxNodes !in 1..GUI_MAX_NODES) {
        throw IllegalArgumentException("maxNodes 需要在 1..$GUI_MAX_NODES 之间")
    }
    return maxDepth to maxNodes
}

internal fun sweepDistance(amount: Int): Int = amount.coerceIn(1, MAX_SCROLL_STEPS)

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
            put("description", "Maximum node depth to include, 1-$GUI_MAX_DEPTH. Defaults to $GUI_DEFAULT_MAX_DEPTH.")
        }
        putJsonObject("maxNodes") {
            put("type", "integer")
            put("description", "Maximum number of nodes to include, 1-$GUI_MAX_NODES. Defaults to $GUI_MAX_NODES.")
        }
        putJsonObject("packageName") {
            put("type", "string")
            put("description", "Optional expected package of the current window; the call fails when another app is in front.")
        }
    }
}

private val findParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
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
            put("description", "Node ref from the latest screen_observe or screen_find on the same target screen.")
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
            put("description", "Screen x coordinate in pixels; requires y.")
        }
        putJsonObject("y") {
            put("type", "integer")
            put("description", "Screen y coordinate in pixels; requires x.")
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
            put("description", "Optional ref of a scrollable node; defaults to the whole screen.")
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
            put("description", "Text to type. Without ref/fieldText only ASCII is supported on the virtual screen and on the main screen without accessibility.")
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

private val launchParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
        putJsonObject("packageName") {
            put("type", "string")
            put("description", "Android package name of the app to open, for example com.android.settings. Use screen_list_apps to find packages.")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("packageName")) })
}

private val listAppsParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putTargetProperty()
    }
}

private fun JsonObjectBuilder.putTargetProperty() {
    putJsonObject("target") {
        put("type", "string")
        put(
            "description",
            "Which screen to operate: main is the phone screen, virtual is the background virtual screen " +
                "started by virtual_screen_start. Defaults to main.",
        )
        putJsonArray("enum") {
            add(JsonPrimitive(ScreenTarget.MAIN.id))
            add(JsonPrimitive(ScreenTarget.VIRTUAL.id))
        }
    }
}

private fun statusTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_status",
    description = "Report screen backends and state: accessibility connection, ADB availability, main display size, and the virtual screen display id, resolution, privilege backend and helper uid.",
    parameters = statusParameters,
    label = "screen_status",
    promptSnippet = "Check screen backends and the virtual screen state",
    promptGuidelines = listOf(
        "Call screen_status when unsure which backends are available before using screen_* tools.",
        "target=main works with accessibility only; without it main observation and coordinate input fall back to ADB wireless debugging.",
    ),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        screenTool { textResult(statusJson(controller.status()).toString()) }
    },
)

private fun observeTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_observe",
    description = "Read a trimmed snapshot of a screen: package, window title and a node list with refs, text, bounds and flags. target=main uses accessibility, falling back to uiautomator XML over ADB.",
    parameters = observeParameters,
    label = "screen_observe",
    promptSnippet = "Observe a screen as a node snapshot",
    promptGuidelines = listOf(
        "Call screen_observe before acting and again after every action that changes the screen; refs from an older observe are invalid.",
        "On the virtual screen, node reading requires the accessibility service; screenshot/tap-by-coordinate still work without it.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val (maxDepth, maxNodes) = observeLimitsOf(args)
            val expectedPackage = args.stringOrNull("packageName")?.trim()?.takeIf { it.isNotEmpty() }
            val observed = controller.observe(target, maxDepth, maxNodes, expectedPackage)
            textResult(observeJson(observed).toString())
        }
    },
)

private fun findTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_find",
    description = "Find nodes on a screen by text, desc, resourceId and/or class, and return their refs for later actions.",
    parameters = findParameters,
    label = "screen_find",
    promptSnippet = "Find screen nodes by text, desc, resourceId or class",
    promptGuidelines = listOf(
        "Use screen_find when the target text, desc, resourceId or class is known; the returned refs stay valid until the next observe on that screen.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val selectors = parseScreenFindSelectors(args)
            val limit = findLimitOf(args)
            val found = controller.find(target, selectors, limit)
            textResult(
                buildJsonObject {
                    put("backend", found.backend)
                    put("count", found.matches.size)
                    put("total", found.snapshot.nodes.count { node ->
                        selectors.all { selector -> GuiNodeTree.matches(node, selector) }
                    })
                    put("hasMore", found.snapshot.nodes.size == GUI_MAX_NODES || found.matches.size >= limit)
                    put("truncated", found.snapshot.truncated)
                    put("nodes", buildJsonArray { found.matches.forEach { add(GuiNodeTree.nodeJson(it)) } })
                }.toString(),
            )
        }
    },
)

private fun screenshotTool(
    controller: ScreenController,
    screenshotDir: File,
    linuxScreenshotDir: String,
): CodingTool = CodingTool(
    name = "screen_screenshot",
    description = "Capture a screen as a PNG file and return its host path, /gui path, width, height, target and backend. target=main needs accessibility; target=virtual uses the virtual screen helper.",
    parameters = screenshotParameters,
    label = "screen_screenshot",
    promptSnippet = "Capture a screen as a PNG",
    promptGuidelines = listOf(
        "Use screen_screenshot only when visual layout matters; screenshots can contain sensitive data, so never share them or read secrets from them.",
        "target=main is not captured on Android 10, while locked, or when an app sets FLAG_SECURE; ask the user instead of retrying in a loop.",
    ),
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
                    put("backend", capture.backend)
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
    description = "Tap or long-press a node or coordinate on a screen by ref, text, desc, resourceId, class or x/y.",
    parameters = tapParameters,
    label = "screen_tap",
    promptSnippet = "Tap or long-press a screen node",
    promptGuidelines = listOf(
        "Ask the user for confirmation before tapping anything destructive such as delete, send, pay or authorize.",
        "Use ref from the latest screen_observe on the same target screen; without accessibility only x/y coordinates work on the virtual screen.",
    ),
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
    description = "Swipe between two coordinates on a screen. target=main needs ADB wireless debugging; target=virtual uses the virtual screen helper.",
    parameters = swipeParameters,
    label = "screen_swipe",
    promptSnippet = "Swipe on a screen",
    promptGuidelines = listOf(
        "Prefer screen_scroll for scrolling content; use screen_swipe for gestures such as dismissing cards.",
    ),
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
    description = "Scroll a screen or a scrollable node in the given direction. Works on the main screen with ADB or accessibility, and on the virtual screen through the helper.",
    parameters = scrollParameters,
    label = "screen_scroll",
    promptSnippet = "Scroll a screen in a direction",
    promptGuidelines = listOf("Call screen_observe again after scrolling to confirm the new screen content."),
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
    description = "Type text into the focused field or an editable node. Any Unicode text needs accessibility and a ref; without them only ASCII can be injected through ADB or the virtual screen helper.",
    parameters = typeParameters,
    label = "screen_type",
    promptSnippet = "Type text into a screen field",
    promptGuidelines = listOf(
        "Never type passwords or verification codes; ask the user to enter sensitive input themselves.",
        "Check verified in the result; when it is false, re-observe the field or ask the user instead of retrying blindly.",
    ),
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
                    put("backend", outcome.backend)
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
    description = "Press a key on a screen: ${SCREEN_KEYS.joinToString(", ")}. target=main uses accessibility for system keys when available, otherwise ADB key events; target=virtual uses the helper.",
    parameters = keyParameters,
    label = "screen_key",
    promptSnippet = "Press a key on a screen",
    promptGuidelines = listOf(
        "Avoid back or home when it would abandon unsaved user input without confirmation.",
    ),
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
    description = "Wait until a screen shows a package or text, or until a text disappears. Conditions combine.",
    parameters = waitParameters,
    label = "screen_wait",
    promptSnippet = "Wait for a screen, text to appear or text to disappear",
    promptGuidelines = listOf(
        "Combine forPackage, forText and forTextGone to confirm screen transitions.",
        "A screen_wait result with ok:false is not an error; re-observe the screen instead of retrying the same action blindly.",
    ),
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

private fun launchTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_launch",
    description = "Open an installed app on the main or virtual screen by package name. For target=virtual the response includes displayId, taskDisplayId and landedOnMain diagnostics.",
    parameters = launchParameters,
    label = "screen_launch",
    promptSnippet = "Open an app on the main or virtual screen",
    promptGuidelines = listOf(
        "Use screen_list_apps to find package names.",
        "When target=virtual and landedOnMain is true, the task did not land on the virtual display; check the diagnostics instead of repeating blindly.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val packageName = parseScreenLaunchPackage(args)
            val report = controller.launch(target, packageName)
            textResult(
                buildJsonObject {
                    put("ok", report.ok)
                    put("target", report.target.id)
                    put("package", report.packageName)
                    put("backend", report.backend)
                    if (report.displayId >= 0) put("displayId", report.displayId)
                    if (report.taskDisplayId >= 0) put("taskDisplayId", report.taskDisplayId)
                    put("landedOnMain", report.landedOnMain)
                    put("landedOnExpectedDisplay", report.landedOnExpectedDisplay)
                    put("message", report.message)
                    if (report.ok && !report.landedOnExpectedDisplay) {
                        put("hint", "任务落屏与预期不一致：请结合 taskDisplayId 检查虚拟屏显示、IME 与首帧")
                    }
                }.toString(),
                isError = !report.ok,
            )
        }
    },
)

private fun listAppsTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_list_apps",
    description = "List installed apps that can be launched, with package name, label and launcher component.",
    parameters = listAppsParameters,
    label = "screen_list_apps",
    promptSnippet = "List launchable installed apps",
    promptGuidelines = listOf("Call screen_list_apps before screen_launch when the package name is unknown."),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool {
            val target = targetOf(args)
            val apps = controller.listApps()
            textResult(
                buildJsonObject {
                    put("target", target.id)
                    put("count", apps.size)
                    put(
                        "apps",
                        buildJsonArray {
                            apps.forEach { app ->
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
    },
)

internal fun statusJson(status: ScreenStatusSnapshot): JsonObject = buildJsonObject {
    putJsonObject("accessibility") {
        put("connected", status.accessibilityConnected)
        put("enabled", status.accessibilityEnabled)
    }
    putJsonObject("adb") {
        put("available", status.adbAvailable)
    }
    putJsonObject("main") {
        put("displayId", status.main.displayId)
        put("width", status.main.width)
        put("height", status.main.height)
        put("backend", status.main.backend)
    }
    putJsonObject("virtual") {
        put("state", status.virtualState)
        status.virtual?.let { info ->
            put("displayId", info.displayId)
            put("width", info.width)
            put("height", info.height)
            put("backend", info.backend)
        }
        status.virtualBackend?.let { put("privilegeBackend", it) }
        status.virtualUid?.let { put("uid", it) }
    }
}

internal fun observeJson(observed: ScreenObserveResult): JsonObject = buildJsonObject {
    put("backend", observed.backend)
    observed.snapshot.packageName?.let { put("package", it) }
    observed.snapshot.windowTitle?.let { put("title", it) }
    put("count", observed.snapshot.nodes.size)
    put("truncated", observed.snapshot.truncated)
    put("nodes", buildJsonArray { observed.snapshot.nodes.forEach { add(GuiNodeTree.nodeJson(it)) } })
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
