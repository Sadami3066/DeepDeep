package io.github.whywood.deepdeep.ui.common.chat

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdMarkdown
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun ChatMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    onFileClick: ((String) -> Unit)? = null,
    live: Boolean = false,
) {
    val context = LocalContext.current
    val colors = deepDeepColors()
    val copied = stringResource(R.string.assistant_copied)
    DdMarkdown(
        text = text,
        modifier = modifier,
        live = live,
        onFileClick = onFileClick,
        codeLabel = "CODE",
        copyCodeLabel = stringResource(R.string.assistant_tool_copy),
        onCopyCode = { code ->
            if (copyToClipboard(context, code)) {
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }
        },
        onImage = { image -> MarkdownImage(image.alt, image.source, image.title, colors) },
    )
}
