package io.github.whywood.deepdeep.design

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdInfoTooltip(
    text: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: DdIcon = DdIcons.Info,
) {
    val colors = deepDeepColors()
    val tooltipState = rememberTooltipState(isPersistent = false)
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(tooltipState) {
        snapshotFlow { tooltipState.isVisible }.collect { visible ->
            if (visible) {
                delay(TooltipVisibleMillis)
                tooltipState.dismiss()
            }
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                shape = DdShapes.Small,
                containerColor = colors.ink,
                contentColor = colors.paper,
            ) {
                Text(
                    text = text,
                    style = DdType.bodySmall,
                    color = colors.paper,
                )
            }
        },
        state = tooltipState,
        enableUserInput = false,
    ) {
        Box(
            modifier = modifier
                .size(44.dp)
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(color = colors.ink.copy(alpha = DdAlpha.Ripple)),
                    role = Role.Button,
                    onClick = {
                        if (tooltipState.isVisible) {
                            tooltipState.dismiss()
                        } else {
                            scope.launch { tooltipState.show() }
                        }
                    },
                    onLongClick = { scope.launch { tooltipState.show() } },
                ),
            contentAlignment = Alignment.Center,
        ) {
            DdIcon(
                icon = icon,
                tint = colors.inkTertiary,
                size = 18.dp,
                contentDescription = contentDescription ?: text,
            )
        }
    }
}

private const val TooltipVisibleMillis = 2000L
