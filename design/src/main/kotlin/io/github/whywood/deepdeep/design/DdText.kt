package io.github.whywood.deepdeep.design

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun DdText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = DdType.body,
    color: Color = deepDeepColors().ink,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        softWrap = softWrap,
        onTextLayout = onTextLayout ?: {},
    )
}

@Composable
fun DdText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = DdType.body,
    color: Color = deepDeepColors().ink,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    softWrap: Boolean = true,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        softWrap = softWrap,
        onTextLayout = onTextLayout ?: {},
        inlineContent = inlineContent,
    )
}
