package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.remote.RemoteDeleteDialog
import io.github.whywood.deepdeep.ui.remote.RemoteGuideDialog
import io.github.whywood.deepdeep.ui.remote.RemotePairFailureDialog
import io.github.whywood.deepdeep.ui.remote.RemotePairSource
import io.github.whywood.deepdeep.ui.remote.RemotePairingDialog
import io.github.whywood.deepdeep.ui.remote.RemotePasteDialog
import io.github.whywood.deepdeep.ui.remote.RemoteRenameDialog
import io.github.whywood.deepdeep.ui.remote.RemoteScanSheet
import io.github.whywood.deepdeep.ui.remote.RemoteScreen
import io.github.whywood.deepdeep.ui.remote.RemoteStateHolder
import io.github.whywood.deepdeep.ui.remote.pairMessageRes
import io.github.whywood.deepdeep.ui.remote.requiresNewPairingCode

@Composable
internal fun RemoteRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val holder = remember(dependencies.remoteConnection, scope) {
        RemoteStateHolder(dependencies.remoteConnection, scope)
    }
    val statuses by holder.statuses.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.remote_pair_default_name)
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    val openComputer: (String) -> Unit = { computerId ->
        navigator.push(AppDestination.RemoteConnect(computerId))
    }

    LaunchedEffect(holder) { holder.refresh() }

    RemoteScreen(
        statuses = statuses,
        menuFor = holder.menuFor,
        onBack = onBack,
        onScan = holder::openScan,
        onPaste = holder::openPaste,
        onGuide = { guideOpen = true },
        onOpen = openComputer,
        onMenuOpen = holder::openMenu,
        onMenuDismiss = holder::dismissMenu,
        onRename = holder::startRename,
        onRepair = holder::openScan,
        onDelete = holder::startDelete,
    )

    if (guideOpen) {
        RemoteGuideDialog(onDismiss = { guideOpen = false })
    }

    if (holder.scanOpen) {
        RemoteScanSheet(
            invalid = holder.scanInvalid,
            onScanned = { raw -> holder.onScanned(raw, defaultName, openComputer) },
            onPaste = {
                holder.dismissScan()
                holder.openPaste()
            },
            onDismiss = holder::dismissScan,
        )
    }

    if (holder.pasteOpen) {
        RemotePasteDialog(
            invalid = holder.pasteInvalid,
            onDismiss = holder::dismissPaste,
            onConfirm = { raw -> holder.submitPaste(raw, defaultName, openComputer) },
        )
    }

    if (holder.pairing) {
        RemotePairingDialog()
    }

    holder.pairFailure?.let { failure ->
        val needsNewCode = failure.requiresNewPairingCode()
        RemotePairFailureDialog(
            message = stringResource(failure.pairMessageRes()),
            actionLabel = when {
                !needsNewCode -> stringResource(R.string.remote_connect_retry)
                holder.pairSource == RemotePairSource.Scan -> stringResource(R.string.remote_pair_rescan)
                else -> stringResource(R.string.remote_pair_repaste)
            },
            onAction = {
                if (needsNewCode) {
                    holder.restartPair()
                } else {
                    holder.retryPair(defaultName, openComputer)
                }
            },
            onDismiss = holder::dismissPairFailure,
        )
    }

    holder.renameFor?.let { computer ->
        RemoteRenameDialog(
            initialName = computer.name,
            onDismiss = holder::dismissRename,
            onConfirm = holder::confirmRename,
        )
    }

    holder.deleteFor?.let { computer ->
        RemoteDeleteDialog(
            computerName = computer.name,
            onDismiss = holder::dismissDelete,
            onConfirm = holder::confirmDelete,
        )
    }
}
