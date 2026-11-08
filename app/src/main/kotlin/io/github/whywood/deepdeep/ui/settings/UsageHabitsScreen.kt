package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.whywood.deepdeep.data.OverlayEntryMode
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun UsageHabitsScreen(
    mode: OverlayEntryMode,
    onBack: () -> Unit,
    onModeChange: (OverlayEntryMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
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
                text = stringResource(R.string.settings_usage_habits),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            DdGroupCard {
                EntryModeRow(mode = mode, onModeChange = onModeChange)
            }
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }
}

@Composable
private fun EntryModeRow(
    mode: OverlayEntryMode,
    onModeChange: (OverlayEntryMode) -> Unit,
) {
    val colors = deepDeepColors()
    var expanded by remember { mutableStateOf(false) }
    DdDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        anchor = {
            DdPressable(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().testTag("usage_habits_entry_mode"),
                shape = DdShapes.Row,
                contentDescription = stringResource(R.string.usage_habits_entry_mode),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DdIcon(icon = DdIcons.HandClick, tint = colors.inkSecondary, size = 20.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.usage_habits_entry_mode),
                        modifier = Modifier.weight(1f),
                        style = DdType.body,
                        color = colors.ink,
                        maxLines = 1,
                    )
                    Text(
                        text = overlayEntryModeLabel(mode),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
                }
            }
        },
    ) {
        OverlayEntryMode.entries.forEach { option ->
            DdMenuItem(
                text = overlayEntryModeLabel(option),
                onClick = {
                    expanded = false
                    if (option != mode) onModeChange(option)
                },
                selected = option == mode,
                trailing = if (option == mode) {
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
internal fun overlayEntryModeLabel(mode: OverlayEntryMode): String = stringResource(
    when (mode) {
        OverlayEntryMode.VOICE -> R.string.usage_habits_mode_voice
        OverlayEntryMode.CAMERA -> R.string.usage_habits_mode_camera
        OverlayEntryMode.NONE -> R.string.usage_habits_mode_none
    },
)
