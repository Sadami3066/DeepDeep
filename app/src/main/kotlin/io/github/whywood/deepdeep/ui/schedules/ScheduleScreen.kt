package io.github.whywood.deepdeep.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import io.github.whywood.deepdeep.runtime.RuntimeWorkspace
import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.SchedulePatch
import io.github.whywood.deepdeep.runtime.ScheduleRunStatus
import io.github.whywood.deepdeep.runtime.ScheduleStore
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
import io.github.whywood.deepdeep.ui.settings.SettingsGroup
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WEEKDAYS = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
)
private val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val NEXT_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val ONCE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ScheduleScreen(
    store: ScheduleStore,
    workspaces: List<RuntimeWorkspace>,
    loadArrangements: suspend () -> List<ConversationSummary>,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenArrangement: (ConversationSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    var schedules by remember { mutableStateOf<List<Schedule>>(emptyList()) }
    var arrangements by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val visibleWorkspaces = remember(workspaces) { workspaces.filterNot { it.hidden } }

    LaunchedEffect(Unit) {
        while (true) {
            schedules = runCatching { store.list() }.getOrElse { current ->
                error = current.message
                schedules
            }
            arrangements = runCatching { loadArrangements() }.getOrDefault(arrangements)
            loading = false
            delay(5_000L)
        }
    }

    fun updateEnabled(schedule: Schedule, enabled: Boolean) {
        scope.launch {
            val result = runCatching { store.update(schedule.id, SchedulePatch(enabled = enabled)) }
            if (result.isFailure) {
                error = result.exceptionOrNull()?.message
            } else {
                schedules = runCatching { store.list() }.getOrDefault(schedules)
            }
        }
    }

    fun delete(schedule: Schedule) {
        scope.launch {
            val result = runCatching { store.delete(schedule.id) }
            if (result.isFailure) {
                error = result.exceptionOrNull()?.message
            } else {
                deleteId = null
                selectedId = null
                schedules = runCatching { store.list() }.getOrDefault(schedules)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("schedules_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.schedules_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.schedules_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            DdIconButton(
                icon = DdIcons.Plus,
                onClick = onNew,
                contentDescription = stringResource(R.string.schedules_new),
                modifier = Modifier.testTag("schedules_new"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            when {
                loading -> Box(
                    Modifier.fillMaxWidth().padding(top = 100.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                }

                schedules.isEmpty() -> EmptySchedules(onNew = onNew)

                else -> {
                    Spacer(Modifier.height(8.dp))
                    SettingsGroup {
                        schedules.forEachIndexed { index, schedule ->
                            val workspace = visibleWorkspaces.firstOrNull { it.id == schedule.workspaceId }
                            DdListRow(
                                title = schedule.name,
                                subtitle = stringResource(
                                    R.string.schedules_subtitle,
                                    triggerLabel(schedule.trigger),
                                    workspace?.name ?: schedule.workspaceId,
                                ),
                                onClick = { selectedId = schedule.id },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = nextRunLabel(schedule.nextRunAt),
                                            style = DdType.bodySmall,
                                            color = colors.inkTertiary,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        DdSwitch(
                                            checked = schedule.enabled,
                                            onCheckedChange = { updateEnabled(schedule, it) },
                                            modifier = Modifier.testTag("schedule_switch_${schedule.id}"),
                                        )
                                    }
                                },
                            )
                            if (index != schedules.lastIndex) DdHairline()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.schedules_note),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }
            error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(text = message, style = DdType.bodySmall, color = colors.danger)
            }
            if (arrangements.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.schedules_arrangements),
                    style = DdType.micro,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    arrangements.forEachIndexed { index, summary ->
                        DdListRow(
                            title = summary.title,
                            subtitle = stringResource(R.string.schedules_arrangement_hint),
                            onClick = { onOpenArrangement(summary) },
                            showChevron = true,
                            modifier = Modifier.testTag("schedule_arrangement_${summary.id}"),
                        )
                        if (index != arrangements.lastIndex) DdHairline()
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    val selected = schedules.firstOrNull { it.id == selectedId }
    if (selected != null) {
        ScheduleDetailSheet(
            schedule = selected,
            workspaceName = visibleWorkspaces.firstOrNull { it.id == selected.workspaceId }?.name
                ?: selected.workspaceId,
            onOpenSession = onOpenSession,
            onDelete = { deleteId = selected.id },
            onDismiss = { selectedId = null },
        )
    }

    val deleteTarget = schedules.firstOrNull { it.id == deleteId }
    if (deleteTarget != null) {
        DdDialog(
            onDismissRequest = { deleteId = null },
            title = stringResource(R.string.schedules_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.schedules_delete_message, deleteTarget.name),
                    style = DdType.body,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DeleteActions(
                    onDismiss = { deleteId = null },
                    onConfirm = { delete(deleteTarget) },
                )
            },
        )
    }
}

@Composable
private fun EmptySchedules(onNew: () -> Unit) {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Clock, tint = colors.inkTertiary, size = 30.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.schedules_empty_title),
            style = DdType.body,
            color = colors.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.schedules_empty_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(20.dp))
        DdPrimaryButton(
            text = stringResource(R.string.schedules_new),
            onClick = onNew,
            modifier = Modifier.testTag("schedules_empty_new"),
        )
    }
}

@Composable
private fun ScheduleDetailSheet(
    schedule: Schedule,
    workspaceName: String,
    onOpenSession: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .testTag("schedule_detail"),
        ) {
            DdSheetHeader(title = schedule.name, onClose = onDismiss)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                DetailLine(stringResource(R.string.schedules_trigger), triggerLabel(schedule.trigger))
                DetailLine(stringResource(R.string.schedules_workspace), workspaceName)
                DetailLine(stringResource(R.string.schedules_next), nextRunLabel(schedule.nextRunAt))
                schedule.lastRunAt?.let { DetailLine(stringResource(R.string.schedules_last), lastRunLabel(it)) }
                schedule.lastStatus?.let { status ->
                    DetailLine(stringResource(R.string.schedules_status), statusLabel(status))
                }
                schedule.lastError?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 8.dp),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.schedules_prompt),
                    style = DdType.micro,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.wash)
                        .padding(14.dp),
                ) {
                    Text(
                        text = schedule.prompt,
                        style = DdType.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.inkSecondary,
                    )
                }
                schedule.lastSessionId?.let { sessionId ->
                    Spacer(Modifier.height(20.dp))
                    DdPrimaryButton(
                        text = stringResource(R.string.schedules_open_session),
                        onClick = {
                            onDismiss()
                            onOpenSession(sessionId)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("schedule_open_session"),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DdIcon(icon = DdIcons.Trash, tint = colors.danger, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    DdTextAction(
                        text = stringResource(R.string.schedules_delete),
                        onClick = onDelete,
                        color = colors.danger,
                        modifier = Modifier.weight(1f).testTag("schedule_delete"),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(92.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeleteActions(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    DdTextAction(
        text = stringResource(R.string.schedules_cancel),
        onClick = onDismiss,
        color = deepDeepColors().inkSecondary,
        modifier = Modifier.testTag("schedules_delete_cancel"),
    )
    DdTextAction(
        text = stringResource(R.string.schedules_delete_confirm),
        onClick = onConfirm,
        color = deepDeepColors().danger,
        modifier = Modifier.testTag("schedules_delete_confirm"),
    )
}

@Composable
private fun triggerLabel(trigger: ScheduleTrigger): String = when (trigger) {
    is ScheduleTrigger.Once -> stringResource(
        R.string.schedules_trigger_once,
        trigger.date.format(ONCE_DATE_FORMAT),
        trigger.time.format(TIME_FORMAT),
    )

    is ScheduleTrigger.Daily -> stringResource(
        R.string.schedules_trigger_daily,
        trigger.time.format(TIME_FORMAT),
    )

    is ScheduleTrigger.Weekly -> when (trigger.days) {
        WEEKDAYS -> stringResource(R.string.schedules_trigger_weekdays, trigger.time.format(TIME_FORMAT))
        WEEKENDS -> stringResource(R.string.schedules_trigger_weekends, trigger.time.format(TIME_FORMAT))
        else -> stringResource(
            R.string.schedules_trigger_weekly,
            trigger.days.sortedBy { it.value }
                .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
            trigger.time.format(TIME_FORMAT),
        )
    }
}

@Composable
private fun nextRunLabel(timestamp: Long?): String {
    if (timestamp == null) return stringResource(R.string.schedules_never)
    return NEXT_FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
}

@Composable
private fun lastRunLabel(timestamp: Long): String =
    NEXT_FORMAT.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

@Composable
private fun statusLabel(status: ScheduleRunStatus): String = stringResource(
    when (status) {
        ScheduleRunStatus.RUNNING -> R.string.schedules_status_running
        ScheduleRunStatus.DONE -> R.string.schedules_status_done
        ScheduleRunStatus.STOPPED -> R.string.schedules_status_stopped
        ScheduleRunStatus.FAILED -> R.string.schedules_status_failed
    },
)
