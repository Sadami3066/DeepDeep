package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.CustomProviderEntry
import io.github.whywood.deepdeep.data.CustomProviderModel
import io.github.whywood.deepdeep.data.CustomProviderProtocol
import io.github.whywood.deepdeep.data.CustomProviderStore
import io.github.whywood.deepdeep.data.DiscoveredModel
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ThinkingLevel
import kotlinx.coroutines.launch

private data class ModelDraft(
    val id: String = "",
    val name: String = "",
    val contextWindow: String = "",
    val maxTokens: String = "",
    val supportsImages: Boolean = false,
    val reasoning: Boolean = false,
    val thinkingLevels: Set<ThinkingLevel> = emptySet(),
)

private data class ModelEditorRequest(val index: Int, val model: CustomProviderModel?)

private fun CustomProviderModel.toDraft(): ModelDraft = ModelDraft(
    id = id,
    name = name,
    contextWindow = contextWindow?.toString().orEmpty(),
    maxTokens = maxTokens?.toString().orEmpty(),
    supportsImages = supportsImages,
    reasoning = reasoning,
    thinkingLevels = thinkingLevels,
)

private fun DiscoveredModel.toDraft(): ModelDraft = ModelDraft(
    id = id,
    name = name.orEmpty(),
    contextWindow = contextWindow?.toString().orEmpty(),
    maxTokens = maxTokens?.toString().orEmpty(),
    supportsImages = supportsImages == true,
)

private fun ModelDraft.toModel(): CustomProviderModel = CustomProviderModel(
    id = id.trim(),
    name = name.trim().ifBlank { id.trim() },
    contextWindow = contextWindow.trim().toLongOrNull()?.takeIf { it > 0 },
    maxTokens = maxTokens.trim().toLongOrNull()?.takeIf { it > 0 },
    supportsImages = supportsImages,
    reasoning = reasoning,
    thinkingLevels = if (reasoning) thinkingLevels else emptySet(),
)

private fun formatContextWindow(value: Long?): String? = when {
    value == null || value <= 0 -> null
    value >= 1_000_000 -> "${value / 1_000_000}M"
    value >= 1000 -> "${value / 1000}K"
    else -> value.toString()
}

