package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.ai.TextContent
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json

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

internal val MAIN_SCREEN_KEYS = SCREEN_KEYS + setOf("home", "recents", "notifications", "volume_up", "volume_down")

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
    mainController: ScreenController? = null,
    onVirtualActivity: () -> Unit = {},
): List<CodingTool> {
    val mainTools = mainController?.let { toolsForScreen(it, screenshotDir).associateBy { tool -> tool.name } }
    return toolsForScreen(controller, screenshotDir).map { virtualTool ->
        val properties = virtualTool.parameters.getValue("properties").jsonObject
        CodingTool(
            name = virtualTool.name,
            label = virtualTool.label,
            description = virtualTool.description,
            parameters = JsonObject(virtualTool.parameters + ("properties" to JsonObject(properties + ("target" to buildJsonObject {
                put("type", "string")
                put("enum", buildJsonArray { add(JsonPrimitive("virtual")); add(JsonPrimitive("main")) })
                put("default", "virtual")
            })))),
            promptGuidelines = screenGuidelines(virtualTool.name),
            execute = { id, args, signal, update ->
                signal?.throwIfAborted()
                screenTool(JsonObject(emptyMap())) {
                    val target = args["target"]?.let {
                        val primitive = it as? JsonPrimitive
                        require(primitive?.isString == true) { "target must be a virtual or main string" }
                        primitive.content.trim().lowercase()
                    } ?: "virtual"
                    require(target == "virtual" || target == "main") { "target must be virtual or main" }
                    if (target == "virtual") onVirtualActivity()
                    val delegate = if (target == "main") mainTools?.get(virtualTool.name)
                        ?: error("Main screen tools are not available; no other screen was operated") else virtualTool
                    val selected = if (virtualTool.name == "screen_status") mainTools?.get(virtualTool.name) ?: delegate else delegate
                    val result = coroutineScope {
                        val execution = async { selected.execute(id, JsonObject(args - "target"), signal, update) }
                        val unsubscribe = signal?.onAbort { execution.cancel() }
                        try {
                            execution.await().also { signal?.throwIfAborted() }
                        } catch (error: CancellationException) {
                            if (signal?.isAborted == true) throw RequestAbortedException(signal.reason)
                            throw error
                        } finally { unsubscribe?.invoke() }
                    }
                    val content = (result.content.singleOrNull() as? TextContent)?.text
                    val json = content?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                    if (json != null) result.content = listOf(TextContent(JsonObject(json + ("target" to JsonPrimitive(target))).toString()))
                    result
                }
            },
        )
    }
}

internal const val SCREEN_TARGET_GUIDELINE =
    "Screen tools default to the virtual screen; pass target=main explicitly for the phone display, and never switch screens after a failure."
internal const val SCREEN_CANCEL_GUIDELINE =
    "After a cancellation or timeout, observe the selected screen before retrying an action."
internal const val SCREEN_OBSERVE_GUIDELINE =
    "Observe the screen before node operations and verify the result afterward; refs keep resolving by node identity across ordinary operations, and only a restarted screen invalidates them."
internal const val SCREEN_LAUNCH_GUIDELINE =
    "After screen_launch, observe the target screen before interacting."

private fun screenGuidelines(name: String): List<String> = buildList {
    add(SCREEN_TARGET_GUIDELINE)
    add(SCREEN_CANCEL_GUIDELINE)
    if (name == "screen_observe") add(SCREEN_OBSERVE_GUIDELINE)
    if (name == "screen_launch") add(SCREEN_LAUNCH_GUIDELINE)
}

