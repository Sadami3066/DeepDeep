package io.github.whywood.deepdeep.screen

import io.github.whywood.deepdeep.plugin.gui.GuiBounds
import io.github.whywood.deepdeep.plugin.gui.GuiNode

internal object AdbUiAutomatorParser {
    private val TAG_PATTERN = Regex("<node\\b([^>]*?)(/?)>|</node>")
    private val ATTRIBUTE_PATTERN = Regex("([A-Za-z0-9_:-]+)=\"([^\"]*)\"")
    private val BOUNDS_PATTERN = Regex("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")

    data class Parsed(val root: GuiNode, val packageName: String?)

    fun parse(xml: String): Parsed? {
        val tags = TAG_PATTERN.findAll(xml).toList()
        if (tags.isEmpty()) return null
        var root: MutableNode? = null
        val stack = ArrayDeque<MutableNode>()
        for (match in tags) {
            if (match.value.startsWith("</")) {
                stack.removeLastOrNull()
                continue
            }
            val node = MutableNode(attributes(match.groupValues[1]))
            val parent = stack.lastOrNull()
            if (parent == null) {
                if (root == null) root = node
            } else {
                parent.children += node
            }
            if (!match.groupValues[2].contains('/')) stack.addLast(node)
        }
        val parsedRoot = root ?: return null
        val packageName = parsedRoot.attributes["package"]?.takeIf { it.isNotBlank() }
            ?: findPackage(parsedRoot)
        return Parsed(parsedRoot.toGuiNode(), packageName)
    }

    private fun attributes(source: String): Map<String, String> =
        ATTRIBUTE_PATTERN.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }

    private fun findPackage(node: MutableNode): String? {
        node.children.forEach { child ->
            child.attributes["package"]?.takeIf { it.isNotBlank() }?.let { return it }
            findPackage(child)?.let { return it }
        }
        return null
    }

    private fun parseBounds(value: String?): GuiBounds? {
        val match = value?.let { BOUNDS_PATTERN.find(it) } ?: return null
        val (left, top, right, bottom) = match.destructured
        return GuiBounds(
            left = left.toIntOrNull() ?: return null,
            top = top.toIntOrNull() ?: return null,
            right = right.toIntOrNull() ?: return null,
            bottom = bottom.toIntOrNull() ?: return null,
        )
    }

    private class MutableNode(val attributes: Map<String, String>) {
        val children = mutableListOf<MutableNode>()

        fun toGuiNode(): GuiNode {
            val className = attributes["class"].orEmpty()
            return GuiNode(
                className = className,
                bounds = parseBounds(attributes["bounds"]) ?: GuiBounds(0, 0, 0, 0),
                text = attributes["text"]?.trim()?.takeIf { it.isNotEmpty() },
                desc = attributes["content-desc"]?.trim()?.takeIf { it.isNotEmpty() },
                resourceId = attributes["resource-id"]?.takeIf { it.isNotEmpty() },
                clickable = attributes["clickable"] == "true",
                scrollable = attributes["scrollable"] == "true",
                editable = className.endsWith("EditText") || className.endsWith("AutoCompleteTextView"),
                enabled = attributes["enabled"] != "false",
                children = children.map { it.toGuiNode() },
            )
        }
    }
}
