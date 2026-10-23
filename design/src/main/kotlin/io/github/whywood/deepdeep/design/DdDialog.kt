package io.github.whywood.deepdeep.design

import android.view.WindowManager
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
