package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.DdFonts
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun FontScreen(
    current: DdFontStyle,
    onBack: () -> Unit,
    onSelect: (DdFontStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("font_settings"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
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
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_font_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            SettingsGroup {
                DdFontStyle.entries.forEachIndexed { index, style ->
                    if (index > 0) {
                        DdHairline()
                    }
                    FontPreviewRow(
                        style = style,
                        selected = style == current,
                        onClick = { onSelect(style) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_font_preview_note),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FontPreviewRow(
    style: DdFontStyle,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val family = DdFonts.familyFor(style)
    val label = style.displayName
    val currentLabel = stringResource(R.string.settings_font_current)
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentDescription = if (selected) "$label $currentLabel" else label,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = DdType.label.copy(fontFamily = family),
                    color = colors.ink,
                    maxLines = 1,
                )
                Text(
                    text = "深潜 DeepDeep 0123",
                    style = DdType.body.copy(fontFamily = family),
                    color = colors.inkSecondary,
                    maxLines = 1,
                )
                Text(
                    text = "写代码、读文档、聊想法。The quick brown fox jumps over the lazy dog.",
                    style = DdType.bodySmall.copy(fontFamily = family, fontSize = 13.sp, lineHeight = 19.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = currentLabel,
                    style = DdType.chip,
                    color = colors.accent,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
                DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
            }
        }
    }
}
