package io.github.whywood.deepdeep.ui.shell

import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.plugin.PluginInteraction
import io.github.whywood.deepdeep.runtime.JobSnapshot
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.SessionUsage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHostTest {
    private val attachment = ShellOverlay.AttachmentPreview(
        AttachmentRef("a1", "a.png", "image/png", 1L, "a.png", AttachmentKind.IMAGE), File("a.png"),
    )
    private val usage = ShellOverlay.Usage(SessionUsage(1L, 2L, 3L, 4L, 5L, 6L, 0.0))
    private val jobs = ShellOverlay.Jobs(
        listOf(JobSnapshot("j1", "s1", "label", "cmd", JobStatus.RUNNING, 0L)), "j1", "out",
    )
    private val files = ShellOverlay.Files(File("root"), "root")
    private val pluginSetup = ShellOverlay.PluginSetup(PluginInteraction("p1", "title", "stage"))
    private val voice = ShellOverlay.VoiceAssistant("answer", false, false, false)
    private val all = ShellOverlayState(
        ShellOverlay.WorkspaceCreate(listOf("home")),
        ShellOverlay.WorkspaceRename("w1", "home", listOf("home")),
        ShellOverlay.WorkspaceDelete("w1", "home", 2),
        attachment, usage, jobs, files, pluginSetup, voice,
    )

    @Test
    fun emptyStateResolvesNoOverlay() {
        assertTrue(resolveOverlays(ShellOverlayState()).isEmpty())
        assertNull(resolveOverlay(ShellOverlayState()))
    }

    @Test
    fun allOverlaysResolveInCompositionOrder() {
        val expected = listOf(
            all.workspaceCreate, all.workspaceRename, all.workspaceDelete,
            attachment, usage, jobs, files, pluginSetup, voice,
        )
        assertEquals(expected, resolveOverlays(all))
    }

    @Test
    fun topmostOverlayIsLastResolved() {
        assertEquals(voice, resolveOverlay(all))
        assertEquals(pluginSetup, resolveOverlay(all.copy(voiceAssistant = null)))
        assertEquals(files, resolveOverlay(all.copy(voiceAssistant = null, pluginSetup = null)))
    }

    @Test
    fun singleOverlayResolvesAlone() {
        val onlyUsage = ShellOverlayState(usage = usage)
        assertEquals(listOf(usage), resolveOverlays(onlyUsage))
        assertEquals(usage, resolveOverlay(onlyUsage))
    }

    @Test
    fun lowerPriorityOverlaysStayRenderedUnderTopmost() {
        val state = ShellOverlayState(ShellOverlay.WorkspaceCreate(emptyList()), usage = usage)
        assertEquals(listOf(ShellOverlay.WorkspaceCreate(emptyList()), usage), resolveOverlays(state))
        assertEquals(usage, resolveOverlay(state))
    }
}
