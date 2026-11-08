package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

@Immutable
data class DdKeyValueItem(
    val label: String,
    val value: String,
)

@Composable
fun DdKeyValueGrid(
    items: List<DdKeyValueItem>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
) {
    val colors = deepDeepColors()
    val columnCount = columns.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DdSpacing.Lg),
    ) {
        items.chunked(columnCount).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DdSpacing.Lg),
            ) {
                rowItems.forEach { item ->
                    Column(Modifier.weight(1f)) {
                        DdText(
                            text = item.label,
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(DdSpacing.Xxs))
                        DdText(
                            text = item.value,
                            style = DdType.body,
                            color = colors.ink,
                            maxLines = 2,
                        )
                    }
                }
                repeat(columnCount - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