private fun toolsForScreen(controller: ScreenController, screenshotDir: File): List<CodingTool> = listOf(
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
        if (x == null || y == null) throw IllegalArgumentException("Point selection requires both x and y")
        if (named.isNotEmpty()) {
            throw IllegalArgumentException("ref, text, desc, resourceId and class cannot be combined with x and y")
        }
        if (x < 0 || y < 0) throw IllegalArgumentException("Coordinates x and y must not be negative")
        return GuiSelector.Point(x, y)
    }
    if (named.size > 1) {
        throw IllegalArgumentException("Provide only one of ref, text, desc, resourceId or class")
    }
    return when {
        ref != null -> GuiSelector.Ref(ref)
        text != null -> GuiSelector.Text(text)
        desc != null -> GuiSelector.Desc(desc)
        resourceId != null -> GuiSelector.ResourceId(resourceId)
        className != null -> GuiSelector.ClassName(className)
        else -> throw IllegalArgumentException("Provide one of ref, text, desc, resourceId, class or x and y as the target")
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
        throw IllegalArgumentException("Provide only one of ref or fieldText")
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
        else -> throw IllegalArgumentException("Unsupported direction; available: up, down, left, right")
    }

internal fun parseScreenKey(args: JsonObject): String {
    val name = args.stringOrNull("key")?.trim()?.lowercase().orEmpty()
    if (name.isEmpty()) throw IllegalArgumentException("Missing required parameter: key")
    if (name !in MAIN_SCREEN_KEYS) {
        throw IllegalArgumentException("Unsupported key: $name; available: ${MAIN_SCREEN_KEYS.joinToString(", ")}")
    }
    return name
}

internal fun parseScreenLaunchPackage(args: JsonObject): String {
    val packageName = args.stringOrNull("packageName")?.trim().orEmpty()
    if (packageName.isEmpty()) throw IllegalArgumentException("Missing required parameter: packageName")
    if (!PACKAGE_NAME.matches(packageName)) {
        throw IllegalArgumentException("packageName is not a valid Android package name: $packageName")
    }
    return packageName
}

internal fun parseScreenTypeText(args: JsonObject): String {
    val text = args.stringOrNull("text")
        ?: throw IllegalArgumentException("Missing required parameter: text")
    if (text.isEmpty()) throw IllegalArgumentException("text must not be empty")
    return text
}

internal fun tapDurationOf(args: JsonObject): Int {
    val duration = args.intOrNull("durationMs") ?: DEFAULT_TAP_MILLIS
    if (duration !in 0..MAX_TAP_MILLIS) {
        throw IllegalArgumentException("durationMs must be in 0..$MAX_TAP_MILLIS")
    }
    return duration
}

internal fun swipeDurationOf(args: JsonObject): Long {
    val duration = args.longOrNull("durationMs") ?: DEFAULT_SWIPE_MILLIS.toLong()
    if (duration !in MIN_SWIPE_MILLIS..MAX_SWIPE_MILLIS) {
        throw IllegalArgumentException("durationMs must be in $MIN_SWIPE_MILLIS..$MAX_SWIPE_MILLIS")
    }
    return duration
}

internal fun scrollAmountOf(args: JsonObject): Int {
    val amount = args.intOrNull("amount") ?: DEFAULT_SCROLL_AMOUNT
    if (amount !in 1..MAX_SCROLL_AMOUNT) {
        throw IllegalArgumentException("amount must be in 1..$MAX_SCROLL_AMOUNT")
    }
    return amount
}

internal fun waitTimeoutOf(args: JsonObject): Long {
    val timeout = args.intOrNull("timeoutMs") ?: DEFAULT_WAIT_MILLIS
    if (timeout !in MIN_WAIT_MILLIS..MAX_WAIT_MILLIS) {
        throw IllegalArgumentException("timeoutMs must be in $MIN_WAIT_MILLIS..$MAX_WAIT_MILLIS")
    }
    return timeout.toLong()
}

internal fun observeLimitOf(args: JsonObject): Int? {
    val limit = args.intOrNull("limit") ?: return null
    if (limit !in 1..50) {
        throw IllegalArgumentException("limit must be in 1..50")
    }
    return limit
}

