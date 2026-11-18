package io.github.whywood.deepdeep.screen

import com.wangzan101.mika.core.uistate.InterfaceUpdate
import com.wangzan101.mika.core.uistate.ModelSnapshot
import com.wangzan101.mika.core.uistate.RefStabilizer
import com.wangzan101.mika.core.uistate.Resolved
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal class PhongentScreenReferences {
    private var binding: String? = null
    private var stabilizer = newStabilizer()
    private var shown: ModelSnapshot? = null
    private var previousLimits: Pair<Int, Int>? = null
    private val chain = ArrayDeque<String>()

    fun update(
        observed: ScreenObserveResult,
        visibleBase: String?,
        maxNodes: Int,
        scanLimits: Pair<Int, Int>,
        display: ScreenDisplayInfo?,
        visibleReads: Set<String>? = null,
    ): JsonObject {
        val snapshot = register(observed, display)
        val seen = chain.isNotEmpty() && chain.size - 1 < MAX_CHAIN_DIFFS &&
            (visibleReads?.let { reads -> chain.all { it in reads } } ?: (shown?.snapshotId == visibleBase)) &&
            previousLimits == scanLimits && !observed.snapshot.truncated
        val diff = InterfaceUpdate.between(shown, snapshot, seen, maxNodes)
        shown = snapshot
        previousLimits = scanLimits
        if (diff["full"] == JsonPrimitive(true)) chain.clear()
        if (observed.snapshot.truncated || diff["truncated"] == JsonPrimitive(true)) {
            chain.clear()
        } else {
            chain += snapshot.snapshotId
        }
        return diff
    }

    fun invalidate() {
        binding = null
        shown = null
        previousLimits = null
        chain.clear()
        stabilizer = newStabilizer()
    }

    suspend fun resolve(controller: ScreenController, ref: String, snapshotId: String): String {
        val snapshot = controller.observe(SCREEN_DEFAULT_MAX_DEPTH, GUI_MAX_NODES, null)
        register(snapshot, controller.effectDisplayInfo())
        return when (val resolved = stabilizer.resolve(ref, snapshotId)) {
            is Resolved.Current -> resolved.portRef
            Resolved.Stale -> error("The screen reference expired. Observe this target again and use its new snapshotId and ref.")
        }
    }

    private fun register(observed: ScreenObserveResult, display: ScreenDisplayInfo?): ModelSnapshot {
        val selected = observed.snapshot.windows.firstOrNull { it.selected }
        val current = listOf(observed.scope, observed.backend, selected?.id, observed.snapshot.packageName).joinToString("/")
        if (binding != current) {
            stabilizer = newStabilizer()
            shown = null
            chain.clear()
            binding = current
        }
        return stabilizer.next(observed.snapshot.toEffectSnapshot(display))
    }

    private fun newStabilizer() = RefStabilizer("s-${UUID.randomUUID()}-")

    private companion object {
        const val MAX_CHAIN_DIFFS = 5
    }
}
