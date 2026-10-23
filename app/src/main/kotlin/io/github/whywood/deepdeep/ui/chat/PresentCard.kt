package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ToolTrace
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class PresentedFile(
    val path: String,
    val name: String,
)

fun parsePresentedFiles(trace: ToolTrace): List<PresentedFile>? {
    if (trace.name != "present") return null
    val raw = trace.output?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val array = Json.parseToJsonElement(raw).jsonObject["files"] as? JsonArray ?: return null
        val files = array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val path = item.stringOrNull("path")?.trim().orEmpty()
            if (path.isEmpty()) return@mapNotNull null
            PresentedFile(
                path = path,
                name = path.trimEnd('/').substringAfterLast('/').ifBlank { path },
            )
        }
        files.takeIf { it.size == array.size && it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PresentCard(
    files: List<PresentedFile>,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("present_card"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        files.forEachIndexed { index, file ->
            PresentFileCard(
                file = file,
                index = index,
                onOpen = { onOpen(file.path) },
                onShare = { onShare(file.path) },
            )
        }
    }
}

@Composable
private fun PresentFileCard(
    file: PresentedFile,
    index: Int,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(12.dp)
    DdPressable(
        onClick = onOpen,
        modifier = Modifier
            .size(140.dp)
            .testTag("present_open_$index"),
        shape = shape,
        background = colors.surface,
        contentDescription = file.name,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 14.dp, top = 26.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                DdIcon(
                    icon = file.icon(),
                    tint = colors.inkTertiary,
                    size = 22.dp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = file.name,
                    style = DdType.bodySmall,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DdIconButton(
                icon = DdIcons.Share,
                onClick = onShare,
                contentDescription = stringResource(R.string.present_share),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("present_share_$index"),
                size = 30.dp,
                iconSize = 14.dp,
                tint = colors.inkTertiary,
            )
        }
    }
}

private fun PresentedFile.icon(): DdIcon = when (name.substringAfterLast('.', "").lowercase()) {
    "png", "jpg", "jpeg", "webp", "gif", "bmp", "heic" -> DdIcons.Image
    else -> DdIcons.File
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull
