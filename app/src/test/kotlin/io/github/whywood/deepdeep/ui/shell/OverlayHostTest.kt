package io.github.whywood.deepdeep.ui.shell

import io.github.whywood.deepdeep.data.AttachmentKind
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.runtime.JobSnapshot
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.SessionUsage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHostTest {
    private val workspaceCreate = ShellOverlay.WorkspaceCreate(listOf("home"), "/data/workspaces")
    private val workspaceRename = ShellOverlay.WorkspaceRename("w1", "home", listOf("home"))
    private val workspaceDelete = ShellOverlay.WorkspaceDelete("w1", "home", 2)
    private val attachment = ShellOverlay.AttachmentPreview(
        AttachmentRef("a1", "a.png", "image/png", 1L, "a.png", AttachmentKind.IMAGE), File("a.png"),
    )
    private val usage = ShellOverlay.Usage(SessionUsage(1L, 2L, 3L, 4L, 5L, 6L, 0.0))
    private val jobs = ShellOverlay.Jobs(
        listOf(JobSnapshot("j1", "s1", "label", "cmd", JobStatus.RUNNING, 0L)), "j1", "out",
    )
    private val all = ShellOverlayState(
        workspaceCreate = workspaceCreate,
        workspaceRename = workspaceRename,
        workspaceDelete = workspaceDelete,
        attachmentPreview = attachment,
        usage = usage,
        jobs = jobs,
    )

    @Test
    fun emptyStateResolvesNoOverlay() {
        assertTrue(resolveOverlays(ShellOverlayState()).isEmpty())
        assertNull(resolveOverlay(ShellOverlayState()))
    }

    @Test
    fun allOverlaysResolveInCompositionOrder() {
        val expected = listOf(
            workspaceCreate, workspaceRename, workspaceDelete,
            attachment, usage, jobs,
        )
        assertEquals(expected, resolveOverlays(all))
    }

    @Test
    fun topmostOverlayIsLastResolved() {
        assertEquals(jobs, resolveOverlay(all))
        assertEquals(usage, resolveOverlay(all.copy(jobs = null)))
    }

    @Test
    fun singleOverlayResolvesAlone() {
        val onlyUsage = ShellOverlayState(usage = usage)
        assertEquals(listOf(usage), resolveOverlays(onlyUsage))
        assertEquals(usage, resolveOverlay(onlyUsage))
    }

    @Test
    fun lowerPriorityOverlaysStayRenderedUnderTopmost() {
        val state = ShellOverlayState(
            workspaceCreate = ShellOverlay.WorkspaceCreate(emptyList(), "/data/workspaces"),
            usage = usage,
        )
        assertEquals(
            listOf(ShellOverlay.WorkspaceCreate(emptyList(), "/data/workspaces"), usage),
            resolveOverlays(state),
        )
        assertEquals(usage, resolveOverlay(state))
    }
}