internal fun observeLimitsOf(args: JsonObject): Pair<Int, Int> {
    val maxDepth = args.intOrNull("maxDepth") ?: SCREEN_DEFAULT_MAX_DEPTH
    val maxNodes = args.intOrNull("maxNodes") ?: GUI_MAX_NODES
    if (maxDepth !in 1..GUI_MAX_DEPTH) {
        throw IllegalArgumentException("maxDepth must be in 1..$GUI_MAX_DEPTH")
    }
    if (maxNodes !in 1..GUI_MAX_NODES) {
        throw IllegalArgumentException("maxNodes must be in 1..$GUI_MAX_NODES")
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
            put("description", "Optional package whose window to read; other windows on that screen are then ignored for node reads and an error lists the visible windows when it is absent.")
        }
        putJsonObject("text") {
            put("type", "string")
            put("description", "Keep only nodes whose visible text or content description contains this value, ignoring case, spaces and separators.")
        }
        putJsonObject("desc") {
            put("type", "string")
            put("description", "Keep only nodes whose content description contains this value, ignoring case, spaces and separators.")
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
            put("description", "Node ref from screen_observe on the same target screen; it is re-resolved by node identity when the node moves.")
        }
        putJsonObject("text") {
            put("type", "string")
            put("description", "Visible text or content description to match case-insensitively as a substring, ignoring spaces and separators.")
        }
        putJsonObject("desc") {
            put("type", "string")
            put("description", "Content description to match case-insensitively as a substring, ignoring spaces and separators.")
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
            put("description", "Ref of the editable node from screen_observe on the same target screen; supports any Unicode text.")
        }
        putJsonObject("fieldText") {
            put("type", "string")
            put("description", "Visible text or content description of the editable node when no ref is known; requires accessibility.")
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
            putJsonArray("enum") { MAIN_SCREEN_KEYS.forEach { add(JsonPrimitive(it)) } }
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
            put("description", "Android package name from screen_list_apps; opens on the selected screen.")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("packageName")) })
}

private fun statusTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_status",
    description = "Check screen state, recovery status and available operations. Main screen nodes, Unicode field input and screenshots require accessibility; virtual coordinate gestures and screenshots use its own connection.",
    parameters = statusParameters,
    label = "screen_status",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) { textResult(statusJson(controller.status()).toString()) }
    },
)

private fun observeTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_observe",
    description = "Read the selected screen as nodes. Filters combine with AND. Refs are re-resolved by node identity across ordinary operations; a restarted screen invalidates them. scanned counts visited nodes; complete=false means unsearched nodes remain, even when there are no matches.",
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
    description = "Save the selected screen as a PNG. Main screen capture requires accessibility and Android 11+. Protected or locked screens may refuse capture. Returns one absolute path usable by file tools, shell and present.",
    parameters = screenshotParameters,
    label = "screen_screenshot",
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        screenTool(args) {
            val capture = controller.screenshot()
            if (capture.png.isEmpty()) throw IllegalStateException("Screenshot content is empty; retry later")
            if (!screenshotDir.isDirectory && !screenshotDir.mkdirs()) {
                throw IllegalStateException("Screenshot directory is not writable: ${screenshotDir.absolutePath}")
            }
            val fileName = "screen-${java.util.UUID.randomUUID()}.png"
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
            if (listOf(x1, y1, x2, y2).any { it < 0 }) throw IllegalArgumentException("Coordinates must not be negative")
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
    description = "Type into the selected screen. Unicode text needs accessibility and a ref or fieldText. ASCII without a field uses the virtual helper or authorized main system input. verified reports field readback.",
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
                        put("hint", "Write not verified: call screen_observe again to check the field content")
                    }
                }.toString(),
            )
        }
    },
)

