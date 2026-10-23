package io.github.whywood.deepdeep.ui.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    CompositionLocalProvider(LocalDdRowHorizontalPadding provides 16.dp) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
internal fun InitialBadge(name: String, size: Dp = 36.dp) {
    val colors = deepDeepColors()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            style = DdType.label,
            color = colors.accent,
            maxLines = 1,
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = DdType.chip,
        color = if (active) colors.accent else colors.inkTertiary,
        maxLines = 1,
    )
}

@Composable
internal fun PlaceholderRow(
    icon: DdIcon,
    @StringRes title: Int,
) {
    val context = LocalContext.current
    DdListRow(
        icon = icon,
        title = stringResource(title),
        value = stringResource(R.string.settings_placeholder),
        onClick = {
            Toast.makeText(context, R.string.settings_placeholder, Toast.LENGTH_SHORT).show()
        },
    )
}
