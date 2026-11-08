package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    CompositionLocalProvider(LocalDdRowHorizontalPadding provides DdSpacing.Lg) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(DdShapes.Row)
                .background(colors.surface)
                .padding(vertical = DdSpacing.Xs),
            content = content,
        )
    }
}

@Composable
internal fun StatusPill(text: String, active: Boolean) {
    val colors = deepDeepColors()
    Text(
        text = text,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) colors.accentSoft else colors.wash)
            .padding(horizontal = 10.dp, vertical = DdSpacing.Xs),
        style = DdType.chip,
        color = if (active) colors.accent else colors.inkTertiary,
        maxLines = 1,
    )
}
