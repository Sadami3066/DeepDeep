package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdAvatar
import io.github.whywood.deepdeep.design.DdElevation
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconBox
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdRadius
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdText
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTheme
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.design.LocalDeepDeepColors
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
fun DesignSystemScreen(onClose: () -> Unit) {
    var santorini by remember { mutableStateOf(true) }
    val colors = if (santorini) DdTheme.lightSantorini() else deepDeepColors()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalDeepDeepColors provides colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.paper)
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DdIconButton(
                        icon = DdIcons.Close,
                        onClick = onClose,
                        contentDescription = "Close",
                        tint = colors.ink,
                    )
                    Spacer(Modifier.width(DdSpacing.Xs))
                    Column(Modifier.weight(1f)) {
                        DdText(text = "Design System", style = DdType.heading, color = colors.ink)
                        DdText(text = "Debug preview", style = DdType.bodySmall, color = colors.inkTertiary)
                    }
                    DdTextAction(
                        text = if (santorini) "Santorini" else "DeepDeep",
                        onClick = { santorini = !santorini },
                        color = colors.accent,
                    )
                }
                DdHairline(color = colors.hairline.copy(alpha = DdAlpha.Hairline))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = DdSpacing.ScreenHorizontal),
                ) {
                    PaletteSection(colors)
                    TypeSection(colors)
                    SpacingSection(colors)
                    RadiusSection(colors)
                    ElevationSection(colors)
                    AlphaSection(colors)
                    MotionSection(colors)
                    ComponentsSection(colors)
                    Spacer(Modifier.height(DdSpacing.Huge))
                }
            }
        }
    }
}

@Composable
private fun PaletteSection(colors: DeepDeepColors) {
    ShowcaseSection(title = "Palette", colors = colors) {
        val swatches = listOf(
            "paper" to colors.paper,
            "surface" to colors.surface,
            "raised" to colors.raised,
            "ink" to colors.ink,
            "inkSecondary" to colors.inkSecondary,
            "inkTertiary" to colors.inkTertiary,
            "accent" to colors.accent,
            "accentSoft" to colors.accentSoft,
            "wash" to colors.wash,
            "hairline" to colors.hairline,
            "danger" to colors.danger,
            "success" to colors.success,
            "warning" to colors.warning,
        )
        swatches.forEach { (name, color) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(DdShapes.Pill)
                        .background(color)
                        .border(1.dp, colors.hairline, DdShapes.Pill),
                )
                Spacer(Modifier.width(DdSpacing.Md))
                DdText(text = name, modifier = Modifier.weight(1f), style = DdType.label, color = colors.ink)
                DdText(text = colorHex(color), style = DdType.numeric, color = colors.inkTertiary)
            }
        }
    }
}

@Composable
private fun TypeSection(colors: DeepDeepColors) {
    ShowcaseSection(title = "Type", colors = colors) {
        val samples = listOf(
            "wordmark" to DdType.wordmark,
            "display" to DdType.display,
            "pageTitle" to DdType.pageTitle,
            "heading" to DdType.heading,
            "body" to DdType.body,
            "bodySmall" to DdType.bodySmall,
            "label" to DdType.label,
            "chip" to DdType.chip,
            "button" to DdType.button,
            "micro" to DdType.micro,
            "numeric" to DdType.numeric,
        )
        samples.forEach { (name, style) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdText(
                    text = "Aa 你好 123",
                    modifier = Modifier.weight(1f),
                    style = style,
                    color = colors.ink,
                    maxLines = 1,
                )
                DdText(text = name, style = DdType.micro, color = colors.inkTertiary)
            }
        }
    }
}

@Composable
private fun SpacingSection(colors: DeepDeepColors) {
    ShowcaseSection(title = "Spacing", colors = colors) {
        val steps = listOf(
            "Xs" to DdSpacing.Xs,
            "Sm" to DdSpacing.Sm,
            "Md" to DdSpacing.Md,
            "Lg" to DdSpacing.Lg,
            "Xl" to DdSpacing.Xl,
            "Xxl" to DdSpacing.Xxl,
            "Xxxl" to DdSpacing.Xxxl,
            "Huge" to DdSpacing.Huge,
        )
        steps.forEach { (name, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdText(text = name, modifier = Modifier.width(56.dp), style = DdType.label, color = colors.inkSecondary)
                Box(
                    Modifier
                        .width(value)
                        .height(8.dp)
                        .clip(DdShapes.Pill)
                        .background(colors.accent.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(DdSpacing.Md))
                DdText(text = "${value.value.toInt()}", style = DdType.numeric, color = colors.inkTertiary)
            }
        }
    }
}

@Composable
private fun RadiusSection(colors: DeepDeepColors) {
    ShowcaseSection(title = "Radius", colors = colors) {
        Row(horizontalArrangement = Arrangement.spacedBy(DdSpacing.Lg)) {
            val radii = listOf(
                "8" to DdRadius.Small,
                "14" to DdRadius.Segmented,
                "18" to DdRadius.Card,
                "28" to DdRadius.Sheet,
            )
            radii.forEach { (label, radius) ->
                val shape = RoundedCornerShape(radius)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(shape)
                            .background(colors.accentSoft)
                            .border(1.dp, colors.accent.copy(alpha = 0.25f), shape),
                    )
                    Spacer(Modifier.height(DdSpacing.Xs))
                    DdText(text = label, style = DdType.numeric, color = colors.inkTertiary)
                }
            }
        }
    }
}

