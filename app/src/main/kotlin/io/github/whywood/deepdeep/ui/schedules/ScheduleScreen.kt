package io.github.whywood.deepdeep.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.*
import io.github.whywood.deepdeep.runtime.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

internal val SCHEDULE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val WEEKDAY_SET = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND_SET = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val NEXT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
fun ScheduleScreen(
    service: ScheduleService,
    runtime: AgentRuntime,
    initialConversationId: String?,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    val schedules by service.schedules.collectAsStateWithLifecycle()
    var titles by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var editorDraft by remember { mutableStateOf<ScheduleDraft?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { service.list() }
        conversations = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
        loaded = true
    }
    LaunchedEffect(schedules.map { it.sessionId }) {
        val missing = schedules.map { it.sessionId }.filter { it.isNotBlank() }.toSet() - titles.keys
        if (missing.isNotEmpty()) {
            val loaded = HashMap<String, String?>()
            for (id in missing) loaded[id] = runCatching { runtime.sessionTitle(id) }.getOrNull()
            titles = titles + loaded
        }
    }

    fun openNew() {
        val conversation = initialConversationId?.let { id -> conversations.firstOrNull { it.id == id } }
        errorRes = null
        editorDraft = ScheduleDraft(
            sessionId = conversation?.id.orEmpty(),
            sessionFile = conversation?.filePath.orEmpty(),
            cwd = conversation?.cwd.orEmpty(),
            conversationTitle = conversation?.title.orEmpty(),
        )
    }

    fun openExisting(schedule: Schedule) {
        errorRes = null
        editorDraft = ScheduleDraft.from(schedule, titles[schedule.sessionId].orEmpty())
    }

    fun saveDraft(draft: ScheduleDraft) {
        scope.launch {
            saving = true
            errorRes = null
            val trigger = draft.toTrigger()
            val result = runCatching {
                if (draft.id == null) {
                    service.create(
                        NewSchedule(
                            draft.name, draft.prompt, trigger, draft.enabled, draft.sessionId, draft.sessionFile, draft.cwd,
                        ),
                    )
                } else {
                    service.update(
                        draft.id,
                        SchedulePatch(
                            draft.name, draft.prompt, trigger, draft.enabled, draft.sessionId, draft.sessionFile, draft.cwd,
                        ),
                    )
                }
            }
            saving = false
            if (result.isSuccess) editorDraft = null else errorRes = R.string.schedule_error_save_failed
        }
    }

    fun runEditorNow() {
        val draft = editorDraft ?: return
        val id = draft.id ?: return
        scope.launch {
            saving = true
            errorRes = null
            val result = service.runNow(id)
            saving = false
            if (result.isSuccess) {
                editorDraft = null
                if (draft.sessionId.isNotBlank()) onOpenConversation(draft.sessionId)
            } else {
                errorRes = deliveryErrorRes((result.exceptionOrNull() as? ScheduleDeliveryException)?.error)
            }
        }
    }

    fun toggleEnabled(schedule: Schedule, enabled: Boolean) {
        scope.launch {
            if (runCatching { service.update(schedule.id, SchedulePatch(enabled = enabled)) }.isFailure) {
                errorRes = R.string.schedule_error_save_failed
            }
        }
    }

    fun deleteDraft(draft: ScheduleDraft) {
        val id = draft.id ?: return
        scope.launch {
            saving = true
            errorRes = null
            val result = runCatching { service.delete(id) }
            saving = false
            if (result.isSuccess) editorDraft = null else errorRes = R.string.schedule_error_delete_failed
        }
    }

    Column(
        modifier.fillMaxSize().background(colors.paper).statusBarsPadding().navigationBarsPadding()
            .testTag("schedules_screen"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(DdIcons.ArrowLeft, onBack, stringResource(R.string.schedules_back), tint = colors.ink, iconSize = 21.dp)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.schedules_title), Modifier.weight(1f), style = DdType.heading, color = colors.ink)
            DdIconButton(
                DdIcons.Plus, { openNew() }, stringResource(R.string.schedules_new),
                Modifier.testTag("schedules_new"), tint = colors.ink, iconSize = 21.dp,
            )
        }
        when {
            !loaded -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DdSpinner(size = 22.dp, strokeWidth = 2.dp)
            }

            schedules.isEmpty() -> EmptySchedules(Modifier.weight(1f))

            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                val active = schedules.filter { it.enabled }.sortedWith(compareBy({ it.nextRunAt ?: Long.MAX_VALUE }, { it.name }))
                val inactive = schedules.filterNot { it.enabled }.sortedByDescending { it.updatedAt }
                if (active.isNotEmpty()) {
                    ScheduleGroup(stringResource(R.string.schedules_group_active), active, titles, ::openExisting, ::toggleEnabled)
                }
                if (inactive.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    ScheduleGroup(stringResource(R.string.schedules_group_inactive), inactive, titles, ::openExisting, ::toggleEnabled)
                }
                errorRes?.let { message ->
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(message), style = DdType.bodySmall, color = colors.danger)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    editorDraft?.let { draft ->
        ScheduleEditor(
            initialDraft = draft,
            isEditing = draft.id != null,
            externalError = errorRes?.let { stringResource(it) },
            saving = saving,
            runtime = runtime,
            onDismiss = { editorDraft = null; errorRes = null },
            onSave = ::saveDraft,
            onRunNow = ::runEditorNow,
            onDelete = { deleteDraft(draft) },
            onOpenConversation = onOpenConversation,
        )
    }
}

