package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.WebSearchSettingsStore
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun WebSearchSettingsScreen(
    configured: Map<String, Boolean>,
    onSave: (String, String) -> Unit,
    onClear: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var editing by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        },
        modifier = modifier.testTag("web_search_settings"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.web_search_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.web_search_summary),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(24.dp))
            DdSectionLabel(stringResource(R.string.web_search_section_providers))
            Spacer(Modifier.height(8.dp))
            SettingsGroup {
                WebSearchProviderEntry(
                    id = WebSearchSettingsStore.EXA,
                    configured = configured[WebSearchSettingsStore.EXA] == true,
                    onEdit = { editing = WebSearchSettingsStore.EXA },
                )
                WebSearchProviderEntry(
                    id = WebSearchSettingsStore.TAVILY,
                    configured = configured[WebSearchSettingsStore.TAVILY] == true,
                    onEdit = { editing = WebSearchSettingsStore.TAVILY },
                )
                WebSearchProviderEntry(
                    id = WebSearchSettingsStore.BRAVE,
                    configured = configured[WebSearchSettingsStore.BRAVE] == true,
                    onEdit = { editing = WebSearchSettingsStore.BRAVE },
                )
                WebSearchProviderEntry(
                    id = WebSearchSettingsStore.SERPER,
                    configured = configured[WebSearchSettingsStore.SERPER] == true,
                    onEdit = { editing = WebSearchSettingsStore.SERPER },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.web_search_note),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
    val provider = editing
    if (provider != null) {
        DdDialog(
            onDismissRequest = { editing = null },
            title = stringResource(webSearchProviderName(provider)),
            content = {
                Column {
                    DdTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = stringResource(R.string.web_search_key_hint),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        testTag = "web_search_key_$provider",
                    )
                    if (configured[provider] == true) {
                        Spacer(Modifier.height(12.dp))
                        DdTextAction(
                            text = stringResource(R.string.web_search_clear),
                            onClick = {
                                onClear(provider)
                                editing = null
                            },
                            color = colors.warning,
                        )
                    }
                }
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.web_search_cancel),
                    onClick = { editing = null },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.web_search_save),
                    onClick = {
                        val value = draft.trim()
                        if (value.isNotEmpty()) {
                            onSave(provider, value)
                            draft = ""
                            editing = null
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun WebSearchProviderEntry(
    id: String,
    configured: Boolean,
    onEdit: () -> Unit,
) {
    DdListRow(
        title = stringResource(webSearchProviderName(id)),
        subtitle = stringResource(webSearchProviderDescription(id)),
        subtitleMaxLines = 2,
        value = stringResource(if (configured) R.string.web_search_configured else R.string.web_search_not_configured),
        onClick = onEdit,
        modifier = Modifier.testTag("web_search_$id"),
    )
}

private fun webSearchProviderName(id: String): Int = when (id) {
    WebSearchSettingsStore.EXA -> R.string.web_search_exa
    WebSearchSettingsStore.TAVILY -> R.string.web_search_tavily
    WebSearchSettingsStore.BRAVE -> R.string.web_search_brave
    else -> R.string.web_search_serper
}

private fun webSearchProviderDescription(id: String): Int = when (id) {
    WebSearchSettingsStore.EXA -> R.string.web_search_exa_summary
    WebSearchSettingsStore.TAVILY -> R.string.web_search_tavily_summary
    WebSearchSettingsStore.BRAVE -> R.string.web_search_brave_summary
    else -> R.string.web_search_serper_summary
}
