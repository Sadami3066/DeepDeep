package io.github.whywood.deepdeep.plugin.gui

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
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
private const val DEFAULT_SCROLL_AMOUNT = 1
private const val MAX_SCROLL_AMOUNT = 10
private const val DEFAULT_WAIT_MILLIS = 5_000
private const val MIN_WAIT_MILLIS = 100
private const val MAX_WAIT_MILLIS = 60_000
const val GUI_DEFAULT_LINUX_SCREENSHOT_DIR = "/gui/screenshots"
private const val DEFAULT_FIND_LIMIT = 20
private const val MAX_FIND_LIMIT = 50
private const val DISCONNECTED_MESSAGE =
    "无障碍服务未开启：请在插件页打开无障碍设置并启用 DeepDeep 无障碍服务"

private val OBSERVE_GUIDELINES = listOf(
    "Call gui_observe before acting and again after every action that changes the screen; refs from an older observe are invalid.",
    "Prefer the ref from the latest gui_observe; fall back to text, desc, resourceId or class, and use x/y only when no node matches.",
    "Use gui_find when the target text, desc, resourceId or class is known and the full tree is large.",
    "Never tap system permission dialogs, accessibility authorization prompts or device admin screens.",
    "Never read passwords or verification codes from the screen; ask the user instead.",
    "Ask the user to confirm before destructive actions such as delete, send, pay or authorize; stop and ask when the screen looks unexpected.",
)

private val observeParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("maxDepth") {
            put("type", "integer")
            put("description", "Maximum node depth to include, 1-$GUI_MAX_DEPTH. Defaults to $GUI_DEFAULT_MAX_DEPTH.")
            put("minimum", 1)
            put("maximum", GUI_MAX_DEPTH)
        }
        putJsonObject("maxNodes") {
            put("type", "integer")
            put("description", "Maximum number of nodes to include, 1-$GUI_MAX_NODES. Defaults to $GUI_DEFAULT_MAX_NODES.")
            put("minimum", 1)
            put("maximum", GUI_MAX_NODES)
        }
        putJsonObject("packageName") {
            put("type", "string")
            put(
                "description",
                "Optional expected package of the current window; the call fails when another app is in front.",
            )
        }
    }
}

private val findParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
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
            put("minimum", 1)
            put("maximum", MAX_FIND_LIMIT)
        }
    }
}

private val tapParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("ref") {
            put("type", "string")
            put("description", "Node ref from the latest gui_observe or gui_find.")
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
            put("minimum", 0)
            put("maximum", MAX_TAP_MILLIS)
        }
    }
}

private val setTextParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("ref") {
            put("type", "string")
            put("description", "Ref of the editable node from the latest gui_observe.")
        }
        putJsonObject("text") {
            put("type", "string")
            put("description", "Visible text of the editable node to match case-insensitively.")
        }
        putJsonObject("setText") {
            put("type", "string")
            put("description", "Replacement text for the field; an empty string clears the field.")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("setText")) })
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
            put("description", "Optional ref of a scrollable node; defaults to the whole window.")
        }
        putJsonObject("amount") {
            put("type", "integer")
            put("description", "Number of swipe steps, 1-$MAX_SCROLL_AMOUNT. Defaults to $DEFAULT_SCROLL_AMOUNT.")
            put("minimum", 1)
            put("maximum", MAX_SCROLL_AMOUNT)
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("direction")) })
}

private val globalParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put("description", "Global action to perform.")
            putJsonArray("enum") {
                add(JsonPrimitive("back"))
                add(JsonPrimitive("home"))
                add(JsonPrimitive("recents"))
                add(JsonPrimitive("notifications"))
            }
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
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
            put("minimum", MIN_WAIT_MILLIS)
            put("maximum", MAX_WAIT_MILLIS)
        }
    }
}

private val screenshotParameters: JsonObject = buildJsonObject {
    put("type", "object")
}

fun guiTools(
    actions: GuiActions,
    screenshotDir: File,
    linuxScreenshotDir: String = GUI_DEFAULT_LINUX_SCREENSHOT_DIR,
): List<CodingTool> = listOf(
    guiObserveTool(actions),
    guiFindTool(actions),
    guiTapTool(actions),
    guiSetTextTool(actions),
    guiScrollTool(actions),
    guiGlobalTool(actions),
    guiWaitTool(actions),
    guiScreenshotTool(actions, screenshotDir, linuxScreenshotDir),
)

