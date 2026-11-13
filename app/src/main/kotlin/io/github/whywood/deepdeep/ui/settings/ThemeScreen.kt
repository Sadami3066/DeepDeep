package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.theme.ThemeChoice
import io.github.whywood.deepdeep.theme.ThemeOption
import io.github.whywood.deepdeep.theme.ThemeSource

@Composable
fun ThemeScreen(
    builtIn: List<ThemeOption>,
    fromPlugins: List<ThemeOption>,
    current: ThemeChoice,
    onBack: () -> Unit,
    onSelect: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("theme_settings"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            ThemeSectionLabel(stringResource(R.string.settings_theme_builtin))
            SettingsGroup {
                builtIn.forEachIndexed { index, option ->
                    if (index > 0) DdHairline()
                    ThemeRow(
                        option = option,
                        selected = option.id == current,
                        onClick = { onSelect(option.id) },
                    )
                }
            }
            ThemeSectionLabel(stringResource(R.string.settings_theme_from_plugins))
            if (fromPlugins.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_theme_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = DdSpacing.Sm),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            } else {
                SettingsGroup {
                    fromPlugins.forEachIndexed { index, option ->
                        if (index > 0) DdHairline()
                        ThemeRow(
                            option = option,
                            selected = option.id == current,
                            onClick = { onSelect(option.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }
}

@Composable
private fun ThemeSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
        style = DdType.micro,
        color = deepDeepColors().inkTertiary,
    )
}

@Composable
private fun ThemeRow(
    option: ThemeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val currentLabel = stringResource(R.string.settings_theme_current)
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DdShapes.Row,
        contentDescription = if (selected) "${option.label} $currentLabel" else option.label,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = DdSpacing.Lg, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option.label,
                        style = DdType.body,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) {
                        Spacer(Modifier.width(DdSpacing.Xs))
                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 16.dp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondaryLabel(option),
                    style = DdType.chip,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(DdSpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                option.swatch.forEach { color ->
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, colors.hairline, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun secondaryLabel(option: ThemeOption): String {
    val tone = when {
        option.id == ThemeChoice.System -> stringResource(R.string.settings_theme_auto)
        option.dark -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_light)
    }
    val source = option.source as? ThemeSource.Plugin ?: return tone
    return "$tone · ${source.pluginName}"
}
