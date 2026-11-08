package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalDdRowHorizontalPadding = staticCompositionLocalOf { 0.dp }

@Composable
fun DdPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    DdPressable(
        onClick = onClick,
        interactionSource = remember { MutableInteractionSource() },
        modifier = modifier,
        shape = shape,
        background = background,
        enabled = enabled,
        contentDescription = contentDescription,
        content = content,
    )
}

@Composable
fun DdPressable(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    hapticFeedbackType: HapticFeedbackType? = null,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val pressed by interactionSource.collectIsPressedAsState()
    val wash = animateFloatAsState(
        targetValue = if (pressed && enabled) DdAlpha.Press else 0f,
        animationSpec = if (pressed) snap() else tween(DdMotion.PRESS_OUT, easing = DdMotion.EaseOut),
        label = "press-wash",
    )
    val haptics = LocalHapticFeedback.current
    val hapticType = hapticFeedbackType
    val semantics = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    Box(
        modifier = modifier
            .then(semantics)
            .clip(shape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colors.ink.copy(alpha = DdAlpha.Ripple)),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    if (hapticType != null) {
                        haptics.performHapticFeedback(hapticType)
                    }
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val alpha = wash.value
                    if (alpha > 0f) drawRect(colors.ink.copy(alpha = alpha))
                },
        )
        content()
    }
}

@Composable
fun DdIconButton(
    icon: DdIcon,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = deepDeepColors().ink,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
) {
    DdPressable(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        background = background,
        enabled = enabled,
        contentDescription = contentDescription,
    ) {
        DdIcon(icon = icon, tint = tint, size = iconSize)
    }
}

@Composable
fun DdWordmark(
    modifier: Modifier = Modifier,
    color: Color = deepDeepColors().ink,
    dotColor: Color = deepDeepColors().accent,
    style: TextStyle = DdType.wordmark,
) {
    Row(modifier) {
        Text(
            text = "deepdeep",
            style = style,
            color = color,
        )
        Text(
            text = ".",
            modifier = Modifier.alignByBaseline(),
            style = style,
            color = dotColor,
        )
    }
}

@Composable
fun DdHairline(
    modifier: Modifier = Modifier,
    color: Color = deepDeepColors().hairline.copy(alpha = 0.45f),
) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun DdRowDivider(
    modifier: Modifier = Modifier,
    leadingInset: Dp = 0.dp,
    color: Color = deepDeepColors().hairline.copy(alpha = 0.4f),
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = leadingInset)
            .height(1.dp)
            .background(color),
    )
}

@Composable
fun DdSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = DdType.micro,
        color = deepDeepColors().inkTertiary,
    )
}

@Composable
fun DdTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = deepDeepColors().accent,
    enabled: Boolean = true,
) {
    DdPressable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(8.dp),
        enabled = enabled,
        contentDescription = null,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 2.dp),
            style = DdType.label,
            color = if (enabled) color else color.copy(alpha = 0.38f),
            maxLines = 1,
        )
    }
}

@Composable
fun DdListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: DdIcon? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    subtitleMaxLines: Int = 1,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val alpha = if (enabled) 1f else 0.42f
    DdPressable(
        onClick = onClick ?: {},
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        enabled = onClick != null && enabled,
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = LocalDdRowHorizontalPadding.current, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                DdIcon(icon = icon, tint = colors.inkSecondary.copy(alpha = alpha), size = 20.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = DdType.body,
                    color = colors.ink.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary.copy(alpha = alpha),
                        maxLines = subtitleMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else if (value != null) {
                Text(
                    text = value,
                    modifier = Modifier.padding(start = 12.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary.copy(alpha = alpha),
                    maxLines = 1,
                )
            }
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                DdIcon(
                    icon = DdIcons.ChevronRight,
                    tint = colors.inkTertiary.copy(alpha = 0.55f * alpha),
                    size = 15.dp,
                )
            }
        }
    }
}

@Composable
fun DdListRow(
    title: String,
    leadingIcon: DdIcon,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showChevron: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    iconContainerColor: Color = deepDeepColors().wash,
    iconTint: Color = deepDeepColors().inkSecondary,
) {
    val colors = deepDeepColors()
    val alpha = if (enabled) 1f else DdAlpha.Disabled
    DdPressable(
        onClick = onClick ?: {},
        modifier = modifier.fillMaxWidth(),
        shape = DdShapes.Row,
        enabled = onClick != null && enabled,
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = LocalDdRowHorizontalPadding.current, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconBox(
                icon = leadingIcon,
                size = 40.dp,
                iconSize = 20.dp,
                containerColor = iconContainerColor,
                tint = iconTint.copy(alpha = alpha),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = DdType.body,
                    color = colors.ink.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                DdIcon(
                    icon = DdIcons.ChevronRight,
                    tint = colors.inkTertiary.copy(alpha = DdAlpha.Muted * alpha),
                    size = 15.dp,
                )
            }
        }
    }
}

@Composable
fun DdChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: DdIcon? = null,
    enabled: Boolean = true,
) {
    val colors = deepDeepColors()
    val semantics = if (selected) Modifier.semantics { this.selected = true } else Modifier
    DdPressable(
        onClick = onClick,
        modifier = modifier.height(36.dp).then(semantics),
        shape = CircleShape,
        background = if (selected) colors.accentSoft else colors.wash,
        enabled = enabled,
        contentDescription = text,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                DdIcon(
                    icon = icon,
                    tint = if (selected) colors.accent else colors.inkSecondary,
                    size = 15.dp,
                )
            }
            Text(
                text = text,
                style = DdType.chip,
                color = if (selected) colors.accent else colors.inkSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun DdPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        background = if (enabled) colors.accent else colors.wash,
        enabled = enabled,
        contentDescription = text,
    ) {
        Text(
            text = text,
            style = DdType.button,
            color = if (enabled) colors.onAccent else colors.inkTertiary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdSheet(
    onDismiss: () -> Unit,
    fullBleed: Boolean = false,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = containerColor ?: if (fullBleed) Color.Black else colors.raised,
        contentColor = if (fullBleed) Color.White else colors.ink,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = if (fullBleed) {
            null
        } else {
            {
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.hairline),
                )
            }
        },
        contentWindowInsets = if (fullBleed) {
            { WindowInsets(0, 0, 0, 0) }
        } else {
            { BottomSheetDefaults.windowInsets }
        },
    ) {
        Column(
            modifier = if (fullBleed) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
            },
            content = content,
        )
    }
}

@Composable
fun DdSheetHeader(
    title: String,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = DdType.heading,
            color = colors.ink,
        )
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onClose,
            contentDescription = stringResource(R.string.dd_close),
            tint = colors.inkSecondary,
            iconSize = 20.dp,
        )
    }
}
