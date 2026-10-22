package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.07f else 0f,
        animationSpec = tween(DdMotion.PRESS, easing = DdMotion.EaseOut),
        label = "press-wash",
    )
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
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().background(colors.ink.copy(alpha = wash)))
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
    color: Color = deepDeepColors().hairline,
) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
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
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val alpha = if (enabled) 1f else 0.42f
    val clickable = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = 60.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            DdIcon(icon = icon, tint = colors.inkSecondary.copy(alpha = alpha), size = 20.dp)
            Spacer(Modifier.width(14.dp))
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
                    maxLines = 1,
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
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary.copy(alpha = alpha), size = 16.dp)
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = colors.raised,
        contentColor = colors.ink,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.hairline),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
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
            contentDescription = "关闭",
            tint = colors.inkSecondary,
            iconSize = 20.dp,
        )
    }
}
