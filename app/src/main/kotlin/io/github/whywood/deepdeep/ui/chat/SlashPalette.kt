package io.github.whywood.deepdeep.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

private const val SLASH_ENTER_MILLIS = 140
private const val SLASH_MAX_HEIGHT_DP = 300
private const val SLASH_ROW_HEIGHT_DP = 52
private const val SLASH_NAME_WIDTH_DP = 150
private const val SLASH_DISABLED_ALPHA = 0.4f

data class CommandItem(
    val id: String,
    val name: String,
    val title: String,
    val description: String,
    val enabled: Boolean,
    val insertText: String,
)

@Composable
fun SlashPalette(
    items: List<CommandItem>,
    query: String,
    onPick: (CommandItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val filtered = remember(items, query) {
        val normalized = query.trim().removePrefix("/")
        if (normalized.isEmpty()) {
            items
        } else {
            items.filter {
                it.name.startsWith(normalized, ignoreCase = true) ||
                    it.title.contains(normalized, ignoreCase = true) ||
                    it.description.contains(normalized, ignoreCase = true)
            }
        }
    }
    if (filtered.isEmpty()) return
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = DdMotion.spec<Float>(duration = SLASH_ENTER_MILLIS),
        label = "slash-palette-enter",
    )
    val paletteDescription = stringResource(R.string.cmd_palette_description)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .widthIn(max = 780.dp)
            .fillMaxWidth()
            .alpha(progress)
            .clip(shape)
            .background(colors.paper)
            .border(1.dp, colors.hairline, shape)
            .semantics { contentDescription = paletteDescription }
            .testTag("slash_palette"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.cmd_palette_title),
                style = DdType.micro,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = filtered.size.toString(),
                style = DdType.numeric,
                color = colors.inkTertiary,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = SLASH_MAX_HEIGHT_DP.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            filtered.forEachIndexed { index, item ->
                DdPressable(
                    onClick = { onPick(item) },
                    modifier = Modifier.fillMaxWidth().testTag("slash_item_${item.id}"),
                    shape = RectangleShape,
                    enabled = item.enabled,
                    contentDescription = item.title,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(SLASH_ROW_HEIGHT_DP.dp)
                            .alpha(if (item.enabled) 1f else SLASH_DISABLED_ALPHA)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "/${item.name}",
                            modifier = Modifier.width(SLASH_NAME_WIDTH_DP.dp),
                            style = DdType.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            ),
                            color = colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = DdType.bodySmall,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = item.description,
                                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                color = colors.inkTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (index != filtered.lastIndex) {
                    DdHairline(Modifier.padding(start = 16.dp, end = 16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
