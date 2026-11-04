package io.github.whywood.deepdeep.plugin.gui

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class GuiSnapshotCache {
    private val snapshots = ConcurrentHashMap<Int, GuiSnapshot>()
    private val knownNodes = ConcurrentHashMap<String, GuiSnapshotNode>()

    fun clear() {
        snapshots.clear()
        knownNodes.clear()
    }

    fun remove(displayId: Int) {
        snapshots.remove(displayId)
    }

    fun snapshot(displayId: Int): GuiSnapshot? = snapshots[displayId]

    fun store(displayId: Int, snapshot: GuiSnapshot): GuiSnapshot {
        require(displayId >= 0) { "Display id must not be negative" }
        val generation = UUID.randomUUID().toString()
        val stamped = snapshot.copy(nodes = snapshot.nodes.mapIndexed { index, node ->
            node.copy(ref = "$displayId:$generation:$index")
        })
        snapshots[displayId] = stamped
        remember(stamped.nodes)
        return stamped
    }

    fun findOrNull(displayId: Int, selector: GuiSelector): GuiSnapshotNode? {
        require(displayId >= 0) { "Display id must not be negative" }
        val current = snapshots[displayId] ?: return null
        return GuiNodeTree.find(current, selector)
    }

    fun find(displayId: Int, selector: GuiSelector): GuiSnapshotNode {
        require(displayId >= 0) { "Display id must not be negative" }
        val current = snapshots[displayId] ?: throw IllegalStateException(missingSnapshotMessage(displayId))
        return GuiNodeTree.find(current, selector)
            ?: throw IllegalArgumentException("Node ref is stale or did not match; call screen_observe again")
    }

    fun findTarget(displayId: Int, selector: GuiSelector): GuiSnapshotNode? {
        findOrNull(displayId, selector)?.let { return it }
        val ref = (selector as? GuiSelector.Ref)?.ref ?: return null
        return knownNodes[ref.trim().removePrefix("#")]
    }

    fun remember(displayId: Int, snapshot: GuiSnapshot) {
        require(displayId >= 0) { "Display id must not be negative" }
        remember(snapshot.nodes)
    }

    private fun remember(nodes: List<GuiSnapshotNode>) {
        if (knownNodes.size + nodes.size > MAX_KNOWN_NODES) knownNodes.clear()
        nodes.forEach { node -> knownNodes[node.ref] = node }
    }

    private companion object {
        const val MAX_KNOWN_NODES = 2048
    }
}