@Composable
private fun ElevationSection(colors: DeepDeepColors) {
    ShowcaseSection(title = "Elevation", colors = colors) {
        val steps = listOf(
            "2" to DdElevation.Indicator,
            "3" to DdElevation.Thumb,
            "8" to DdElevation.Soft,
            "10" to DdElevation.Composer,
            "12" to DdElevation.Overlay,
            "14" to DdElevation.Panel,
            "15" to DdElevation.ComposerFocused,
            "16" to DdElevation.Menu,
        )
        steps.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(DdSpacing.Xxl)) {
                row.forEach { (label, elevation) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .shadow(elevation, DdShapes.Medium, clip = false)
                                .clip(DdShapes.Medium)
                                .background(colors.surface),
                        )
                        Spacer(Modifier.height(DdSpacing.Xs))
                        DdText(text = label, style = DdType.numeric, color = colors.inkTertiary)
                    }
                }
            }
            Spacer(Modifier.height(DdSpacing.Lg))
        }
    }
}

@Composable
private fun AlphaSection(colors: DeepDeepColors) {
    ShowcaseSection(title = "Alpha", colors = colors) {
        val steps = listOf(
            "Disabled" to DdAlpha.Disabled,
            "Muted" to DdAlpha.Muted,
            "Secondary" to DdAlpha.Secondary,
            "Hairline" to DdAlpha.Hairline,
            "Divider" to DdAlpha.Divider,
            "Border" to DdAlpha.Border,
            "Press" to DdAlpha.Press,
            "Ripple" to DdAlpha.Ripple,
            "TrackLight" to DdAlpha.TrackLight,
            "TrackDark" to DdAlpha.TrackDark,
            "IndicatorDark" to DdAlpha.IndicatorDark,
        )
        steps.forEach { (name, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdText(text = name, modifier = Modifier.weight(1f), style = DdType.label, color = colors.inkSecondary)
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(DdShapes.Pill)
                        .background(colors.ink.copy(alpha = value))
                        .border(1.dp, colors.hairline, DdShapes.Pill),
                )
                Spacer(Modifier.width(DdSpacing.Md))
                DdText(
                    text = "${(value * 100).toInt()}%",
                    modifier = Modifier.width(40.dp),
                    style = DdType.numeric,
                    color = colors.inkTertiary,
                )
            }
        }
    }
}

