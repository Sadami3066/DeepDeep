package io.github.whywood.deepdeep.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

enum class DdContentStateKind { Loading, Empty, Error }

@Composable
fun DdContentState(
    kind: DdContentStateKind,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: DdIcon? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = deepDeepColors()
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = DdSpacing.Section,
                vertical = DdSpacing.Xxl,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (kind) {
                DdContentStateKind.Loading -> DdSpinner(
                    color = colors.inkTertiary,
                    size = DdSpacing.Xxl,
                )
                DdContentStateKind.Empty -> DdIcon(
                    icon = icon ?: DdIcons.FolderOpen,
                    tint = colors.inkTertiary,
                    size = DdSpacing.Xxxl,
                )
                DdContentStateKind.Error -> DdIcon(
                    icon = icon ?: DdIcons.Info,
                    tint = colors.danger,
                    size = DdSpacing.Xxxl,
                )
            }
            Spacer(Modifier.height(DdSpacing.Lg))
            DdText(
                text = title,
                style = DdType.body,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Spacer(Modifier.height(DdSpacing.Xs))
                DdText(
                    text = description,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(DdSpacing.Xl))
                action()
            }
        }
    }
}
