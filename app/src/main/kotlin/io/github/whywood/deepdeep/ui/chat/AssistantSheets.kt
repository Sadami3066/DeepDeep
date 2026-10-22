package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ThinkingLevel

@Composable
internal fun AssistantModelSheet(
    options: List<ModelOption>,
    modelOverride: String?,
    configuredModel: String?,
    onSelect: (String?) -> Unit,
    onClose: () -> Unit,
) {
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(title = stringResource(R.string.assistant_model_sheet_title), onClose = onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            ModelRow(
                title = stringResource(R.string.assistant_model_follow),
                subtitle = configuredModel?.let { modelDisplayName(options, it, it) }
                    ?: stringResource(R.string.assistant_model_unconfigured),
                selected = modelOverride == null,
                onClick = { onSelect(null) },
            )
            options.forEach { option ->
                DdHairline()
                ModelRow(
                    title = option.displayName,
                    selected = modelOverride == option.id,
                    onClick = { onSelect(option.id) },
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ModelRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = DdType.label,
                color = if (selected) colors.accent else colors.ink,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
            }
        }
        if (selected) {
            DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 20.dp)
        }
    }
}

@Composable
internal fun AssistantThinkingSheet(
    levels: List<ThinkingLevel>,
    selected: ThinkingLevel,
    onSelect: (ThinkingLevel) -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(title = stringResource(R.string.assistant_thinking_title), onClose = onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            levels.forEachIndexed { index, level ->
                DdListRow(
                    title = thinkingLevelLabel(level),
                    onClick = { onSelect(level) },
                    trailing = {
                        if (level == selected) {
                            DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 20.dp)
                        }
                    },
                )
                if (index != levels.lastIndex) DdHairline()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}
