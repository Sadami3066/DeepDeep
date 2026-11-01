package io.github.whywood.deepdeep.plugin.gui

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class GuiSnapshotCache {
    private val snapshots = ConcurrentHashMap<Int, GuiSnapshot>()

    fun clear() = snapshots.clear()

    fun remove(displayId: Int) { snapshots.remove(displayId) }

    fun store(displayId: Int, snapshot: GuiSnapshot): GuiSnapshot {
        require(displayId >= 0) { "屏幕编号不能为负数" }
        val generation = UUID.randomUUID().toString()
        val stamped = snapshot.copy(nodes = snapshot.nodes.mapIndexed { index, node ->
            node.copy(ref = "$displayId:$generation:$index")
        })
        snapshots[displayId] = stamped
        return stamped
    }

    fun find(displayId: Int, selector: GuiSelector): GuiSnapshotNode {
        require(displayId >= 0) { "屏幕编号不能为负数" }
        val current = snapshots[displayId] ?: throw IllegalStateException(missingSnapshotMessage(displayId))
        return GuiNodeTree.find(current, selector)
            ?: throw IllegalArgumentException("节点 ref 已失效或未匹配，请重新 screen_observe")
    }
}
