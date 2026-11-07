package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val PendingStatus = "pending"
private const val InProgressStatus = "in_progress"
private const val CompletedStatus = "completed"
private val PanelShape = RoundedCornerShape(18.dp)
private val HeaderHeight = 44.dp
private val DividerHeight = 1.dp

internal data class TodoItem(val content: String, val status: String)

@Composable
internal fun TodoPanel(payload: String?, modifier: Modifier = Modifier) {
    val todos = remember(payload) { parseTodos(payload) }
    var lastTodos by remember { mutableStateOf(emptyList<TodoItem>()) }
    LaunchedEffect(todos) {
        if (todos.isNotEmpty()) lastTodos = todos
    }
    AnimatedVisibility(
        visible = todos.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn(tween(DdMotion.FAST)) +
            expandVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
        exit = fadeOut(tween(DdMotion.FAST)) +
            shrinkVertically(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
    ) {
        TodoPanelContent(todos = todos.ifEmpty { lastTodos })
    }
}

@Composable
private fun TodoPanelContent(todos: List<TodoItem>) {
    val colors = deepDeepColors()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation = animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "todo-chevron",
    )
    val stateLabel = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val completedCount = todos.count { it.status == CompletedStatus }
    val allCompleted = completedCount == todos.size
    val current = todos.firstOrNull { it.status == InProgressStatus }
        ?: todos.firstOrNull { it.status != CompletedStatus }
    val collapsedSummary = current?.content ?: stringResource(R.string.assistant_todo_title)
    val summary = if (expanded) stringResource(R.string.assistant_todo_title) else collapsedSummary
    val scrollState = rememberScrollState()
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        val listMaxHeight = (maxHeight * 0.4f - HeaderHeight - DividerHeight).coerceAtLeast(0.dp)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(colors.surface)
                    .then(
                        if (colors.dark) {
                            Modifier.border(1.dp, colors.ink.copy(alpha = 0.08f), PanelShape)
                        } else {
                            Modifier
                        },
                    )
                    .animateContentSize(
                        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                    ),
            ) {
                DdPressable(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HeaderHeight)
                        .semantics { stateDescription = stateLabel }
                        .testTag("todo_panel_toggle"),
                    shape = PanelShape,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (allCompleted) {
                            DdIcon(icon = DdIcons.Check, tint = colors.success, size = 14.dp)
                        } else {
                            StatusDot(
                                status = current?.status.orEmpty(),
                                accent = colors.accent,
                                muted = colors.inkTertiary,
                                warning = colors.warning,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = summary,
                            modifier = Modifier.weight(1f),
                            style = DdType.bodySmall,
                            color = if (allCompleted) colors.inkTertiary else colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        DdIcon(icon = DdIcons.Check, tint = colors.inkTertiary, size = 12.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                R.string.assistant_todo_progress,
                                completedCount,
                                todos.size,
                            ),
                            style = DdType.chip,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.width(6.dp))
                        DdIcon(
                            icon = DdIcons.ChevronDown,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation.value },
                            tint = colors.inkTertiary.copy(alpha = 0.7f),
                            size = 14.dp,
                        )
                    }
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(DdMotion.FAST)),
                    exit = fadeOut(tween(DdMotion.FAST)),
                ) {
                    Column {
                        DdHairline(Modifier.padding(horizontal = 16.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = listMaxHeight)
                                .padding(vertical = 6.dp)
                                .verticalScroll(scrollState),
                        ) {
                            todos.forEach { todo ->
                                TodoPanelRow(todo)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoPanelRow(todo: TodoItem) {
    val colors = deepDeepColors()
    val completed = todo.status == CompletedStatus
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            status = todo.status,
            accent = colors.accent,
            muted = colors.inkTertiary,
            warning = colors.warning,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = todo.content,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = if (completed) colors.inkTertiary else colors.ink,
            textDecoration = if (completed) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusDot(status: String, accent: Color, muted: Color, warning: Color) {
    val color = when (status) {
        CompletedStatus -> muted
        InProgressStatus -> warning
        else -> accent
    }
    Box(
        Modifier
            .size(8.dp)
            .background(color.copy(alpha = if (status == PendingStatus) 0.35f else 1f), CircleShape),
    )
}

internal fun parseTodos(payload: String?): List<TodoItem> {
    if (payload.isNullOrBlank()) return emptyList()
    val root = runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull()
        ?: return emptyList()
    val array = root["todos"] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val content = (item["content"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val status = (item["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (content.isEmpty()) null else TodoItem(content, status)
    }
}
