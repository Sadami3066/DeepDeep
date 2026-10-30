package io.github.whywood.deepdeep.ui.workspaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AGENT_DOC_MAX_BYTES
import io.github.whywood.deepdeep.data.AgentDocStore
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import kotlinx.coroutines.launch

@Composable
fun AgentsDocSheet(
    workspace: RuntimeWorkspace,
    agentDocStore: AgentDocStore,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var hasDoc by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("") }
    var initial by remember { mutableStateOf("") }
    var draft by rememberSaveable(workspace.cwd) { mutableStateOf("") }
    var draftRestored by rememberSaveable(workspace.cwd) { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var discardedFromOutside by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var sheetEpoch by remember { mutableStateOf(0) }
    val defaultFileName = stringResource(R.string.agents_doc_default_file)

    LaunchedEffect(workspace.cwd) {
        val doc = agentDocStore.read(workspace.cwd)
        hasDoc = doc != null
        fileName = doc?.fileName ?: defaultFileName
        initial = doc?.content.orEmpty()
        if (!draftRestored) {
            draft = initial
            draftRestored = true
        }
        loading = false
    }

    val bytes = remember(draft) { draft.toByteArray(Charsets.UTF_8).size }
    val tooLarge = bytes > AGENT_DOC_MAX_BYTES
    val dirty = !loading && draft != initial

    val close: (Boolean) -> Unit = { fromOutside ->
        if (dirty) {
            discardedFromOutside = fromOutside
            confirmDiscard = true
        } else {
            onDismiss()
        }
    }

    val cancelDiscard: () -> Unit = {
        confirmDiscard = false
        if (discardedFromOutside) sheetEpoch += 1
    }

    val save: () -> Unit = {
        if (!saving) {
            if (tooLarge) {
                errorRes = R.string.agents_doc_too_large
            } else {
                saving = true
                errorRes = null
                scope.launch {
                    val ok = agentDocStore.save(workspace.cwd, draft)
                    saving = false
                    if (ok) {
                        onSaved()
                        onDismiss()
                    } else {
                        errorRes = R.string.agents_doc_save_failed
                    }
                }
            }
        }
    }

    val delete: () -> Unit = {
        if (!saving) {
            saving = true
            errorRes = null
            scope.launch {
                val ok = agentDocStore.delete(workspace.cwd)
                saving = false
                if (ok) {
                    onSaved()
                    onDismiss()
                } else {
                    errorRes = R.string.agents_doc_save_failed
                }
            }
        }
    }

    key(sheetEpoch) {
        DdSheet(onDismiss = { close(true) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.94f)
                    .testTag("agents_doc_sheet"),
            ) {
                DdSheetHeader(
                    title = stringResource(R.string.agents_doc_title),
                    onClose = { close(false) },
                )
                if (loading) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = fileName,
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(8.dp))
                        DdTextArea(
                            value = draft,
                            onValueChange = {
                                draft = it
                                errorRes = null
                            },
                            placeholder = stringResource(R.string.agents_doc_placeholder),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            minHeight = 140.dp,
                            maxHeight = 2000.dp,
                            testTag = "agents_doc_editor",
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.agents_doc_note),
                                modifier = Modifier.weight(1f),
                                style = DdType.bodySmall,
                                color = colors.inkTertiary,
                            )
                            Text(
                                text = stringResource(R.string.agents_doc_counter, draft.length),
                                style = DdType.numeric,
                                color = if (tooLarge) colors.danger else colors.inkTertiary,
                            )
                        }
                        if (tooLarge) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.agents_doc_too_large),
                                style = DdType.bodySmall,
                                color = colors.danger,
                            )
                        }
                        errorRes?.let { res ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(res),
                                style = DdType.bodySmall,
                                color = colors.danger,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasDoc) {
                        DdTextAction(
                            text = stringResource(R.string.agents_doc_delete),
                            onClick = { confirmDelete = true },
                            color = colors.danger,
                            modifier = Modifier.testTag("agents_doc_delete"),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    DdPrimaryButton(
                        text = stringResource(R.string.agents_doc_save),
                        onClick = save,
                        modifier = Modifier.width(148.dp).testTag("agents_doc_save"),
                        enabled = !loading && !saving && !tooLarge,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (confirmDiscard) {
        DdDialog(
            onDismissRequest = cancelDiscard,
            modifier = Modifier.testTag("agents_doc_discard_dialog"),
            title = stringResource(R.string.agents_doc_discard_title),
            content = {
                Text(
                    text = stringResource(R.string.agents_doc_discard_message),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.agents_doc_cancel),
                    onClick = cancelDiscard,
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.agents_doc_discard_confirm),
                    onClick = {
                        confirmDiscard = false
                        onDismiss()
                    },
                    color = colors.danger,
                    modifier = Modifier.testTag("agents_doc_discard_confirm"),
                )
            },
        )
    }

    if (confirmDelete) {
        DdDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.testTag("agents_doc_delete_dialog"),
            title = stringResource(R.string.agents_doc_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.agents_doc_delete_message),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.agents_doc_cancel),
                    onClick = { confirmDelete = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.agents_doc_delete_confirm),
                    onClick = {
                        confirmDelete = false
                        delete()
                    },
                    color = colors.danger,
                    modifier = Modifier.testTag("agents_doc_delete_confirm"),
                )
            },
        )
    }
}
