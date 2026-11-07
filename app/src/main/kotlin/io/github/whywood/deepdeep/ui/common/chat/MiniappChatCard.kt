package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.MiniappManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

data class MiniappCardApp(
    val id: String,
    val name: String,
    val icon: String,
    val version: String,
    val entryPath: String? = null,
)

private const val ICON_FALLBACK = "✦"
private const val DELIVERY_PRESENT = "present"

fun parseRegisteredMiniapp(trace: ToolTrace): MiniappCardApp? {
    if (trace.name != "miniapp_load" && trace.name != "miniapp_register") return null
    val raw = trace.output?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true) return null
        if (root.stringOrNull("delivery")?.trim() == DELIVERY_PRESENT) return null
        val app = root["app"] as? JsonObject ?: return null
        val id = app.stringOrNull("id")?.trim().orEmpty()
        val name = app.stringOrNull("name")?.trim().orEmpty()
        if (id.isEmpty() || name.isEmpty()) return null
        val directory = root.stringOrNull("path")?.trim().orEmpty()
        MiniappCardApp(
            id = id,
            name = name,
            icon = app.stringOrNull("icon")?.trim().orEmpty().ifBlank { ICON_FALLBACK },
            version = app.stringOrNull("version")?.trim().orEmpty().ifBlank { MiniappManifest.DEFAULT_VERSION },
            entryPath = directory.takeIf { it.isNotEmpty() }
                ?.let { File(it, "index.html") }
                ?.takeIf { it.isFile }
                ?.absolutePath,
        )
    } catch (_: Exception) {
        null
    }
}

@Composable
fun MiniappChatCard(
    app: MiniappCardApp,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(12.dp)
    DdPressable(
        onClick = { onOpen(app.id) },
        modifier = modifier.fillMaxWidth().testTag("miniapp_chat_card"),
        shape = shape,
        background = colors.surface,
        contentDescription = app.name,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape),
        ) {
            val preview = app.entryPath
            if (preview != null) {
                HtmlFileView(
                    path = preview,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 10.dp, start = 10.dp, end = 10.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .testTag("miniapp_chat_preview"),
                    onActivate = { onOpen(app.id) },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Text(text = app.icon.ifBlank { ICON_FALLBACK }, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle ?: stringResource(R.string.miniapp_chat_card_created),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 16.dp)
            }
        }
    }
}

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull
