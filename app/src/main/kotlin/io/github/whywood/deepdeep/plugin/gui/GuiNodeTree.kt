package io.github.whywood.deepdeep.plugin.gui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val GUI_DEFAULT_MAX_DEPTH = 8
const val GUI_MAX_DEPTH = 20
const val GUI_DEFAULT_MAX_NODES = 120
const val GUI_MAX_NODES = 400

data class GuiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    fun format(): String = "[$left,$top][$right,$bottom]"
}

data class GuiNode(
    val className: String,
    val bounds: GuiBounds,
    val text: String? = null,
    val desc: String? = null,
    val resourceId: String? = null,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = true,
    val children: List<GuiNode> = emptyList(),
    val token: Any? = null,
)

data class GuiSnapshotNode(
    val ref: String,
    val className: String,
    val bounds: GuiBounds,
    val text: String?,
    val desc: String?,
    val resourceId: String?,
    val clickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val depth: Int,
    val token: Any? = null,
)

data class GuiSnapshot(
    val packageName: String?,
    val windowTitle: String?,
    val nodes: List<GuiSnapshotNode>,
    val truncated: Boolean,
    val scanned: Int = nodes.size,
)

sealed interface GuiSelector {
    data class Ref(val ref: String) : GuiSelector

    data class Text(val text: String) : GuiSelector

    data class Desc(val desc: String) : GuiSelector

    data class ResourceId(val id: String) : GuiSelector

    data class ClassName(val name: String) : GuiSelector

    data class Point(val x: Int, val y: Int) : GuiSelector
}

object GuiNodeTree {
    fun buildSnapshot(
        root: GuiNode,
        packageName: String? = null,
        windowTitle: String? = null,
        maxDepth: Int = GUI_DEFAULT_MAX_DEPTH,
        maxNodes: Int = GUI_DEFAULT_MAX_NODES,
    ): GuiSnapshot {
        require(maxDepth >= 0) { "maxDepth 不能为负数" }
        require(maxNodes > 0) { "maxNodes 必须大于 0" }
        val nodes = ArrayList<GuiSnapshotNode>(minOf(maxNodes, 128))
        var truncated = false

        fun visit(node: GuiNode, depth: Int) {
            if (depth > maxDepth || nodes.size >= maxNodes) {
                truncated = true
                return
            }
            nodes += GuiSnapshotNode(
                ref = nodes.size.toString(),
                className = node.className,
                bounds = node.bounds,
                text = node.text,
                desc = node.desc,
                resourceId = node.resourceId,
                clickable = node.clickable,
                scrollable = node.scrollable,
                editable = node.editable,
                enabled = node.enabled,
                depth = depth,
                token = node.token,
            )
            node.children.forEach { child -> visit(child, depth + 1) }
        }

        visit(root, 0)
        return GuiSnapshot(
            packageName = packageName,
            windowTitle = windowTitle,
            nodes = nodes,
            truncated = truncated,
            scanned = nodes.size,
        )
    }

    fun countNodes(node: GuiNode): Int = 1 + node.children.sumOf { child -> countNodes(child) }

    fun render(snapshot: GuiSnapshot): String = buildJsonObject {
        snapshot.packageName?.let { put("package", it) }
        snapshot.windowTitle?.let { put("title", it) }
        put("count", snapshot.nodes.size)
        put("truncated", snapshot.truncated)
        put("nodes", buildJsonArray { snapshot.nodes.forEach { node -> add(nodeJson(node)) } })
    }.toString()

    fun nodeJson(node: GuiSnapshotNode): JsonObject = buildJsonObject {
        put("ref", node.ref)
        put("class", node.className)
        node.text?.let { put("text", it) }
        node.desc?.let { put("desc", it) }
        node.resourceId?.let { put("resourceId", it) }
        put("bounds", node.bounds.format())
        if (node.clickable) put("clickable", true)
        if (node.scrollable) put("scrollable", true)
        if (node.editable) put("editable", true)
        if (!node.enabled) put("enabled", false)
        put("depth", node.depth)
    }

    fun parseRef(ref: String): Int? {
        val index = ref.trim().removePrefix("#").toIntOrNull() ?: return null
        return index.takeIf { it >= 0 }
    }

    fun findRef(snapshot: GuiSnapshot, ref: String): GuiSnapshotNode? {
        val key = ref.trim().removePrefix("#")
        return snapshot.nodes.firstOrNull { it.ref == key }
    }

    fun refIndex(ref: String): Int? {
        val key = ref.trim().removePrefix("#")
        val index = key.substringAfterLast(':').toIntOrNull() ?: key.toIntOrNull() ?: return null
        return index.takeIf { it >= 0 }
    }

