package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private data class TodoRow(val content: String, val status: String)

@Composable
fun TodoCard(payload: String?) {
    val todos = remember(payload) { parseTodos(payload) }
    if (todos.isEmpty()) return
    val colors = deepDeepColors()
    DdGroupCard {
        Text(
            text = stringResource(R.string.assistant_todo_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = DdType.label,
            color = colors.inkSecondary,
        )
        todos.forEach { todo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(todo.status, colors.accent, colors.inkTertiary, colors.warning)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = todo.content,
                    modifier = Modifier.weight(1f),
                    style = DdType.body,
                    color = if (todo.status == "completed") colors.inkTertiary else colors.ink,
                    textDecoration = if (todo.status == "completed") TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

@Composable
private fun StatusDot(status: String, accent: Color, muted: Color, warning: Color) {
    val color = when (status) {
        "completed" -> muted
        "in_progress" -> warning
        else -> accent
    }
    Box(
        Modifier.size(8.dp).background(color.copy(alpha = if (status == "pending") 0.35f else 1f), CircleShape),
    )
}

private fun parseTodos(payload: String?): List<TodoRow> {
    if (payload.isNullOrBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return emptyList()
    val array = root["todos"] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val content = (item["content"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val status = (item["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (content.isEmpty()) null else TodoRow(content, status)
    }
}