private fun keyTool(controller: ScreenController): CodingTool = CodingTool(
    name = "screen_key",
    description = "Press a screen key: back, enter, delete, tab, escape, space or arrows. Home, recents, notifications and volume keys are main-only. Availability depends on accessibility and authorized ADB/Root; inspect screen_status.",
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
                throw IllegalArgumentException("Provide at least one of forPackage, forText or forTextGone")
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
                        put("hint", "Wait timed out: the expected change did not appear; use screen_observe to check the current state before deciding the next step")
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
    description = "Open an app on the selected screen by package name. Virtual needs an online virtual screen. Refuses to move an app from another screen; reports whether launch was confirmed on the requested screen.",
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
                if (!report.ok || !report.landedOnExpectedDisplay) put("hint", "Check screen_status with the same target and the actual screen; call virtual_screen(action=start) only when target=virtual is offline, then decide whether to relaunch the app")
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
    status.main?.let { main ->
        putJsonObject("main") {
            put("width", main.width)
            put("height", main.height)
            put("readNodes", connected)
            put("tap", status.mainInputAvailable)
            put("swipe", status.mainInputAvailable)
            put("typeText", if (connected) "unicode-with-field" else if (status.mainInputAvailable) "ascii" else "none")
            put("screenshot", status.mainScreenshotAvailable)
            put("keys", buildJsonArray { status.mainKeys.forEach { add(JsonPrimitive(it)) } })
        }
    }
    when (accessibilityState) {
        "disconnected" -> put(
            "hint",
            "Accessibility is enabled but the service is not connected: wait briefly and retry, or toggle it off and on in system settings if it stays disconnected. Virtual screen coordinate gestures and screenshots still work while it is online",
        )

        "disabled" -> put(
            "hint",
            "Accessibility service is off: enable DeepDeep accessibility in settings; once enabled, nodes can be read and operated by ref",
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
                    put("hint", if (status.reconnecting) "Reconnecting; check screen_status shortly" else virtualFailureHint(failure.category))
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
    val windows = observed.snapshot.windows
    if (windows.size > 1) {
        putJsonArray("windows") { windows.forEach { window -> add(windowJson(window)) } }
    }
    val hints = buildList {
        if (observed.snapshot.truncated) {
            add("Scan was not exhaustive, so absence is not proof; raise maxDepth/maxNodes, and filters only apply to scanned nodes")
        }
        if (shown.size < total.size) add("Result count reached limit; raise or omit limit")
        if (filters.isNotEmpty() && total.isEmpty()) {
            add(
                "No match: scanned ${observed.snapshot.scanned} nodes; text matches visible text or content descriptions, " +
                    "so try a shorter value or use resourceId/class, then retry",
            )
        }
    }
    if (hints.isNotEmpty()) put("hint", hints.joinToString("; "))
}

internal fun windowJson(window: GuiWindowInfo): JsonObject = buildJsonObject {
    window.packageName?.let { put("package", it) }
    window.title?.let { put("title", it) }
    put("type", windowKindName(window.kind))
    if (window.focused) put("focused", true)
    if (window.active) put("active", true)
    if (window.selected) put("selected", true)
    if (window.bounds.width > 0 && window.bounds.height > 0) put("bounds", window.bounds.format())
}

internal fun virtualFailureJsonCategory(category: String): String = when (category) {
    VIRTUAL_FAILURE_TRANSPORT -> "connection"
    VIRTUAL_FAILURE_BACKEND -> "privilege"
    else -> "start"
}

internal fun virtualFailureHint(category: String): String = when (category) {
    VIRTUAL_FAILURE_TRANSPORT -> "Connection lost and did not recover automatically; call virtual_screen(action=start), then screen_launch and screen_observe to confirm the current screen"
    VIRTUAL_FAILURE_BACKEND -> "Wireless debugging (ADB) or Root is currently unavailable; check privileges in settings and retry"
    else -> "Virtual screen failed to start; retry later"
}

private suspend fun screenTool(args: JsonObject, block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "Screen operation failed", isError = true)
}

private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Missing required parameter: $name")

private fun JsonObject.requiredInt(name: String): Int =
    intOrNull(name) ?: throw IllegalArgumentException("Missing or invalid required parameter: $name")
