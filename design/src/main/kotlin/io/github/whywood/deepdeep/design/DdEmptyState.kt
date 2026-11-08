package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DdEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = DdType.bodySmall,
        color = deepDeepColors().inkTertiary,
    )
}

@Composable
fun DdEmptyState(
    text: String,
    icon: DdIcon,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = deepDeepColors()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = DdSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DdSpacing.Md),
    ) {
        DdIconBox(
            icon = icon,
            size = 48.dp,
            iconSize = 24.dp,
            shape = CircleShape,
            containerColor = colors.wash,
            tint = colors.inkTertiary,
        )
        DdText(
            text = text,
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            action()
        }
    }
}
