package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DdSheetScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: DdIcon? = null,
    actions: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        DdSheetHeader(
            title = title,
            onClose = onClose,
            leadingIcon = leadingIcon,
            subtitle = subtitle,
            actions = actions,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DdSpacing.ScreenHorizontal),
            content = content,
        )
        if (footer != null) {
            DdHairline()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = DdSpacing.ScreenHorizontal,
                        vertical = DdSpacing.Md,
                    ),
            ) {
                footer()
            }
        }
    }
}