@Composable
fun CustomProviderEditorScreen(
    entry: CustomProviderEntry?,
    onBack: () -> Unit,
    onSave: (CustomProviderEntry) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDiscoverModels: (suspend (String, String, CustomProviderProtocol) -> Result<List<DiscoveredModel>>)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var name by remember(entry?.id) { mutableStateOf(entry?.name.orEmpty()) }
    var protocol by remember(entry?.id) {
        mutableStateOf(CustomProviderProtocol.fromValue(entry?.api.orEmpty()) ?: CustomProviderProtocol.OpenAiCompletions)
    }
    var baseUrl by remember(entry?.id) { mutableStateOf(entry?.baseUrl.orEmpty()) }
    var apiKey by remember(entry?.id) { mutableStateOf(entry?.apiKey.orEmpty()) }
    val models = remember(entry?.id) {
        mutableStateListOf<ModelDraft>().apply {
            entry?.models?.forEach { model -> add(model.toDraft()) }
        }
    }
    var protocolExpanded by remember { mutableStateOf(false) }
    var modelEditor by remember(entry?.id) { mutableStateOf<ModelEditorRequest?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var discoverError by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val trimmedName = name.trim()
    val trimmedBaseUrl = baseUrl.trim()
    val trimmedApiKey = apiKey.trim()
    val error = when {
        trimmedName.isEmpty() -> R.string.settings_custom_provider_error_name
        trimmedBaseUrl.isEmpty() -> R.string.settings_custom_provider_error_base_url
        !trimmedBaseUrl.startsWith("http://") && !trimmedBaseUrl.startsWith("https://") ->
            R.string.settings_custom_provider_error_base_url_scheme
        trimmedApiKey.isEmpty() -> R.string.settings_custom_provider_error_api_key
        models.isEmpty() -> R.string.settings_custom_provider_error_models
        models.any { it.id.trim().isEmpty() } -> R.string.settings_custom_provider_error_model_id
        else -> null
    }

    fun save() {
        if (error != null) return
        onSave(
            CustomProviderEntry(
                id = entry?.id ?: CustomProviderStore.newId(),
                name = trimmedName,
                api = protocol.value,
                baseUrl = trimmedBaseUrl,
                apiKey = trimmedApiKey,
                models = models.map { it.toModel() },
            ),
        )
    }

    val editor = modelEditor
    if (editor != null) {
        CustomProviderModelScreen(
            model = editor.model,
            onBack = { modelEditor = null },
            onSave = { model ->
                if (editor.index < models.size) {
                    models[editor.index] = model.toDraft()
                } else {
                    models.add(model.toDraft())
                }
                modelEditor = null
            },
            onDelete = if (editor.index < models.size) {
                {
                    models.removeAt(editor.index)
                    modelEditor = null
                }
            } else {
                null
            },
        )
        return
    }

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.weight(1f))
            DdTextAction(
                text = stringResource(R.string.settings_custom_provider_save),
                onClick = ::save,
                enabled = error == null,
            )
        },
        modifier = modifier,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(
                    if (entry == null) {
                        R.string.settings_custom_provider_new
                    } else {
                        R.string.settings_custom_provider_edit
                    },
                ),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Lg))
            FormField(
                label = stringResource(R.string.settings_custom_provider_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.settings_custom_provider_name_hint),
            )
            Text(
                text = stringResource(R.string.settings_custom_provider_protocol),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(6.dp))
            DdGroupCard {
                ProtocolRow(
                    protocol = protocol,
                    expanded = protocolExpanded,
                    onExpandedChange = { protocolExpanded = it },
                    onSelect = { selected ->
                        protocol = selected
                        protocolExpanded = false
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            FormField(
                label = stringResource(R.string.settings_custom_provider_base_url),
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = stringResource(R.string.settings_custom_provider_base_url_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            FormField(
                label = stringResource(R.string.settings_custom_provider_api_key),
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = stringResource(R.string.settings_custom_provider_api_key_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                visualTransformation = PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(DdSpacing.Md))
            DdSectionLabel(
                stringResource(R.string.settings_custom_provider_models),
                Modifier.padding(start = DdSpacing.Xs),
            )
            Spacer(Modifier.height(10.dp))
            DdGroupCard {
                models.forEachIndexed { index, model ->
                    DdListRow(
                        title = model.name.trim().ifBlank {
                            model.id.trim().ifBlank { stringResource(R.string.settings_custom_provider_model_untitled) }
                        },
                        subtitle = modelCapabilitySummary(model),
                        onClick = { modelEditor = ModelEditorRequest(index, model.toModel()) },
                        showChevron = true,
                    )
                    DdHairline()
                }
                DdListRow(
                    title = stringResource(R.string.settings_custom_provider_model_add),
                    icon = DdIcons.Plus,
                    onClick = { modelEditor = ModelEditorRequest(models.size, null) },
                )
                if (onDiscoverModels != null) {
                    DdHairline()
                    DdListRow(
                        title = stringResource(
                            if (discovering) {
                                R.string.settings_custom_provider_discovering
                            } else {
                                R.string.settings_custom_provider_discover
                            },
                        ),
                        subtitle = stringResource(R.string.settings_custom_provider_discover_hint),
                        icon = DdIcons.Search,
                        enabled = !discovering,
                        onClick = {
                            discoverError = null
                            if (trimmedBaseUrl.isEmpty() || trimmedApiKey.isEmpty()) {
                                discoverError = R.string.settings_custom_provider_discover_missing
                            } else {
                                discovering = true
                                scope.launch {
                                    val result = onDiscoverModels(trimmedBaseUrl, trimmedApiKey, protocol)
                                    discovering = false
                                    result.fold(
                                        onSuccess = { discovered ->
                                            if (discovered.isEmpty()) {
                                                discoverError = R.string.settings_custom_provider_discover_empty
                                            } else {
                                                val existing = models.associateBy { it.id.trim() }
                                                val next = discovered
                                                    .map { model -> model.copy(id = model.id.trim()) }
                                                    .filter { model -> model.id.isNotEmpty() }
                                                    .distinctBy { model -> model.id }
                                                    .map { model -> existing[model.id] ?: model.toDraft() }
                                                models.clear()
                                                models.addAll(next)
                                            }
                                        },
                                        onFailure = {
                                            discoverError = R.string.settings_custom_provider_discover_failed
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
            if (discoverError != null) {
                Spacer(Modifier.height(DdSpacing.Md))
                Text(
                    text = stringResource(discoverError!!),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            if (error != null) {
                Spacer(Modifier.height(DdSpacing.Md))
                Text(
                    text = stringResource(error),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            if (onDelete != null) {
                Spacer(Modifier.height(DdSpacing.Lg))
                DdGroupCard {
                    DdListRow(
                        title = stringResource(R.string.settings_custom_provider_delete),
                        icon = DdIcons.Trash,
                        onClick = { confirmDelete = true },
                    )
                }
            }
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }

    if (confirmDelete && onDelete != null) {
        DdDialog(
            onDismissRequest = { confirmDelete = false },
            title = stringResource(R.string.settings_custom_provider_delete),
            content = {
                Text(
                    text = stringResource(R.string.settings_custom_provider_delete_confirm, entry?.name.orEmpty()),
                    style = DdType.body,
                    color = colors.ink,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.settings_custom_provider_cancel),
                    onClick = { confirmDelete = false },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.settings_custom_provider_delete_action),
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    color = colors.danger,
                )
            },
        )
    }
}

@Composable
private fun modelCapabilitySummary(model: ModelDraft): String? {
    val parts = mutableListOf<String>()
    formatContextWindow(model.contextWindow.trim().toLongOrNull())?.let(parts::add)
    if (model.supportsImages) parts.add(stringResource(R.string.settings_custom_provider_capability_image))
    if (model.reasoning) {
        parts.add(
            if (model.thinkingLevels.isEmpty()) {
                stringResource(R.string.settings_custom_provider_capability_thinking)
            } else {
                stringResource(
                    R.string.settings_custom_provider_capability_thinking_levels,
                    thinkingLevelsSummary(model.thinkingLevels),
                )
            },
        )
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun thinkingLevelsSummary(levels: Set<ThinkingLevel>): String = buildString {
    ThinkingLevel.entries.forEach { level ->
        if (level in levels) {
            if (isNotEmpty()) append('/')
            append(thinkingLevelLabel(level))
        }
    }
}

@Composable
private fun ProtocolRow(
    protocol: CustomProviderProtocol,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (CustomProviderProtocol) -> Unit,
) {
    val colors = deepDeepColors()
    DdDropdown(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
        anchor = {
            DdPressable(
                onClick = { onExpandedChange(true) },
                modifier = Modifier.fillMaxWidth(),
                contentDescription = stringResource(R.string.settings_custom_provider_protocol),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = DdSpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = protocolLabel(protocol),
                        style = DdType.body,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
                }
            }
        },
    ) {
        CustomProviderProtocol.entries.forEach { option ->
            DdMenuItem(
                text = protocolLabel(option),
                onClick = { onSelect(option) },
                selected = option == protocol,
                trailing = if (option == protocol) {
                    {
                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun protocolLabel(protocol: CustomProviderProtocol): String = stringResource(
    when (protocol) {
        CustomProviderProtocol.OpenAiCompletions -> R.string.settings_custom_provider_protocol_openai_completions
        CustomProviderProtocol.OpenAiResponses -> R.string.settings_custom_provider_protocol_openai_responses
        CustomProviderProtocol.AnthropicMessages -> R.string.settings_custom_provider_protocol_anthropic
    },
)

@Composable
internal fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Text(
        text = label,
        style = DdType.bodySmall,
        color = deepDeepColors().inkTertiary,
    )
    Spacer(Modifier.height(6.dp))
    DdTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
    )
    Spacer(Modifier.height(12.dp))
}
