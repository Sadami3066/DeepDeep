package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun DdGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    CompositionLocalProvider(LocalDdRowHorizontalPadding provides 18.dp) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface)
                .padding(vertical = 2.dp),
            content = content,
        )
    }
}

@Composable
fun DdGroupCard(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    CompositionLocalProvider(LocalDdRowHorizontalPadding provides DdSpacing.RowHorizontal) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(DdShapes.Card)
                .background(colors.surface),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DdSpacing.Card, vertical = DdSpacing.Md),
            ) {
                header()
            }
            DdRowDivider(leadingInset = DdSpacing.Card)
            Column(
                modifier = Modifier.padding(vertical = DdSpacing.Xxs),
                content = content,
            )
            if (footer != null) {
                DdRowDivider(leadingInset = DdSpacing.Card)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DdSpacing.Card, vertical = DdSpacing.Md),
                ) {
                    footer()
                }
            }
        }
    }
}
