package io.github.whywood.deepdeep.screen

import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.uistate.ActionEffect
import com.wangzan101.mika.core.uistate.EffectKind
import com.wangzan101.mika.core.uistate.PortFacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenEffectsTest {
    @Test
    fun ignoresVolatileNodeRefs() {
        val before = snapshot(node(ref = "old"))
        val after = snapshot(node(ref = "new"))

        val effect = classify(before, after)

        assertEquals(EffectKind.NoEffect, effect?.kind)
        assertTrue(effect?.diff?.none == true)
    }

    @Test
    fun detectsContentAndPlaceChanges() {
        val before = snapshot(node(ref = "1", text = "播放"))
        val content = snapshot(node(ref = "2", text = "暂停"))
        val place = snapshot(node(ref = "3"), packageName = "com.example.next")

        assertEquals(EffectKind.SmallChange, classify(before, content)?.kind)
        assertEquals(EffectKind.PlaceChanged, classify(before, place)?.kind)
    }

    @Test
    fun reportsUnavailableWhenEitherObservationFailed() {
        assertNull(classify(null, snapshot(node(ref = "1"))))
    }

    private fun classify(before: GuiSnapshot?, after: GuiSnapshot?) =
        ActionEffect.classify(Verb.Tap, before?.toEffectSnapshot(null), after?.toEffectSnapshot(null), PortFacts(), null)

    private fun snapshot(node: GuiSnapshotNode, packageName: String = "com.example") = GuiSnapshot(
        packageName = packageName,
        windowTitle = "Main",
        nodes = listOf(node),
        truncated = false,
    )

    private fun node(ref: String, text: String = "播放") = GuiSnapshotNode(
        ref = ref,
        className = "android.widget.Button",
        bounds = GuiBounds(0, 0, 100, 50),
        text = text,
        desc = null,
        resourceId = "com.example:id/play",
        clickable = true,
        scrollable = false,
        editable = false,
        enabled = true,
        depth = 0,
    )
}
