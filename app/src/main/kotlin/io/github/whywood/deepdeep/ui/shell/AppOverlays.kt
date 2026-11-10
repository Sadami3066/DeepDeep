package io.github.whywood.deepdeep.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.runtime.JobSnapshot
import io.github.whywood.deepdeep.runtime.JobStatus
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.ui.chat.ChatViewModel
import io.github.whywood.deepdeep.ui.common.chat.AttachmentSource
import io.github.whywood.deepdeep.ui.common.chat.AttachmentSourceSheet
import io.github.whywood.deepdeep.ui.common.chat.CameraCaptureSheet
import io.github.whywood.deepdeep.ui.common.chat.ChatUiState
import io.github.whywood.deepdeep.ui.common.chat.rememberAttachmentPickers
import io.github.whywood.deepdeep.ui.common.shareChooser
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



internal class AppOverlaysState {
    var attachmentSheetOpen by mutableStateOf(false)
    var cameraSheetOpen by mutableStateOf(false)
    var previewAttachment by mutableStateOf<AttachmentRef?>(null)
    var previewRoot by mutableStateOf<File?>(null)
    var usageSheetOpen by mutableStateOf(false)
    var jobsSheetOpen by mutableStateOf(false)
    var selectedJobId by mutableStateOf<String?>(null)
    var jobOutput by mutableStateOf<String?>(null)
    var workspaceCreateOpen by mutableStateOf(false)
    var workspaceRenameId by mutableStateOf<String?>(null)
    var workspaceDeleteTarget by mutableStateOf<RuntimeWorkspace?>(null)
    var workspaceDeleteCount by mutableIntStateOf(0)

    companion object {
        val Saver: Saver<AppOverlaysState, Any> = listSaver(
            save = {
                listOf(
                    it.attachmentSheetOpen,
                    it.cameraSheetOpen,
                    it.workspaceCreateOpen,
                    it.workspaceRenameId ?: "",
                    it.usageSheetOpen,
                )
            },
            restore = { saved ->
                AppOverlaysState().apply {
                    attachmentSheetOpen = saved[0] as Boolean
                    cameraSheetOpen = saved[1] as Boolean
                    workspaceCreateOpen = saved[2] as Boolean
                    workspaceRenameId = (saved[3] as String).ifBlank { null }
                    usageSheetOpen = saved[4] as Boolean
                }
            },
        )
    }
}

@Composable
internal fun AppOverlays(
    graph: AppDependencies,
    chatViewModel: ChatViewModel,
    state: ChatUiState,
    activeJobs: List<JobSnapshot>,
    attachmentRoot: File,
    drawer: ShellDrawerState,
    drawerActions: ShellDrawerActions,
    overlays: AppOverlaysState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val privateWorkspacesDir = remember(context) { File(context.filesDir, "workspaces").absolutePath }
    val shareChooserTitle = stringResource(R.string.assistant_attach_share)
    val attachmentPickers = rememberAttachmentPickers { uri -> chatViewModel.attach(uri) }
    LaunchedEffect(overlays.jobsSheetOpen, overlays.selectedJobId) {
        val id = overlays.selectedJobId
        if (!overlays.jobsSheetOpen || id == null) return@LaunchedEffect
        while (true) {
            val job = graph.jobRegistry.jobs.value.firstOrNull { it.id == id } ?: break
            overlays.jobOutput = runCatching { graph.jobRegistry.read(id).output }.getOrNull()
            if (job.status != JobStatus.RUNNING) break
            delay(800)
        }
    }
    OverlayHost(
        overlays = ShellOverlayState(
            workspaceCreate = ShellOverlay.WorkspaceCreate(
                state.workspaces.map { it.name },
                privateWorkspacesDir,
            ).takeIf { overlays.workspaceCreateOpen },
            workspaceRename = overlays.workspaceRenameId
                ?.let { id -> state.workspaces.firstOrNull { it.id == id } }
                ?.let { target ->
                    ShellOverlay.WorkspaceRename(
                        target.id,
                        target.name,
                        state.workspaces.filterNot { it.id == target.id }.map { it.name },
                    )
                },
            workspaceDelete = overlays.workspaceDeleteTarget?.let {
                ShellOverlay.WorkspaceDelete(it.id, it.name, overlays.workspaceDeleteCount)
            },
            attachmentPreview = overlays.previewAttachment?.let {
                ShellOverlay.AttachmentPreview(it, graph.attachmentStore.file(it, overlays.previewRoot ?: attachmentRoot))
            },
            usage = state.usage?.takeIf { overlays.usageSheetOpen }?.let(ShellOverlay::Usage),
            jobs = ShellOverlay.Jobs(activeJobs, overlays.selectedJobId, overlays.jobOutput).takeIf { overlays.jobsSheetOpen },
        ),
        overlayActions = ShellOverlayActions(
            onConfirmWorkspaceCreate = { name, directory ->
                overlays.workspaceCreateOpen = false
                chatViewModel.createWorkspace(name, directory)
            },
            onDismissWorkspaceCreate = { overlays.workspaceCreateOpen = false },
            onConfirmWorkspaceRename = { name ->
                val id = overlays.workspaceRenameId
                overlays.workspaceRenameId = null
                id?.let { chatViewModel.renameWorkspace(it, name) }
            },
            onDismissWorkspaceRename = { overlays.workspaceRenameId = null },
            onConfirmWorkspaceDelete = {
                val target = overlays.workspaceDeleteTarget
                overlays.workspaceDeleteTarget = null
                target?.let { chatViewModel.deleteWorkspace(it.id) }
            },
            onDismissWorkspaceDelete = { overlays.workspaceDeleteTarget = null },
            onDismissAttachmentPreview = { overlays.previewAttachment = null; overlays.previewRoot = null },
            onShareAttachmentPreview = {
                scope.launch {
                    overlays.previewAttachment?.let { attachment ->
                        graph.attachmentStore.shareIntent(attachment, overlays.previewRoot ?: attachmentRoot)
                    }?.let { context.startActivity(shareChooser(context, it, shareChooserTitle)) }
                }
            },
            onDismissUsage = { overlays.usageSheetOpen = false },
            onSelectJob = { id ->
                val same = overlays.selectedJobId == id
                overlays.selectedJobId = if (same) null else id
                overlays.jobOutput = null
                if (!same) scope.launch { overlays.jobOutput = graph.jobRegistry.read(id).output }
            },
            onCloseJobOutput = { overlays.selectedJobId = null; overlays.jobOutput = null },
            onKillJob = { id -> scope.launch { graph.jobRegistry.kill(id, null) } },
            onClearSettledJobs = { graph.jobRegistry.clearSettled() },
            onDismissJobs = { overlays.jobsSheetOpen = false },
        ),
        drawer = drawer,
        drawerActions = drawerActions,
    )
    if (overlays.attachmentSheetOpen) {
        AttachmentSourceSheet(
            onDismiss = { overlays.attachmentSheetOpen = false },
            onPick = { source ->
                overlays.attachmentSheetOpen = false
                when (source) {
                    AttachmentSource.IMAGE -> attachmentPickers.pickImages()
                    AttachmentSource.FILE -> attachmentPickers.pickFiles()
                    AttachmentSource.CAMERA -> overlays.cameraSheetOpen = true
                }
            },
        )
    }
    if (overlays.cameraSheetOpen) {
        CameraCaptureSheet(
            onDismiss = { overlays.cameraSheetOpen = false },
            onCaptured = { uri ->
                overlays.cameraSheetOpen = false
                chatViewModel.attach(uri)
            },
        )
    }
}