@Composable
private fun MotionSection(colors: DeepDeepColors) {
    ShowcaseSection(title = "Motion", colors = colors) {
        val durations = listOf(
            "PRESS" to DdMotion.PRESS,
            "PRESS_IN" to DdMotion.PRESS_IN,
            "PRESS_OUT" to DdMotion.PRESS_OUT,
            "FAST" to DdMotion.FAST,
            "BASE" to DdMotion.BASE,
            "ENTER" to DdMotion.ENTER,
            "PAGE" to DdMotion.PAGE,
            "DRAWER" to DdMotion.DRAWER,
            "SHEET" to DdMotion.SHEET,
            "NAV" to DdMotion.NAV,
            "SCREEN" to DdMotion.SCREEN,
            "STAGGER" to DdMotion.STAGGER,
        )
        durations.forEach { (name, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdText(text = name, modifier = Modifier.weight(1f), style = DdType.label, color = colors.inkSecondary)
                DdText(text = "${value}ms", style = DdType.numeric, color = colors.inkTertiary)
            }
        }
        Spacer(Modifier.height(DdSpacing.Md))
        DdText(
            text = "EaseOut cubic(0.22, 1, 0.36, 1) · EaseInOut cubic(0.65, 0, 0.35, 1)",
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun ComponentsSection(colors: DeepDeepColors) {
    var selectedTone by remember { mutableStateOf("温和") }
    var searchQuery by remember { mutableStateOf("") }
    val pressSource = remember { MutableInteractionSource() }
    ShowcaseSection(title = "Components", colors = colors) {
        ComponentGroup(title = "DdText · DdIconBox", colors = colors) {
            DdText(
                text = "DdText 封装 DdType、颜色、行数与对齐。",
                style = DdType.body,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Md))
            Row(horizontalArrangement = Arrangement.spacedBy(DdSpacing.Md)) {
                DdIconBox(icon = DdIcons.Brain)
                DdIconBox(icon = DdIcons.Plug, size = 44.dp, iconSize = 22.dp)
                DdIconBox(
                    icon = DdIcons.Shield,
                    size = 48.dp,
                    iconSize = 24.dp,
                    containerColor = colors.accentSoft,
                    tint = colors.accent,
                )
            }
        }
        ComponentGroup(title = "DdPressable · DdPrimaryButton · DdTextAction", colors = colors) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DdSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdPrimaryButton(text = "Primary", onClick = {})
                DdTextAction(text = "Text action", onClick = {}, color = colors.accent)
            }
            Spacer(Modifier.height(DdSpacing.Md))
            DdPressable(
                onClick = {},
                interactionSource = pressSource,
                hapticFeedbackType = HapticFeedbackType.LongPress,
                shape = DdShapes.Medium,
                background = colors.accentSoft,
                modifier = Modifier.height(44.dp),
            ) {
                DdText(
                    text = "DdPressable · shared interactionSource · haptic",
                    modifier = Modifier.padding(horizontal = DdSpacing.Lg),
                    style = DdType.label,
                    color = colors.accent,
                )
            }
        }
        ComponentGroup(title = "DdListRow", colors = colors) {
            DdGroupCard {
                DdListRow(
                    title = "新版列表行",
                    leadingIcon = DdIcons.Brain,
                    subtitle = "40dp 图标容器 + 单行说明",
                    onClick = {},
                    showChevron = true,
                )
                DdListRow(
                    title = "旧版列表行",
                    icon = DdIcons.Clock,
                    subtitle = "20dp 图标，签名保持不变",
                    onClick = {},
                    showChevron = true,
                )
            }
        }
        ComponentGroup(title = "DdGroupCard", colors = colors) {
            DdGroupCard(
                header = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DdText(
                            text = "Header slot",
                            modifier = Modifier.weight(1f),
                            style = DdType.label,
                            color = colors.inkSecondary,
                        )
                        DdTextAction(text = "Action", onClick = {}, color = colors.accent)
                    }
                },
                footer = {
                    DdText(text = "Footer slot", style = DdType.bodySmall, color = colors.inkTertiary)
                },
            ) {
                DdListRow(title = "第一行", icon = DdIcons.Image, onClick = {})
                DdListRow(title = "第二行", icon = DdIcons.File, onClick = {})
            }
        }
        ComponentGroup(title = "DdSegmentedControl", colors = colors) {
            DdSegmentedControl(
                options = listOf("温和", "可爱", "冷静"),
                selected = selectedTone,
                onSelect = { selectedTone = it },
                item = { option, isSelected ->
                    DdText(
                        text = option,
                        style = DdType.chip,
                        color = if (isSelected) colors.ink else colors.inkTertiary,
                        maxLines = 1,
                    )
                },
            )
        }
        ComponentGroup(title = "DdSearchField", colors = colors) {
            DdSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "搜索组件",
                onSearch = {},
                clearContentDescription = "清除",
                trailing = {
                    DdIcon(icon = DdIcons.Settings, tint = colors.inkTertiary, size = 18.dp)
                },
            )
        }
        ComponentGroup(title = "DdEmptyState", colors = colors) {
            DdEmptyState(
                text = "没有内容",
                icon = DdIcons.Folder,
                action = { DdTextAction(text = "重试", onClick = {}, color = colors.accent) },
            )
        }
        ComponentGroup(title = "DdAvatar", colors = colors) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DdSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdAvatar()
                DdAvatar(initials = "DD", size = 44.dp)
                DdAvatar(size = 44.dp, placeholderIcon = DdIcons.Sparkles, onClick = {})
                DdAvatar(size = 48.dp, onClick = {})
            }
        }
    }
}

@Composable
private fun ShowcaseSection(
    title: String,
    colors: DeepDeepColors,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(DdSpacing.Xxl))
    DdText(text = title, style = DdType.pageTitle, color = colors.ink)
    Spacer(Modifier.height(DdSpacing.Md))
    content()
}

@Composable
private fun ComponentGroup(
    title: String,
    colors: DeepDeepColors,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(DdSpacing.Lg))
    DdText(text = title, style = DdType.label, color = colors.inkSecondary)
    Spacer(Modifier.height(DdSpacing.Sm))
    content()
}

private fun colorHex(color: Color): String = "#%08X".format(color.toArgb())
