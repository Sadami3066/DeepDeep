package io.github.whywood.deepdeep.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

private val PreviewMaxHeight = 126.dp

@Composable
internal fun VoiceTranscriptPreview(
    buffer: String,
    partial: String,
    modifier: Modifier = Modifier,
) {
    val committed = buffer.trim()
    val pending = partial.trim()
    if (committed.isEmpty() && pending.isEmpty()) return
    val colors = deepDeepColors()
    val shape = DdShapes.Card
    val scroll = rememberScrollState()
    val text = remember(committed, pending, colors.ink, colors.inkTertiary) {
        buildAnnotatedString {
            append(committed)
            if (committed.isNotEmpty() && pending.isNotEmpty()) append(' ')
            if (pending.isNotEmpty()) {
                withStyle(SpanStyle(color = colors.inkTertiary)) { append(pending) }
            }
        }
    }
    LaunchedEffect(committed, pending) {
        snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF0B1220),
                spotColor = Color(0x2E0B1220),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.composer.copy(alpha = 0.9f),
                        colors.composer.copy(alpha = 0.68f),
                    ),
                ),
            )
            .border(1.dp, colors.hairline.copy(alpha = DdAlpha.Border), shape)
            .padding(horizontal = DdSpacing.Md, vertical = 9.dp)
            .testTag("composer_voice_preview"),
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PreviewMaxHeight)
                .verticalScroll(scroll),
            style = DdType.bodySmall,
            color = colors.ink,
        )
    }
}
