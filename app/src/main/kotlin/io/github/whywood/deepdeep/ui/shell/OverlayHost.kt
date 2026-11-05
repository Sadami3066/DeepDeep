package io.github.whywood.deepdeep.ui.shell

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.data.AttachmentRef
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.PluginInteraction
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.JobSnapshot
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.SessionUsage
import io.github.whywood.deepdeep.ui.DeepDeepDrawer
import io.github.whywood.deepdeep.ui.chat.AttachmentPreviewDialog
import io.github.whywood.deepdeep.ui.chat.ChatUiState
import io.github.whywood.deepdeep.ui.chat.UsageSheet
import io.github.whywood.deepdeep.ui.jobs.JobsSheet
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.workspaces.DeleteWorkspaceDialog
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceNameDialog
import io.github.whywood.deepdeep.ui.workspaces.WorkspaceNameMode
import java.io.File

sealed interface ShellOverlay {
    data class WorkspaceCreate(val existingNames: List<String>) : ShellOverlay
    data class WorkspaceRename(val workspaceId: String, val initial: String, val existingNames: List<String>) : ShellOverlay
    data class WorkspaceDelete(val workspaceId: String, val name: String, val sessionCount: Int) : ShellOverlay
    data class AttachmentPreview(val attachment: AttachmentRef, val file: File) : ShellOverlay
    data class Usage(val usage: SessionUsage) : ShellOverlay
    data class Jobs(val jobs: List<JobSnapshot>, val selectedJobId: String?, val output: String?) : ShellOverlay
    data class PluginSetup(val interaction: PluginInteraction) : ShellOverlay
}

data class ShellOverlayState(
    val workspaceCreate: ShellOverlay.WorkspaceCreate? = null,
    val workspaceRename: ShellOverlay.WorkspaceRename? = null,
    val workspaceDelete: ShellOverlay.WorkspaceDelete? = null,
    val attachmentPreview: ShellOverlay.AttachmentPreview? = null,
    val usage: ShellOverlay.Usage? = null,
    val jobs: ShellOverlay.Jobs? = null,
    val pluginSetup: ShellOverlay.PluginSetup? = null,
)

data class ShellOverlayActions(
    val onConfirmWorkspaceCreate: (String) -> Unit, val onDismissWorkspaceCreate: () -> Unit,
    val onConfirmWorkspaceRename: (String) -> Unit, val onDismissWorkspaceRename: () -> Unit,
    val onConfirmWorkspaceDelete: () -> Unit, val onDismissWorkspaceDelete: () -> Unit,
    val onDismissAttachmentPreview: () -> Unit, val onShareAttachmentPreview: () -> Unit,
    val onDismissUsage: () -> Unit, val onSelectJob: (String) -> Unit,
    val onCloseJobOutput: () -> Unit, val onKillJob: (String) -> Unit,
    val onClearSettledJobs: () -> Unit, val onDismissJobs: () -> Unit,
)

data class ShellDrawerState(
    val open: Boolean,
    val sessions: List<ConversationSummary>,
    val workspaces: List<RuntimeWorkspace>,
    val pinnedWorkspaceIds: Set<String>, val expandedWorkspaceIds: Set<String>,
    val busySessionIds: Set<String>, val reminderSessionIds: Set<String>,
    val userName: String,
)
data class ShellDrawerActions(
    val onNavigate: (AppDestination) -> Unit, val onDismiss: () -> Unit,
    val onCreateWorkspace: () -> Unit, val onToggleWorkspaceExpanded: (String) -> Unit,
    val onToggleWorkspacePinned: (String) -> Unit, val onNewSessionInWorkspace: (String) -> Unit,
    val onWorkspaceSettings: (String) -> Unit, val onRenameWorkspace: (String) -> Unit,
    val onDeleteWorkspace: (String) -> Unit, val onConversation: (String) -> Unit,
    val onRename: (String, String) -> Unit, val onSetPinned: (String, Boolean) -> Unit,
    val onSetArchived: (String, Boolean) -> Unit, val onDelete: (String) -> Unit,
    val onSettings: () -> Unit,
)
fun resolveOverlays(state: ShellOverlayState): List<ShellOverlay> = buildList {
    state.workspaceCreate?.let(::add)
    state.workspaceRename?.let(::add)
    state.workspaceDelete?.let(::add)
    state.attachmentPreview?.let(::add)
    state.usage?.let(::add)
    state.jobs?.let(::add)
    state.pluginSetup?.let(::add)
}

fun resolveOverlay(state: ShellOverlayState): ShellOverlay? = resolveOverlays(state).lastOrNull()