private fun guiObserveTool(actions: GuiActions): CodingTool = CodingTool(
    name = "gui_observe",
    description = "Read a trimmed snapshot of the current screen through the accessibility service: package, window title and a node list with refs, text, bounds and flags.",
    parameters = observeParameters,
    label = "gui_observe",
    promptSnippet = "Observe the current screen as a node snapshot",
    promptGuidelines = OBSERVE_GUIDELINES,
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val maxDepth = args.intOrNull("maxDepth") ?: GUI_DEFAULT_MAX_DEPTH
            val maxNodes = args.intOrNull("maxNodes") ?: GUI_DEFAULT_MAX_NODES
            if (maxDepth !in 1..GUI_MAX_DEPTH) {
                throw IllegalArgumentException("maxDepth 需要在 1..$GUI_MAX_DEPTH 之间")
            }
            if (maxNodes !in 1..GUI_MAX_NODES) {
                throw IllegalArgumentException("maxNodes 需要在 1..$GUI_MAX_NODES 之间")
            }
            val expectedPackage = args.stringOrNull("packageName")?.trim()?.takeIf { it.isNotEmpty() }
            val snapshot = actions.observe(maxDepth, maxNodes)
            if (expectedPackage != null && snapshot.packageName != null && snapshot.packageName != expectedPackage) {
                throw IllegalStateException(
                    "当前窗口属于 ${snapshot.packageName}，不是 $expectedPackage；请先切换到目标应用或省略 packageName",
                )
            }
            textResult(GuiNodeTree.render(snapshot))
        }
    },
)

private fun guiFindTool(actions: GuiActions): CodingTool = CodingTool(
    name = "gui_find",
    description = "Find nodes on the current screen by text, desc, resourceId and/or class, and return their refs for later actions.",
    parameters = findParameters,
    label = "gui_find",
    promptSnippet = "Find screen nodes by text, desc, resourceId or class",
    promptGuidelines = listOf(
        "Use gui_find when the target text, desc, resourceId or class is known; the returned refs stay valid until the next observe.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val selectors = args.findSelectors()
            val limit = args.intOrNull("limit") ?: DEFAULT_FIND_LIMIT
            if (limit !in 1..MAX_FIND_LIMIT) {
                throw IllegalArgumentException("limit 需要在 1..$MAX_FIND_LIMIT 之间")
            }
            val snapshot = actions.observe(GUI_DEFAULT_MAX_DEPTH, GUI_MAX_NODES)
            val allMatches = GuiNodeTree.findAll(snapshot, selectors, Int.MAX_VALUE)
            val matches = allMatches.take(limit)
            textResult(
                buildJsonObject {
                    put("count", matches.size)
                    put("total", allMatches.size)
                    put("hasMore", allMatches.size > matches.size)
                    put("truncated", snapshot.truncated)
                    put(
                        "nodes",
                        buildJsonArray { matches.forEach { node -> add(GuiNodeTree.nodeJson(node)) } },
                    )
                }.toString(),
            )
        }
    },
)

private fun guiTapTool(actions: GuiActions): CodingTool = CodingTool(
    name = "gui_tap",
    description = "Tap or long-press a node on the current screen by ref, text, desc, resourceId, class or screen coordinates.",
    parameters = tapParameters,
    label = "gui_tap",
    promptSnippet = "Tap or long-press a screen node",
    promptGuidelines = listOf(
        "Ask the user for confirmation before tapping anything destructive such as delete, send, pay or authorize.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val selector = args.parseSelector()
            val durationMs = args.intOrNull("durationMs") ?: DEFAULT_TAP_MILLIS
            if (durationMs !in 0..MAX_TAP_MILLIS) {
                throw IllegalArgumentException("durationMs 需要在 0..$MAX_TAP_MILLIS 之间")
            }
            actions.tap(selector, durationMs)
            textResult("已点击 ${selector.describe()}；操作后请再次 gui_observe 确认界面变化。")
        }
    },
)

