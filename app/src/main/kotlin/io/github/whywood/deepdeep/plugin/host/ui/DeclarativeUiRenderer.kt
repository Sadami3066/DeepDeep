package io.github.whywood.deepdeep.plugin.host.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.plugin.api.UiNode
import io.github.whywood.deepdeep.plugin.api.UiNodeTypes
import java.io.File

@Composable
internal fun DeclarativeUiRenderer(
    node: UiNode,
    assetsDir: File,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit = {},
) {
    RenderNode(node = node, assetsDir = assetsDir, modifier = modifier, onAction = onAction)
}

@Composable
private fun RenderNode(
    node: UiNode,
    assetsDir: File,
    modifier: Modifier,
    onAction: (String) -> Unit,
) {
    when (node.type) {
        UiNodeTypes.COLUMN -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            node.children.forEach { child ->
                RenderNode(child, assetsDir, Modifier.fillMaxWidth(), onAction)
            }
        }

        UiNodeTypes.ROW -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            node.children.forEach { child ->
                RenderNode(child, assetsDir, Modifier, onAction)
            }
        }

        UiNodeTypes.TEXT -> Text(
            text = node.text.orEmpty(),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )

        UiNodeTypes.MARKDOWN -> Text(
            text = node.text.orEmpty(),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )

        UiNodeTypes.IMAGE -> {
            val source = node.props["source"].orEmpty()
            val bitmap = remember(source) {
                if (source.startsWith("asset:")) {
                    val path = source.removePrefix("asset:")
                    runCatching { BitmapFactory.decodeFile(File(assetsDir, path).absolutePath) }.getOrNull()
                } else {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = node.props["content_description"],
                    modifier = modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            } else if (source.isNotBlank()) {
                Text(
                    text = source,
                    modifier = modifier,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        UiNodeTypes.BUTTON -> Button(
            onClick = { node.props["action"]?.takeIf { it.isNotBlank() }?.let(onAction) },
            modifier = modifier,
            enabled = node.props["enabled"]?.toBooleanStrictOrNull() ?: true,
        ) {
            Text(node.text.orEmpty())
        }

        UiNodeTypes.LINK -> {
            val uriHandler = LocalUriHandler.current
            TextButton(
                onClick = { node.props["url"]?.takeIf { it.isNotBlank() }?.let(uriHandler::openUri) },
                modifier = modifier,
            ) {
                Text(node.text.orEmpty())
            }
        }

        UiNodeTypes.PROGRESS -> LinearProgressIndicator(
            progress = { ((node.props["value"]?.toIntOrNull() ?: 0) / 100f).coerceIn(0f, 1f) },
            modifier = modifier.fillMaxWidth(),
        )

        UiNodeTypes.DIVIDER -> HorizontalDivider(modifier = modifier.fillMaxWidth())

        UiNodeTypes.SPACER -> Spacer(
            Modifier.height((node.props["height"]?.toIntOrNull() ?: 8).dp),
        )

        UiNodeTypes.QR_CODE -> Text(
            text = node.props["data"].orEmpty(),
            modifier = modifier.padding(4.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )

        else -> Text(
            text = node.text.orEmpty(),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
