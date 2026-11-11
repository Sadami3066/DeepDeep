package io.github.whywood.deepdeep.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.CustomProviderModel
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
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

private val THINKING_LEVELS = listOf("low", "medium", "high", "max")

@Composable
fun CustomProviderModelScreen(
    model: CustomProviderModel?,
    onBack: () -> Unit,
    onSave: (CustomProviderModel) -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var id by remember(model?.id) { mutableStateOf(model?.id.orEmpty()) }
    var name by remember(model?.id) { mutableStateOf(model?.name.orEmpty()) }
    var contextWindow by remember(model?.id) { mutableStateOf(model?.contextWindow?.toString().orEmpty()) }
    var maxTokens by remember(model?.id) { mutableStateOf(model?.maxTokens?.toString().orEmpty()) }
    var supportsImages by remember(model?.id) { mutableStateOf(model?.supportsImages ?: false) }
    var reasoning by remember(model?.id) { mutableStateOf(model?.reasoning ?: false) }
    var maxThinkingLevel by remember(model?.id) {
        mutableStateOf(model?.maxThinkingLevel?.takeIf { it in THINKING_LEVELS } ?: THINKING_LEVELS.last())
    }
    var levelExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val trimmedId = id.trim()
    val contextValue = contextWindow.trim().toLongOrNull()
    val maxTokensValue = maxTokens.trim().toLongOrNull()
    val error = when {
        trimmedId.isEmpty() -> R.string.settings_custom_provider_error_model_id
        contextWindow.isNotBlank() && (contextValue == null || contextValue <= 0) ->
            R.string.settings_custom_provider_model_error_context
        maxTokens.isNotBlank() && (maxTokensValue == null || maxTokensValue <= 0) ->
            R.string.settings_custom_provider_model_error_max_tokens
        else -> null
    }

    fun save() {
        if (error != null) return
        onSave(
            CustomProviderModel(
                id = trimmedId,
                name = name.trim().ifBlank { trimmedId },
                contextWindow = contextValue,
                maxTokens = maxTokensValue,
                supportsImages = supportsImages,
                reasoning = reasoning,
                maxThinkingLevel = if (reasoning) maxThinkingLevel else null,
            ),
        )
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
                    if (model == null) {
                        R.string.settings_custom_provider_model_new
                    } else {
                        R.string.settings_custom_provider_model_edit
                    },
                ),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_custom_provider_model_hint),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(DdSpacing.Lg))
            FormField(
                label = stringResource(R.string.settings_custom_provider_model_id),
                value = id,
                onValueChange = { id = it },
                placeholder = stringResource(R.string.settings_custom_provider_model_id_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            FormField(
                label = stringResource(R.string.settings_custom_provider_model_name),
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.settings_custom_provider_model_name_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            FormField(
                label = stringResource(R.string.settings_custom_provider_model_context),
                value = contextWindow,
                onValueChange = { contextWindow = it.filter { value -> value.isDigit() } },
                placeholder = stringResource(R.string.settings_custom_provider_model_context_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            FormField(
                label = stringResource(R.string.settings_custom_provider_model_max_tokens),
                value = maxTokens,
                onValueChange = { maxTokens = it.filter { value -> value.isDigit() } },
                placeholder = stringResource(R.string.settings_custom_provider_model_max_tokens_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(DdSpacing.Md))
            DdGroupCard {
                DdListRow(
                    title = stringResource(R.string.settings_custom_provider_model_images),
                    trailing = {
                        DdSwitch(checked = supportsImages, onCheckedChange = { supportsImages = it })
                    },
                )
                DdHairline()
                DdListRow(
                    title = stringResource(R.string.settings_custom_provider_model_reasoning),
                    trailing = {
                        DdSwitch(
                            checked = reasoning,
                            onCheckedChange = { enabled ->
                                reasoning = enabled
                                if (!enabled) levelExpanded = false
                            },
                        )
                    },
                )
                if (reasoning) {
                    DdHairline()
                    ThinkingLevelRow(
                        level = maxThinkingLevel,
                        expanded = levelExpanded,
                        onExpandedChange = { levelExpanded = it },
                        onSelect = { selected ->
                            maxThinkingLevel = selected
                            levelExpanded = false
                        },
                    )
                }
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
                        title = stringResource(R.string.settings_custom_provider_model_delete),
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
            title = stringResource(R.string.settings_custom_provider_model_delete),
            content = {
                Text(
                    text = stringResource(
                        R.string.settings_custom_provider_delete_confirm,
                        name.trim().ifBlank { id.trim() },
                    ),
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
private fun ThinkingLevelRow(
    level: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = deepDeepColors()
    DdDropdown(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
        anchor = {
            DdPressable(
                onClick = { onExpandedChange(true) },
                modifier = Modifier.fillMaxWidth(),
                contentDescription = stringResource(R.string.settings_custom_provider_model_thinking_level),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = DdSpacing.Lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_custom_provider_model_thinking_level),
                        style = DdType.body,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = thinkingLevelLabel(level),
                        style = DdType.body,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.width(6.dp))
                    DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
                }
            }
        },
    ) {
        THINKING_LEVELS.forEach { option ->
            DdMenuItem(
                text = thinkingLevelLabel(option),
                onClick = { onSelect(option) },
                selected = option == level,
                trailing = if (option == level) {
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
private fun thinkingLevelLabel(level: String): String = stringResource(
    when (level) {
        "low" -> R.string.settings_custom_provider_model_level_low
        "medium" -> R.string.settings_custom_provider_model_level_medium
        "high" -> R.string.settings_custom_provider_model_level_high
        else -> R.string.settings_custom_provider_model_level_max
    },
)
