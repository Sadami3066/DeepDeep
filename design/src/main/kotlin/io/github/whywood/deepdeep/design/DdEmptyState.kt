package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
