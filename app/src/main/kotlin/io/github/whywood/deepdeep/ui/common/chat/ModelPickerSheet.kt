package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.runtime.ThinkingLevel



@Composable
internal fun ModelPickerSheet(
    providerId: String,
    modelId: String,
    configured: Boolean,
    providers: List<ProviderSummary>,
    models: List<ModelOption>,
    thinkingLevel: ThinkingLevel,
    thinkingLevels: List<ThinkingLevel>,
    onDismiss: () -> Unit,
    onProviderChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onManageProviders: () -> Unit,
) {
    val colors = deepDeepColors()
    var showProviders by remember { mutableStateOf(false) }
    var showThinking by remember { mutableStateOf(false) }
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(
            title = stringResource(R.string.assistant_model_sheet),
            onClose = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.Xxl),
        ) {
            Spacer(Modifier.height(DdSpacing.Sm))
            if (providers.isNotEmpty()) {
                val providerName = providers.firstOrNull { it.id == providerId }?.name ?: providerId
                DdDropdown(
                    expanded = showProviders,
                    onDismissRequest = { showProviders = false },
                    anchor = {
                        DdListRow(
                            title = stringResource(R.string.assistant_model_provider),
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = providerName,
                                        style = DdType.bodySmall,
                                        color = colors.inkTertiary,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
                                }
                            },
                            onClick = { showProviders = true },
                            modifier = Modifier.testTag("assistant_provider_row"),
                        )
                    },
                ) {
                    providers.forEach { provider ->
                        DdMenuItem(
                            text = provider.name,
                            onClick = {
                                showProviders = false
                                onProviderChange(provider.id)
                            },
                            selected = provider.id == providerId,
                            trailing = if (provider.id == providerId) {
                                {
                                    DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 16.dp)
                                }
                            } else {
                                null
                            },
                            testTag = "assistant_provider_${provider.id}",
                        )
                    }
                }
                DdHairline()
            }

            if (!configured) {
                DdListRow(
                    title = stringResource(R.string.assistant_model_unconfigured),
                    onClick = null,
                    enabled = false,
                )
                DdHairline()
            } else {
                if (thinkingLevels.any { it != ThinkingLevel.OFF }) {
                    DdDropdown(
                        expanded = showThinking,
                        onDismissRequest = { showThinking = false },
                        anchor = {
                            DdListRow(
                                title = stringResource(R.string.assistant_thinking_sheet),
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = thinkingLabel(thinkingLevel),
                                            style = DdType.bodySmall,
                                            color = colors.inkTertiary,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
                                    }
                                },
                                onClick = { showThinking = true },
                                modifier = Modifier.testTag("assistant_thinking_row"),
                            )
                        },
                    ) {
                        thinkingLevels.forEach { level ->
                            DdMenuItem(
                                text = thinkingLabel(level),
                                onClick = {
                                    showThinking = false
                                    onDismiss()
                                    onThinkingLevelChange(level)
                                },
                                selected = level == thinkingLevel,
                                trailing = if (level == thinkingLevel) {
                                    {
                                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 16.dp)
                                    }
                                } else {
                                    null
                                },
                                testTag = "assistant_thinking_${level.name.lowercase()}",
                            )
                        }
                    }
                }
                models.forEachIndexed { index, option ->
                    DdListRow(
                        title = option.displayName,
                        onClick = {
                            onDismiss()
                            onModelChange(option.id)
                        },
                        trailing = {
                            if (option.id == modelId) {
                                DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 20.dp)
                            }
                        },
                    )
                    if (index != models.lastIndex) DdHairline()
                }
            }
            Spacer(Modifier.height(28.dp))
        }
        DdHairline(Modifier.padding(horizontal = DdSpacing.Xxl))
        DdListRow(
            title = stringResource(R.string.assistant_model_manage_providers),
            onClick = {
                onDismiss()
                onManageProviders()
            },
            showChevron = true,
            modifier = Modifier.padding(horizontal = DdSpacing.Xxl),
        )
        Spacer(Modifier.height(DdSpacing.Xl))
    }
}