private fun guiSetTextTool(actions: GuiActions): CodingTool = CodingTool(
    name = "gui_set_text",
    description = "Focus an editable field on the current screen, replace its text and read the text back to verify the write.",
    parameters = setTextParameters,
    label = "gui_set_text",
    promptSnippet = "Type into a screen input field and verify the result",
    promptGuidelines = listOf(
        "Never type passwords or verification codes; ask the user to enter sensitive input themselves.",
        "Check verified in the result; when it is false, re-observe the field or ask the user instead of retrying blindly.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val selector = args.parseSelector()
            if (selector is GuiSelector.Point) {
                throw IllegalArgumentException("输入文本需要 ref 或 text 选择器，不能使用坐标")
            }
            val text = args.stringOrNull("setText")
                ?: throw IllegalArgumentException("缺少参数 setText")
            val outcome = actions.setText(selector, text)
            textResult(
                buildJsonObject {
                    put("ok", true)
                    put("verified", outcome.verified)
                    put("text", outcome.text)
                    if (!outcome.verified) {
                        put("hint", "回读文本与写入不一致：界面可能格式化输入或拦截了写入，请重新 gui_observe 确认")
                    }
                }.toString(),
            )
        }
    },
)

private fun guiScrollTool(actions: GuiActions): CodingTool = CodingTool(
    name = "gui_scroll",
    description = "Scroll the current screen or a scrollable node in the given direction.",
    parameters = scrollParameters,
    label = "gui_scroll",
    promptSnippet = "Scroll the current screen",
    promptGuidelines = listOf(
        "Call gui_observe again after scrolling to confirm the new screen content.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val direction = when (args.requiredString("direction").lowercase()) {
                "up" -> GuiScrollDirection.UP
                "down" -> GuiScrollDirection.DOWN
                "left" -> GuiScrollDirection.LEFT
                "right" -> GuiScrollDirection.RIGHT
                else -> throw IllegalArgumentException("direction 只支持 up、down、left、right")
            }
            val amount = args.intOrNull("amount") ?: DEFAULT_SCROLL_AMOUNT
            if (amount !in 1..MAX_SCROLL_AMOUNT) {
                throw IllegalArgumentException("amount 需要在 1..$MAX_SCROLL_AMOUNT 之间")
            }
            val ref = args.stringOrNull("ref")?.trim()?.takeIf { it.isNotEmpty() }
            actions.scroll(direction, amount, ref?.let { GuiSelector.Ref(it) })
            textResult("已向 ${direction.name.lowercase()} 滚动 $amount 次；操作后请再次 gui_observe 确认内容。")
        }
    },
)

private fun guiGlobalTool(actions: GuiActions): CodingTool = CodingTool(
    name = "gui_global",
    description = "Perform a global system action: back, home, recents or the notifications panel.",
    parameters = globalParameters,
    label = "gui_global",
    promptSnippet = "Go back, home, open recents or notifications",
    promptGuidelines = listOf(
        "Avoid back or home when it would abandon unsaved user input without confirmation.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val action = when (args.requiredString("action").lowercase()) {
                "back" -> GuiGlobalAction.BACK
                "home" -> GuiGlobalAction.HOME
                "recents" -> GuiGlobalAction.RECENTS
                "notifications" -> GuiGlobalAction.NOTIFICATIONS
                else -> throw IllegalArgumentException("action 只支持 back、home、recents、notifications")
            }
            actions.global(action)
            textResult("已执行 ${action.name.lowercase()}；操作后请再次 gui_observe 确认界面。")
        }
    },
)

private fun guiWaitTool(actions: GuiActions): CodingTool = CodingTool(
    name = "gui_wait",
    description = "Wait until the current screen shows a package or text, or until a text disappears. Conditions combine.",
    parameters = waitParameters,
    label = "gui_wait",
    promptSnippet = "Wait for a screen, text to appear or text to disappear",
    promptGuidelines = listOf(
        "Combine forPackage, forText and forTextGone to confirm screen transitions.",
        "A gui_wait result with ok:false is not an error; re-observe the screen instead of retrying the same action blindly.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val packageName = args.stringOrNull("forPackage")?.trim()?.takeIf { it.isNotEmpty() }
            val text = args.stringOrNull("forText")?.trim()?.takeIf { it.isNotEmpty() }
            val textGone = args.stringOrNull("forTextGone")?.trim()?.takeIf { it.isNotEmpty() }
            if (packageName == null && text == null && textGone == null) {
                throw IllegalArgumentException("需要提供 forPackage、forText 或 forTextGone 至少一个条件")
            }
            val timeoutMs = args.intOrNull("timeoutMs") ?: DEFAULT_WAIT_MILLIS
            if (timeoutMs !in MIN_WAIT_MILLIS..MAX_WAIT_MILLIS) {
                throw IllegalArgumentException("timeoutMs 需要在 $MIN_WAIT_MILLIS..$MAX_WAIT_MILLIS 之间")
            }
            val startedAt = System.currentTimeMillis()
            val matched = actions.waitFor(packageName, text, textGone, timeoutMs.toLong())
            val elapsedMs = (System.currentTimeMillis() - startedAt).toInt()
            if (matched) {
                textResult(
                    buildJsonObject {
                        put("ok", true)
                        put("elapsedMs", elapsedMs)
                    }.toString(),
                )
            } else {
                textResult(
                    buildJsonObject {
                        put("ok", false)
                        put("reason", "timeout")
                        put("elapsedMs", elapsedMs)
                        put("timeoutMs", timeoutMs)
                        put("hint", "等待超时：界面没有出现预期变化，请重新 gui_observe 判断当前状态")
                    }.toString(),
                )
            }
        }
    },
)

