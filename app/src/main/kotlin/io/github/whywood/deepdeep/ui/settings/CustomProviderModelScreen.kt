package io.github.whywood.deepdeep.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.CustomProviderModel
import io.github.whywood.deepdeep.design.DdChip
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ThinkingLevel

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
    var thinkingLevels by remember(model?.id) { mutableStateOf(model?.thinkingLevels.orEmpty()) }
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
    val missingThinkingLevel = reasoning && thinkingLevels.isEmpty()

    fun save() {
        if (error != null || missingThinkingLevel) return
        onSave(
            CustomProviderModel(
                id = trimmedId,
                name = name.trim().ifBlank { trimmedId },
                contextWindow = contextValue,
                maxTokens = maxTokensValue,
                supportsImages = supportsImages,
                reasoning = reasoning,
                thinkingLevels = if (reasoning) thinkingLevels else emptySet(),
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
                        DdSwitch(checked = reasoning, onCheckedChange = { reasoning = it })
                    },
                )
                if (reasoning) {
                    DdHairline()
                    ThinkingLevelPicker(
                        selected = thinkingLevels,
                        onToggle = { level ->
                            thinkingLevels = if (level in thinkingLevels) {
                                thinkingLevels - level
                            } else {
                                thinkingLevels + level
                            }
                        },
                        showError = missingThinkingLevel,
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
            Spacer(Modifier.height(DdSpacing.Lg))
            DdPrimaryButton(
                text = stringResource(R.string.settings_custom_provider_save),
                onClick = ::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = error == null && !missingThinkingLevel,
            )
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
private fun ThinkingLevelPicker(
    selected: Set<ThinkingLevel>,
    onToggle: (ThinkingLevel) -> Unit,
    showError: Boolean,
) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalDdRowHorizontalPadding.current)
            .padding(top = 14.dp, bottom = DdSpacing.Md),
    ) {
        Text(
            text = stringResource(R.string.settings_custom_provider_model_thinking_levels),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DdSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(DdSpacing.Sm),
        ) {
            ThinkingLevel.entries.forEach { level ->
                DdChip(
                    text = thinkingLevelLabel(level),
                    onClick = { onToggle(level) },
                    selected = level in selected,
                )
            }
        }
        if (showError) {
            Spacer(Modifier.height(DdSpacing.Sm))
            Text(
                text = stringResource(R.string.settings_custom_provider_model_error_thinking_levels),
                style = DdType.bodySmall,
                color = colors.danger,
            )
        }
    }
}

@Composable
internal fun thinkingLevelLabel(level: ThinkingLevel): String = stringResource(
    when (level) {
        ThinkingLevel.OFF -> R.string.settings_custom_provider_model_level_off
        ThinkingLevel.LOW -> R.string.settings_custom_provider_model_level_low
        ThinkingLevel.MEDIUM -> R.string.settings_custom_provider_model_level_medium
        ThinkingLevel.HIGH -> R.string.settings_custom_provider_model_level_high
        ThinkingLevel.XHIGH -> R.string.settings_custom_provider_model_level_xhigh
        ThinkingLevel.MAX -> R.string.settings_custom_provider_model_level_max
    },
)
