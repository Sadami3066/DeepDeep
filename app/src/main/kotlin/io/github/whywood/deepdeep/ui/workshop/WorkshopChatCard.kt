package io.github.whywood.deepdeep.ui.workshop

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
import io.github.whywood.deepdeep.runtime.WorkshopManifest
import io.github.whywood.deepdeep.runtime.WorkshopSpec
import io.github.whywood.deepdeep.runtime.WorkshopType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class WorkshopCardApp(
    val id: String,
    val name: String,
    val type: WorkshopType,
    val icon: String,
    val version: String,
)

fun parseRegisteredWorkshop(trace: ToolTrace): WorkshopCardApp? {
    if (trace.name != "workshop_register") return null
    val raw = trace.output?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true) return null
        val app = root["app"] as? JsonObject ?: return null
        val id = app.stringOrNull("id")?.trim().orEmpty()
        val name = app.stringOrNull("name")?.trim().orEmpty()
        val wire = app.stringOrNull("type")?.trim().orEmpty()
        val type = WorkshopType.entries.firstOrNull { it.wire == wire } ?: return null
        if (id.isEmpty() || name.isEmpty()) return null
        WorkshopCardApp(
            id = id,
            name = name,
            type = type,
            icon = app.stringOrNull("icon")?.trim().orEmpty().ifBlank { WorkshopSpec.defaultIcon(type) },
            version = app.stringOrNull("version")?.trim().orEmpty().ifBlank { WorkshopManifest.DEFAULT_VERSION },
        )
    } catch (_: Exception) {
        null
    }
}

@Composable
fun WorkshopChatCard(
    app: WorkshopCardApp,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(12.dp)
    DdPressable(
        onClick = { onOpen(app.id) },
        modifier = modifier.fillMaxWidth().testTag("workshop_chat_card"),
        shape = shape,
        background = colors.surface,
        contentDescription = app.name,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.hairline.copy(alpha = 0.7f), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = app.icon.ifBlank { WorkshopSpec.defaultIcon(app.type) }, fontSize = 22.sp)
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
                    text = stringResource(
                        R.string.workshop_chat_card_subtitle,
                        stringResource(workshopTypeLabelRes(app.type)),
                    ),
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

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull
