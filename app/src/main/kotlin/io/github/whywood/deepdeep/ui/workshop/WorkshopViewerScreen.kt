package io.github.whywood.deepdeep.ui.workshop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.webkit.WebViewFeature
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.SqliteWorkshopData
import io.github.whywood.deepdeep.runtime.WorkshopAgentGateway
import io.github.whywood.deepdeep.runtime.WorkshopEntry
import io.github.whywood.deepdeep.runtime.WorkshopStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkshopViewerScreen(
    store: WorkshopStore,
    creationId: String,
    onBack: () -> Unit,
    agentGateway: WorkshopAgentGateway? = null,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var entry by remember(creationId) { mutableStateOf<WorkshopEntry?>(null) }
    var loadKey by remember(creationId) { mutableIntStateOf(0) }
    var reloadKey by remember(creationId) { mutableIntStateOf(0) }
    var fatalError by remember(creationId) { mutableStateOf<String?>(null) }
    var canGoBack by remember(creationId) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf(false) }
    var renameName by remember(creationId) { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    val webViewState = remember(creationId) { WorkshopWebViewState() }
    val creation = (entry as? WorkshopEntry.Valid)?.creation
    val name = creation?.manifest?.name
    val valid = creation != null
    val data = remember(creationId, valid) {
        creation?.let { SqliteWorkshopData(store.dataFile(creationId)) }
    }
    val supported = remember {
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }
    val actionFailedText = stringResource(R.string.workshop_action_failed)

    LaunchedEffect(creationId, loadKey) {
        entry = runCatching { store.load(creationId) }.getOrElse { WorkshopEntry.Missing }
    }
    LaunchedEffect(entry) {
        if (entry == WorkshopEntry.Missing) {
            delay(120)
            onBack()
        }
    }
    DisposableEffect(data) {
        onDispose { data?.close() }
    }
    BackHandler(enabled = canGoBack) { webViewState.goBack() }

    fun renameCreation() {
        val next = renameName.trim()
        if (next.isEmpty() || working) return
        working = true
        scope.launch {
            val updated = runCatching { store.rename(creationId, next) }.getOrNull()
            working = false
            if (updated != null) {
                entry = WorkshopEntry.Valid(updated)
                renameOpen = false
            } else {
                dialogError = true
            }
        }
    }

    fun deleteCreation() {
        if (working) return
        working = true
        scope.launch {
            runCatching { data?.close() }
            val deleted = runCatching { store.delete(creationId) }.getOrDefault(false)
            working = false
            if (deleted) {
                deleteOpen = false
                onBack()
            } else {
                dialogError = true
                entry = null
                loadKey++
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("workshop_viewer"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.workshop_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = name ?: stringResource(R.string.workshop_loading),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                DdIconButton(
                    icon = DdIcons.DotsVertical,
                    onClick = { menuOpen = true },
                    contentDescription = stringResource(R.string.workshop_more),
                    modifier = Modifier.testTag("workshop_more"),
                    tint = colors.ink,
                    iconSize = 21.dp,
                )
                DdMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    menuWidth = 148.dp,
                ) {
                    DdMenuItem(
                        text = stringResource(R.string.workshop_reload),
                        icon = DdIcons.Refresh,
                        onClick = {
                            menuOpen = false
                            fatalError = null
                            reloadKey++
                        },
                        testTag = "workshop_reload",
                    )
                    DdMenuItem(
                        text = stringResource(R.string.workshop_rename),
                        icon = DdIcons.Compose,
                        onClick = {
                            if (working) return@DdMenuItem
                            menuOpen = false
                            dialogError = false
                            renameName = name.orEmpty()
                            renameOpen = true
                        },
                        testTag = "workshop_rename",
                    )
                    DdMenuItem(
                        text = stringResource(R.string.workshop_delete),
                        icon = DdIcons.Trash,
                        destructive = true,
                        onClick = {
                            if (working) return@DdMenuItem
                            menuOpen = false
                            dialogError = false
                            deleteOpen = true
                        },
                        testTag = "workshop_delete",
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                entry == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                    }
                }

                creation != null && data != null -> {
                    if (supported) {
                        WorkshopWebView(
                            creation = creation,
                            data = data,
                            agentGateway = agentGateway,
                            reloadKey = reloadKey,
                            onFatalError = { message -> fatalError = message },
                            onHistoryChanged = { canGoBack = it },
                            onRenderCrash = { canGoBack = false },
                            modifier = Modifier.fillMaxSize(),
                            state = webViewState,
                        )
                    } else {
                        UnsupportedWebView()
                    }
                }

                creation != null -> UnsupportedWebView()

                else -> InvalidEntry(onRetry = {
                    entry = null
                    loadKey++
                })
            }
            val failure = fatalError
            if (failure != null && creation != null) {
                PageErrorOverlay(
                    message = failure,
                    onRetry = {
                        fatalError = null
                        reloadKey++
                    },
                )
            }
        }
    }

    if (renameOpen && creation != null) {
        DdDialog(
            onDismissRequest = { if (!working) renameOpen = false },
            title = stringResource(R.string.workshop_rename_title),
            content = {
                DdTextField(
                    value = renameName,
                    onValueChange = { renameName = it.take(40) },
                    placeholder = stringResource(R.string.workshop_rename_hint),
                    testTag = "workshop_rename_input",
                )
                if (dialogError) {
                    Spacer(Modifier.height(10.dp))
                    Text(actionFailedText, style = DdType.bodySmall, color = colors.danger)
                }
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.workshop_cancel),
                    onClick = { renameOpen = false },
                    color = colors.inkSecondary,
                    enabled = !working,
                )
                DdTextAction(
                    text = stringResource(R.string.workshop_rename_save),
                    onClick = { renameCreation() },
                    modifier = Modifier.testTag("workshop_rename_save"),
                    enabled = !working && renameName.isNotBlank(),
                )
            },
        )
    }

    if (deleteOpen && creation != null) {
        DdDialog(
            onDismissRequest = { if (!working) deleteOpen = false },
            title = stringResource(R.string.workshop_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.workshop_delete_message, name.orEmpty()),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
                if (dialogError) {
                    Spacer(Modifier.height(10.dp))
                    Text(actionFailedText, style = DdType.bodySmall, color = colors.danger)
                }
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.workshop_cancel),
                    onClick = { deleteOpen = false },
                    color = colors.inkSecondary,
                    enabled = !working,
                )
                DdTextAction(
                    text = stringResource(R.string.workshop_delete_confirm),
                    onClick = { deleteCreation() },
                    modifier = Modifier.testTag("workshop_delete_confirm"),
                    color = colors.danger,
                    enabled = !working,
                )
            },
        )
    }
}

@Composable
private fun PageErrorOverlay(message: String, onRetry: () -> Unit) {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.workshop_page_failed),
            style = DdType.label,
            color = colors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        DdPrimaryButton(
            text = stringResource(R.string.workshop_page_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("workshop_page_retry"),
        )
    }
}

@Composable
private fun UnsupportedWebView() {
    val colors = deepDeepColors()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.workshop_webview_unsupported),
            modifier = Modifier.padding(horizontal = 32.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InvalidEntry(onRetry: () -> Unit) {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.workshop_page_failed),
            style = DdType.label,
            color = colors.ink,
        )
        Spacer(Modifier.height(24.dp))
        DdPrimaryButton(
            text = stringResource(R.string.workshop_page_retry),
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("workshop_invalid_retry"),
        )
    }
}
