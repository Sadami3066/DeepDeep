package io.github.whywood.deepdeep.ui.mcp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.McpConfigStore
import kotlinx.coroutines.launch

@Composable
internal fun McpImportSheet(
    store: McpConfigStore,
    onDismiss: () -> Unit,
    onReloadSessions: () -> Unit,
    onCommitted: () -> Unit,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    val template = stringResource(R.string.mcp_import_template)
    val fallbackError = stringResource(R.string.mcp_import_error)
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    fun submit() {
        if (importing) return
        error = null
        importing = true
        scope.launch {
            val result = runCatching { store.importServers(text) }
            importing = false
            result.fold(
                onSuccess = {
                    onReloadSessions()
                    onCommitted()
                    onDismiss()
                },
                onFailure = { failure ->
                    error = failure.message?.takeIf { it.isNotBlank() } ?: fallbackError
                },
            )
        }
    }

    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
            DdSheetHeader(
                title = stringResource(R.string.mcp_import_title),
                onClose = onDismiss,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                DdTextArea(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    placeholder = template,
                    modifier = Modifier.padding(top = 8.dp),
                    minHeight = 260.dp,
                    maxHeight = 360.dp,
                    testTag = "mcp_import_json",
                )
                error?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 12.dp),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                DdPrimaryButton(
                    text = stringResource(R.string.mcp_import_save),
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().testTag("mcp_import_save"),
                    enabled = !importing && text.isNotBlank(),
                )
            }
        }
    }
}