    fun counterpart(snapshot: GuiSnapshot, target: GuiSnapshotNode): GuiSnapshotNode? {
        if (snapshot.nodes.isEmpty()) return null
        val targetIndex = refIndex(target.ref)
        var best: GuiSnapshotNode? = null
        var bestScore = -1
        var bestDistance = Int.MAX_VALUE
        snapshot.nodes.forEach { node ->
            val score = relocationScore(node, target, targetIndex) ?: return@forEach
            val distance = boundsDistance(node.bounds, target.bounds)
            if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                best = node
                bestScore = score
                bestDistance = distance
            }
        }
        return best
    }

    private fun relocationScore(node: GuiSnapshotNode, target: GuiSnapshotNode, targetIndex: Int?): Int? {
        val targetId = target.resourceId
        if (targetId != null && node.resourceId != targetId) return null
        val targetText = normalizedMatchText(target.text)
        val targetDesc = normalizedMatchText(target.desc)
        val nodeText = normalizedMatchText(node.text)
        val nodeDesc = normalizedMatchText(node.desc)
        if (targetText.isNotEmpty() && nodeText.isNotEmpty() && nodeText != targetText && nodeDesc != targetText) {
            return null
        }
        if (targetDesc.isNotEmpty() && nodeDesc.isNotEmpty() && nodeDesc != targetDesc && nodeText != targetDesc) {
            return null
        }
        val sameClass = target.className == node.className
        val sameIndex = targetIndex != null && refIndex(node.ref) == targetIndex
        val overlap = boundsOverlapRatio(target.bounds, node.bounds)
        val identityMatched = (targetId != null && node.resourceId == targetId) ||
            (targetText.isNotEmpty() && (nodeText == targetText || nodeDesc == targetText)) ||
            (targetDesc.isNotEmpty() && (nodeDesc == targetDesc || nodeText == targetDesc))
        val labeled = targetId != null || targetText.isNotEmpty() || targetDesc.isNotEmpty()
        if (labeled) {
            if (!identityMatched) return null
        } else if (!sameClass || (!sameIndex && overlap < 0.5f)) {
            return null
        }
        var score = 0
        if (targetId != null && node.resourceId == targetId) score += 12
        if (targetText.isNotEmpty() && (nodeText == targetText || nodeDesc == targetText)) score += 8
        if (targetDesc.isNotEmpty() && (nodeDesc == targetDesc || nodeText == targetDesc)) score += 7
        if (sameClass) score += 2
        if (target.clickable == node.clickable) score += 1
        if (target.editable == node.editable) score += 1
        if (sameIndex) score += 2
        if (overlap >= 0.5f) score += 3 else if (overlap > 0f) score += 1
        return score
    }

    private fun boundsDistance(first: GuiBounds, second: GuiBounds): Int =
        kotlin.math.abs(first.centerX - second.centerX) + kotlin.math.abs(first.centerY - second.centerY)

    private fun boundsOverlapRatio(first: GuiBounds, second: GuiBounds): Float {
        val width = minOf(first.right, second.right) - maxOf(first.left, second.left)
        val height = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
        if (width <= 0 || height <= 0) return 0f
        val intersection = width.toLong() * height
        val smaller = minOf(first.width.toLong() * first.height, second.width.toLong() * second.height)
        if (smaller <= 0) return 0f
        return (intersection.toDouble() / smaller).toFloat()
    }

    fun find(snapshot: GuiSnapshot, selector: GuiSelector): GuiSnapshotNode? = when (selector) {
        is GuiSelector.Ref -> findRef(snapshot, selector.ref)
        else -> snapshot.nodes.firstOrNull { node -> matches(node, selector) }
    }

    fun matches(node: GuiSnapshotNode, selector: GuiSelector): Boolean = when (selector) {
        is GuiSelector.Ref -> selector.ref.trim().removePrefix("#") == node.ref
        is GuiSelector.Text -> matchesValue(node.text, selector.text) || matchesValue(node.desc, selector.text)
        is GuiSelector.Desc -> matchesValue(node.desc, selector.desc)
        is GuiSelector.ResourceId -> matchesResourceId(node.resourceId, selector.id)
        is GuiSelector.ClassName -> matchesClassName(node.className, selector.name)
        is GuiSelector.Point -> node.bounds.contains(selector.x, selector.y)
    }

    fun matchesAll(node: GuiSnapshotNode, selectors: List<GuiSelector>): Boolean =
        selectors.isNotEmpty() && selectors.all { selector -> matches(node, selector) }

    fun findAll(snapshot: GuiSnapshot, selectors: List<GuiSelector>, limit: Int): List<GuiSnapshotNode> =
        snapshot.nodes.filter { node -> matchesAll(node, selectors) }.take(limit)

    fun matchesValue(value: String?, query: String): Boolean {
        val normalizedQuery = normalizedMatchText(query)
        if (normalizedQuery.isEmpty()) return false
        return normalizedMatchText(value).contains(normalizedQuery)
    }

    private fun matchesResourceId(resourceId: String?, id: String): Boolean {
        if (resourceId.isNullOrEmpty() || id.isEmpty()) return false
        return resourceId == id || resourceId.endsWith("/$id") || resourceId.endsWith(":id/$id")
    }

    private fun matchesClassName(className: String, name: String): Boolean {
        if (name.isEmpty()) return false
        return className == name || className.endsWith(".$name")
    }
}

private val MATCH_IGNORED_CHARACTERS = setOf(',', '，', '.', '．', '、', '_', '\'', '’', '‘', '·', '。')

internal fun normalizedMatchText(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    val builder = StringBuilder(value.length)
    value.forEach { character ->
        if (character.isWhitespace() || character == '\u00A0' || character in MATCH_IGNORED_CHARACTERS) return@forEach
        builder.append(character.lowercaseChar())
    }
    return builder.toString()
}
