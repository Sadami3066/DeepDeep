package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun PluginDetailScreen(
    manager: PluginManagerFacade,
    pluginId: String,
    onBack: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenPluginPage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    pluginsRoot: File? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resolvedPluginsRoot = pluginsRoot ?: remember(context) { File(context.filesDir, "plugins") }
    val holder = remember(manager) { PluginsStateHolder(manager, scope) }
    val state by manager.state.collectAsStateWithLifecycle()
    var deleteOpen by remember { mutableStateOf(false) }
    val snapshot = state.snapshot(pluginId)

    LaunchedEffect(holder) { holder.load() }
    LifecycleResumeEffect(holder) {
        holder.load()
        onPauseOrDispose { }
    }
    val missing = pluginDetailMissing(state, pluginId)
    LaunchedEffect(missing) {
        if (missing) onBack()
    }
    val current = snapshot
    if (current == null) {
        PluginDetailLoading(onBack = onBack, modifier = modifier)
        return
    }
    val pages = manager.pagesOf(pluginId)
    val slotEntries = remember(state, pluginId) { manager.pluginSlotEntries(pluginId) }
    val detailSections = remember(slotEntries) { detailSectionEntries(slotEntries) }
    val failureGuard = manager.slotFailureGuard()
    val retryFailedText = stringResource(R.string.plugins_recovery_retry_failed)
    val rollbackFailedText = stringResource(R.string.plugins_recovery_rollback_failed)
    var recoveryBusy by remember(pluginId) { mutableStateOf(false) }
    var recoveryError by remember(pluginId) { mutableStateOf<String?>(null) }
    val onRetryActivation: () -> Unit = {
        if (!recoveryBusy) {
            recoveryBusy = true
            recoveryError = null
            scope.launch {
                val result = runCatching { manager.retryActivation(pluginId) }.getOrNull()
                if (result?.ok == true) {
                    recoveryError = null
                    holder.refresh()
                } else {
                    recoveryError = result?.error ?: retryFailedText
                }
                recoveryBusy = false
            }
        }
    }
    val onRollback: () -> Unit = {
        if (!recoveryBusy) {
            recoveryBusy = true
            recoveryError = null
            scope.launch {
                val rolledBack = runCatching { manager.rollbackPlugin(pluginId) }.getOrDefault(false)
                if (rolledBack) {
                    recoveryError = null
                    holder.refresh()
                } else {
                    recoveryError = rollbackFailedText
                }
                recoveryBusy = false
            }
        }
    }
    PluginDetailContent(
        snapshot = current,
        state = state,
        pluginsRoot = resolvedPluginsRoot,
        pages = pages,
        slotEntries = slotEntries,
        detailSections = detailSections,
        failureGuard = failureGuard,
        showConnect = pluginId in manager.setupPluginIds,
        onConnect = { manager.connect(pluginId) },
        recoveryBusy = recoveryBusy,
        recoveryError = recoveryError,
        onRetryActivation = onRetryActivation,
        onRollback = onRollback,
        deleteOpen = deleteOpen,
        onDeleteOpenChange = { deleteOpen = it },
        onBack = onBack,
        onOpenPermission = onOpenPermission,
        onOpenPluginPage = onOpenPluginPage,
        onRefresh = holder::refresh,
        onInstall = { holder.install(pluginId) },
        onUpdate = { holder.update(pluginId) },
        onUninstall = { holder.uninstall(pluginId) },
        onRunAction = { actionId -> holder.runAction(pluginId, actionId) },
        onSetting = { key, value -> holder.setSetting(pluginId, key, value) },
        modifier = modifier,
    )
}

@Composable
private fun PluginDetailLoading(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("plugin_detail_loading"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.plugins_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DdSpinner(size = 22.dp, strokeWidth = 2.dp)
        }
    }
}