@Composable
internal fun OverlayHost(
    overlays: ShellOverlayState,
    overlayActions: ShellOverlayActions,
    drawer: ShellDrawerState,
    drawerActions: ShellDrawerActions,
    pluginSetupContent: @Composable (PluginInteraction) -> Unit,
) {
    ShellDrawerHost(drawer, drawerActions)
    resolveOverlays(overlays).forEach { overlay ->
        key(overlay::class) {
            when (overlay) {
                is ShellOverlay.WorkspaceCreate -> WorkspaceNameDialog(
                    WorkspaceNameMode.CREATE, existingNames = overlay.existingNames,
                    onConfirm = overlayActions.onConfirmWorkspaceCreate,
                    onDismiss = overlayActions.onDismissWorkspaceCreate,
                )

                is ShellOverlay.WorkspaceRename -> WorkspaceNameDialog(
                    WorkspaceNameMode.RENAME, initial = overlay.initial, existingNames = overlay.existingNames,
                    onConfirm = overlayActions.onConfirmWorkspaceRename,
                    onDismiss = overlayActions.onDismissWorkspaceRename,
                )

                is ShellOverlay.WorkspaceDelete -> DeleteWorkspaceDialog(
                    overlay.name, overlay.sessionCount,
                    onConfirm = overlayActions.onConfirmWorkspaceDelete,
                    onDismiss = overlayActions.onDismissWorkspaceDelete,
                )

                is ShellOverlay.AttachmentPreview -> AttachmentPreviewDialog(
                    overlay.attachment, overlay.file,
                    onDismiss = overlayActions.onDismissAttachmentPreview,
                    onShare = overlayActions.onShareAttachmentPreview,
                )

                is ShellOverlay.Usage -> UsageSheet(overlay.usage, overlayActions.onDismissUsage)

                is ShellOverlay.Jobs -> JobsSheet(
                    overlay.jobs, overlay.selectedJobId, overlay.output,
                    onSelect = overlayActions.onSelectJob,
                    onCloseOutput = overlayActions.onCloseJobOutput,
                    onKill = overlayActions.onKillJob,
                    onClearSettled = overlayActions.onClearSettledJobs,
                    onDismiss = overlayActions.onDismissJobs,
                )

                is ShellOverlay.PluginSetup -> pluginSetupContent(overlay.interaction)
            }
        }
    }
}

@Composable
private fun ShellDrawerHost(state: ShellDrawerState, actions: ShellDrawerActions) {
    val colors = deepDeepColors()
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { 304.dp.toPx() }
    val transition = updateTransition(state.open, label = "drawer")
    val progress = transition.animateFloat(
        transitionSpec = { tween(DdMotion.BASE, easing = DdMotion.EaseOut) },
        label = "drawer-progress",
    ) { open -> if (open) 1f else 0f }
    var mounted by remember { mutableStateOf(state.open) }
    LaunchedEffect(Unit) {
        snapshotFlow { state.open || progress.value > 0.01f }
            .collect { visible -> mounted = visible }
    }
    Box(Modifier.fillMaxSize()) {
        if (mounted) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .drawBehind { drawRect(colors.scrim.copy(alpha = 0.32f * progress.value)) }
                    .then(
                        if (state.open) {
                            Modifier.clickable { actions.onDismiss() }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (mounted) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(304.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer { translationX = -drawerWidthPx * (1f - progress.value) },
            ) {
            DeepDeepDrawer(
                sessions = state.sessions,
                workspaces = state.workspaces,
                pinnedWorkspaceIds = state.pinnedWorkspaceIds,
                expandedWorkspaceIds = state.expandedWorkspaceIds,
                busySessionIds = state.busySessionIds,
                reminderSessionIds = state.reminderSessionIds,
                userName = state.userName,
                onOpenSearch = { actions.onNavigate(AppDestination.Search) },
                onOpenWorkshop = { actions.onNavigate(AppDestination.Workshop) },
                onOpenSkills = { actions.onNavigate(AppDestination.Skills) },
                onOpenMcp = { actions.onNavigate(AppDestination.Mcp) },
                onOpenPlugins = { actions.onNavigate(AppDestination.Plugins) },
                onOpenTimers = { actions.onNavigate(AppDestination.Timers) },
                onCreateWorkspace = actions.onCreateWorkspace,
                onToggleWorkspaceExpanded = actions.onToggleWorkspaceExpanded,
                onToggleWorkspacePinned = actions.onToggleWorkspacePinned,
                onNewSessionInWorkspace = actions.onNewSessionInWorkspace,
                onWorkspaceSettings = actions.onWorkspaceSettings,
                onRenameWorkspace = actions.onRenameWorkspace,
                onDeleteWorkspace = actions.onDeleteWorkspace,
                onConversation = actions.onConversation,
                onRename = actions.onRename,
                onSetPinned = actions.onSetPinned,
                onSetArchived = actions.onSetArchived,
                onDelete = actions.onDelete,
                onSettings = actions.onSettings,
            )
        }
    }
    }
}

@Composable
internal fun rememberFrozenChatState(live: ChatUiState, frozen: Boolean): ChatUiState {
    val snapshot = remember { mutableStateOf(live) }
    if (!frozen) {
        SideEffect { snapshot.value = live }
    }
    return if (frozen) snapshot.value else live
}
