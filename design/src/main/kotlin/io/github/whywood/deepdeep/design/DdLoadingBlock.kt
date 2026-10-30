package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DdLoadingBlock(
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val colors = deepDeepColors()
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DdSpinner(color = colors.inkTertiary, size = 22.dp, strokeWidth = 2.dp)
            if (text != null) {
                Spacer(Modifier.height(10.dp))
                Text(text = text, style = DdType.bodySmall, color = colors.inkTertiary)
            }
        }
    }
}
