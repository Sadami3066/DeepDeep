package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
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
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun FontScreen(
    current: DdFontStyle,
    serifChinese: Boolean,
    onBack: () -> Unit,
    onSelect: (DdFontStyle) -> Unit,
    onSerifChineseChange: (Boolean) -> Unit,
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
            val chinese = LocalConfiguration.current.locales[0].language == "zh"
            SettingsGroup {
                DdFontStyle.entries.forEachIndexed { index, style ->
                    if (index > 0) {
                        DdHairline()
                    }
                    FontPreviewRow(
                        style = style,
                        label = if (style == DdFontStyle.SYSTEM) {
                            stringResource(R.string.settings_font_system)
                        } else {
                            style.displayName
                        },
                        selected = style == current,
                        onClick = { onSelect(style) },
                    )
                }
            }
            if (chinese) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_font_chinese_title),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    style = DdType.chip,
                    color = colors.inkTertiary,
                )
                SettingsGroup {
                    DdListRow(
                        title = stringResource(R.string.settings_font_chinese_system),
                        modifier = Modifier.testTag("font_chinese_switch"),
                        trailing = {
                            DdSwitch(
                                checked = !serifChinese,
                                onCheckedChange = { checked -> onSerifChineseChange(!checked) },
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FontPreviewRow(
    style: DdFontStyle,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val family = DdFonts.familyFor(style)
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
                .heightIn(min = 104.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = DdType.label.copy(fontFamily = family),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentLabel,
                        style = DdType.chip,
                        color = colors.accent,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(2.dp))
                    DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 16.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.widthIn(max = 168.dp).padding(top = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "我思故我在",
                    style = DdType.body.copy(fontFamily = family, fontSize = 18.sp, lineHeight = 26.sp),
                    color = colors.inkSecondary,
                    maxLines = 1,
                )
                Text(
                    text = "I think, therefore I am.",
                    style = DdType.bodySmall.copy(fontFamily = family, fontSize = 13.sp, lineHeight = 19.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