private fun guiScreenshotTool(
    actions: GuiActions,
    screenshotDir: File,
    linuxScreenshotDir: String,
): CodingTool = CodingTool(
    name = "gui_screenshot",
    description = "Capture the current screen as a PNG file and return its host path, /gui path, width and height. Android 11+ only.",
    parameters = screenshotParameters,
    label = "gui_screenshot",
    promptSnippet = "Capture the current screen as a PNG",
    promptGuidelines = listOf(
        "Use gui_screenshot only when the visual layout matters; screenshots can contain sensitive data, so never share them or read secrets from them.",
        "Screens are not captured on Android 10, while locked, or when an app sets FLAG_SECURE; ask the user instead of retrying in a loop.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        guiToolResult {
            requireConnected(actions)
            val shot = actions.screenshot()
            if (shot.png.isEmpty()) throw IllegalStateException("截图内容为空，请稍后重试")
            if (!screenshotDir.isDirectory && !screenshotDir.mkdirs()) {
                throw IllegalStateException("截图目录不可写：${screenshotDir.absolutePath}")
            }
            val fileName = "gui-${System.currentTimeMillis()}.png"
            val file = File(screenshotDir, fileName)
            file.writeBytes(shot.png)
            val base = linuxScreenshotDir.trimEnd('/').ifEmpty { GUI_DEFAULT_LINUX_SCREENSHOT_DIR }
            textResult(
                buildJsonObject {
                    put("path", "$base/$fileName")
                    put("hostPath", file.absolutePath)
                    put("width", shot.width)
                    put("height", shot.height)
                    put("sizeBytes", shot.png.size)
                }.toString(),
            )
        }
    },
)

private suspend fun guiToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "GUI 操作失败", isError = true)
}

private fun requireConnected(actions: GuiActions) {
    if (!actions.connected) throw GuiUnavailableException(DISCONNECTED_MESSAGE)
}

private fun JsonObject.findSelectors(): List<GuiSelector> {
    val selectors = buildList {
        stringOrNull("text")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.Text(it)) }
        stringOrNull("desc")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.Desc(it)) }
        stringOrNull("resourceId")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.ResourceId(it)) }
        stringOrNull("class")?.trim()?.takeIf { it.isNotEmpty() }?.let { add(GuiSelector.ClassName(it)) }
    }
    if (selectors.isEmpty()) {
        throw IllegalArgumentException("需要提供 text、desc、resourceId、class 至少一个条件")
    }
    return selectors
}

private fun JsonObject.parseSelector(): GuiSelector {
    val ref = stringOrNull("ref")?.trim()?.takeIf { it.isNotEmpty() }
    val text = stringOrNull("text")?.trim()?.takeIf { it.isNotEmpty() }
    val desc = stringOrNull("desc")?.trim()?.takeIf { it.isNotEmpty() }
    val resourceId = stringOrNull("resourceId")?.trim()?.takeIf { it.isNotEmpty() }
    val className = stringOrNull("class")?.trim()?.takeIf { it.isNotEmpty() }
    val x = intOrNull("x")
    val y = intOrNull("y")
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

private fun GuiSelector.describe(): String = when (this) {
    is GuiSelector.Ref -> "ref=$ref"
    is GuiSelector.Text -> "text=「$text」"
    is GuiSelector.Desc -> "desc=「$desc」"
    is GuiSelector.ResourceId -> "resourceId=$id"
    is GuiSelector.ClassName -> "class=$name"
    is GuiSelector.Point -> "坐标 ($x, $y)"
}

private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.intOrNull(name: String): Int? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.requiredString(name: String): String =
    stringOrNull(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("缺少参数 $name")