@Composable
private fun ScheduleGroup(
    label: String,
    schedules: List<Schedule>,
    titles: Map<String, String?>,
    onOpen: (Schedule) -> Unit,
    onToggle: (Schedule, Boolean) -> Unit,
) {
    Text(label, Modifier.padding(top = 24.dp, bottom = 4.dp), style = DdType.micro, color = deepDeepColors().inkTertiary)
    schedules.forEachIndexed { index, schedule ->
        val conversationText = when {
            schedule.sessionId.isBlank() -> null
            titles.containsKey(schedule.sessionId) -> titles[schedule.sessionId] ?: stringResource(R.string.schedules_conversation_deleted)
            else -> null
        }
        ScheduleRow(schedule, conversationText, { onOpen(schedule) }, { onToggle(schedule, it) })
        if (index != schedules.lastIndex) DdHairline()
    }
}

@Composable
private fun ScheduleRow(schedule: Schedule, conversationText: String?, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = deepDeepColors()
    val next = nextDeliveryLabel(schedule.nextRunAt)
    val rule = ruleLabel(schedule.trigger)
    val subtitle = if (conversationText == null) {
        stringResource(R.string.schedules_subtitle, next, rule)
    } else {
        stringResource(R.string.schedules_row_subtitle, next, rule, conversationText)
    }
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("schedule_row_${schedule.id}"),
        shape = RoundedCornerShape(12.dp),
        contentDescription = schedule.name,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (schedule.enabled) 1f else 0.55f)) {
                Text(schedule.name, style = DdType.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = DdType.bodySmall, color = colors.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            DdSwitch(schedule.enabled, onToggle, Modifier.testTag("schedule_switch_${schedule.id}"))
        }
    }
}

@Composable
private fun EmptySchedules(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.schedules_empty_title), style = DdType.body, color = deepDeepColors().ink)
    }
}

@Composable
private fun ruleLabel(trigger: ScheduleTrigger): String = stringResource(
    when (trigger) {
        is ScheduleTrigger.Once -> R.string.schedules_frequency_once
        is ScheduleTrigger.Daily -> R.string.schedules_frequency_daily
        is ScheduleTrigger.Weekly -> when {
            trigger.days == WEEKDAY_SET -> R.string.schedules_frequency_weekdays
            trigger.days == WEEKEND_SET -> R.string.schedules_frequency_weekends
            else -> R.string.schedules_frequency_weekly
        }
    },
)

@Composable
private fun nextDeliveryLabel(timestamp: Long?): String {
    if (timestamp == null) return stringResource(R.string.schedules_never)
    val next = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (next.toLocalDate()) {
        today -> stringResource(R.string.schedules_today)
        today.plusDays(1) -> stringResource(R.string.schedules_tomorrow)
        else -> next.format(NEXT_DATE_FORMAT)
    }
    return stringResource(R.string.schedules_day_time, day, next.format(SCHEDULE_TIME_FORMAT))
}

internal fun deliveryErrorRes(error: ScheduleError?): Int = when (error) {
    ScheduleError.SESSION_MISSING -> R.string.schedule_error_session_missing
    ScheduleError.OPEN_FAILED -> R.string.schedule_error_open_failed
    ScheduleError.DELIVERY_FAILED -> R.string.schedule_error_delivery_failed
    null -> R.string.schedule_error_run_failed
}

internal fun draftErrorRes(error: DraftError): Int = when (error) {
    DraftError.EMPTY_NAME -> R.string.schedule_error_empty_name
    DraftError.EMPTY_PROMPT -> R.string.schedule_error_empty_prompt
    DraftError.EMPTY_WEEKDAYS -> R.string.schedule_error_empty_weekdays
    DraftError.PAST_TIME -> R.string.schedule_error_past_time
    DraftError.NO_CONVERSATION -> R.string.schedule_error_no_conversation
}
