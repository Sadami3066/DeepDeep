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
        )
    }

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
        val index = parseRef(ref) ?: return null
        val node = snapshot.nodes.getOrNull(index) ?: return null
        return node.takeIf { it.ref == index.toString() }
    }

    fun find(snapshot: GuiSnapshot, selector: GuiSelector): GuiSnapshotNode? = when (selector) {
        is GuiSelector.Ref -> findRef(snapshot, selector.ref)
        else -> snapshot.nodes.firstOrNull { node -> matches(node, selector) }
    }

    fun matches(node: GuiSnapshotNode, selector: GuiSelector): Boolean = when (selector) {
        is GuiSelector.Ref -> parseRef(selector.ref)?.toString() == node.ref
        is GuiSelector.Text -> node.text?.contains(selector.text, ignoreCase = true) == true
        is GuiSelector.Desc -> node.desc?.contains(selector.desc, ignoreCase = true) == true
        is GuiSelector.ResourceId -> matchesResourceId(node.resourceId, selector.id)
        is GuiSelector.ClassName -> matchesClassName(node.className, selector.name)
        is GuiSelector.Point -> node.bounds.contains(selector.x, selector.y)
    }

    fun matchesAll(node: GuiSnapshotNode, selectors: List<GuiSelector>): Boolean =
        selectors.isNotEmpty() && selectors.all { selector -> matches(node, selector) }

    fun findAll(snapshot: GuiSnapshot, selectors: List<GuiSelector>, limit: Int): List<GuiSnapshotNode> =
        snapshot.nodes.filter { node -> matchesAll(node, selectors) }.take(limit)

    private fun matchesResourceId(resourceId: String?, id: String): Boolean {
        if (resourceId.isNullOrEmpty() || id.isEmpty()) return false
        return resourceId == id || resourceId.endsWith("/$id") || resourceId.endsWith(":id/$id")
    }

    private fun matchesClassName(className: String, name: String): Boolean {
        if (name.isEmpty()) return false
        return className == name || className.endsWith(".$name")
    }
}
