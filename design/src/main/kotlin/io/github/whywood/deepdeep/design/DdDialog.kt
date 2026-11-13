package io.github.whywood.deepdeep.design

import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Suppress("DEPRECATION")
@Composable
fun DdDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = deepDeepColors()
    val motionEnabled = LocalDdMotionEnabled.current
    var revealed by remember { mutableStateOf(!motionEnabled) }
    LaunchedEffect(Unit) { revealed = true }
    val reveal by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "dd-dialog-reveal",
    )
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = LocalView.current
        LaunchedEffect(dialogView) {
            (dialogView.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = reveal
                        val scale = 0.96f + 0.04f * reveal
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.raised)
                    .padding(24.dp),
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = DdType.heading,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                content()
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
        }
    }
}
