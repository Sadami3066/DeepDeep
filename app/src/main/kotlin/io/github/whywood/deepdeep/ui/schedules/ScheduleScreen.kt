package io.github.whywood.deepdeep.ui.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.CronSummary
import io.github.whywood.deepdeep.runtime.Schedule
import io.github.whywood.deepdeep.runtime.ScheduleError
import io.github.whywood.deepdeep.runtime.ScheduleService
import io.github.whywood.deepdeep.runtime.ScheduleTrigger
import io.github.whywood.deepdeep.runtime.summarizeCron
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

internal val SCHEDULE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val WEEKDAY_SET = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND_SET = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val NEXT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
fun ScheduleScreen(
    service: ScheduleService,
    runtime: ConversationQueries,
    initialConversationId: String?,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val holder = remember(service, runtime, initialConversationId) {
        SchedulesStateHolder(
            source = ScheduleServiceDataSource(service, runtime),
            scope = scope,
            initialConversationId = initialConversationId,
        )
    }
    LaunchedEffect(holder) { holder.load() }
    val state by holder.state.collectAsStateWithLifecycle()

    SchedulesContent(
        state = state,
        onBack = onBack,
        onQueryChange = holder::setQuery,
        onNew = holder::openNew,
        onRetry = { scope.launch { holder.retry() } },
        onOpen = { schedule -> scope.launch { holder.openExisting(schedule) } },
        onToggle = { schedule, enabled -> scope.launch { holder.setEnabled(schedule.id, enabled) } },
        onDismissEditor = holder::dismissEditor,
        onSave = { draft -> scope.launch { holder.save(draft) } },
        onRunNow = { draft ->
            scope.launch {
                if (holder.runNow(draft) && draft.sessionId.isNotBlank()) onOpenConversation(draft.sessionId)
            }
        },
        onDelete = { draft ->
            val id = draft.id
            if (id != null) scope.launch { holder.remove(id) }
        },
        onOpenConversation = onOpenConversation,
        modifier = modifier,
    )
}

@Composable
internal fun SchedulesContent(
    state: SchedulesUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNew: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Schedule) -> Unit,
    onToggle: (Schedule, Boolean) -> Unit,
    onDismissEditor: () -> Unit,
    onSave: (ScheduleDraft) -> Unit,
    onRunNow: (ScheduleDraft) -> Unit,
    onDelete: (ScheduleDraft) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
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
                DdIcons.Plus, { onNew() }, stringResource(R.string.schedules_new),
                Modifier.testTag("schedules_new"), tint = colors.ink, iconSize = 21.dp,
            )
        }
        DdSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.schedules_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "schedules_search_field",
            clearTestTag = "schedules_search_clear",
        )
        state.errorRes?.let { message ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(message),
                    Modifier.weight(1f),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
                DdTextAction(stringResource(R.string.assistant_retry), onRetry, enabled = !state.busy)
            }
        }
        when {
            state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DdSpinner(size = 22.dp, strokeWidth = 2.dp)
            }

            state.items.isEmpty() -> EmptySchedules(Modifier.weight(1f))

            state.filtered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_no_match), style = DdType.bodySmall, color = colors.inkTertiary)
            }

            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                if (state.active.isNotEmpty()) {
                    ScheduleGroup(
                        stringResource(R.string.schedules_group_active),
                        state.active,
                        state.titles,
                        onOpen,
                        onToggle,
                    )
                }
                if (state.inactive.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    ScheduleGroup(
                        stringResource(R.string.schedules_group_inactive),
                        state.inactive,
                        state.titles,
                        onOpen,
                        onToggle,
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (state.editorOpen) {
        ScheduleEditor(
            initialDraft = state.editorDraft,
            isEditing = state.editingId != null,
            externalError = state.errorRes?.let { stringResource(it) },
            saving = state.busy,
            conversations = state.conversations,
            workspaceNames = state.workspaceNames,
            onDismiss = onDismissEditor,
            onSave = onSave,
            onRunNow = onRunNow,
            onDelete = onDelete,
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
private fun ruleLabel(trigger: ScheduleTrigger): String = when (trigger) {
    is ScheduleTrigger.Once -> stringResource(R.string.schedules_frequency_once)
    is ScheduleTrigger.Daily -> stringResource(R.string.schedules_frequency_daily)
    is ScheduleTrigger.Weekly -> stringResource(
        when {
            trigger.days == WEEKDAY_SET -> R.string.schedules_frequency_weekdays
            trigger.days == WEEKEND_SET -> R.string.schedules_frequency_weekends
            else -> R.string.schedules_frequency_weekly
        },
    )

    is ScheduleTrigger.Cron -> cronSummaryLabel(trigger.expression) ?: trigger.expression
}

@Composable
internal fun cronSummaryLabel(expression: String): String? {
    val locale = LocalConfiguration.current.locales[0]
    val separator = stringResource(R.string.schedules_cron_weekday_separator)
    return when (val summary = summarizeCron(expression)) {
        null -> null
        CronSummary.EveryMinute -> stringResource(R.string.schedules_cron_every_minute)
        is CronSummary.EveryMinutes -> stringResource(R.string.schedules_cron_every_minutes, summary.every)
        is CronSummary.EveryHours -> if (summary.every == 1) {
            if (summary.minute == 0) {
                stringResource(R.string.schedules_cron_hourly)
            } else {
                stringResource(R.string.schedules_cron_hourly_at, summary.minute)
            }
        } else {
            stringResource(R.string.schedules_cron_every_hours, summary.every, summary.minute)
        }

        is CronSummary.Daily -> stringResource(R.string.schedules_cron_daily, summary.time.format(SCHEDULE_TIME_FORMAT))
        is CronSummary.Weekly -> stringResource(
            R.string.schedules_cron_weekly,
            weekdayLabel(summary.days, locale, separator),
            summary.time.format(SCHEDULE_TIME_FORMAT),
        )

        is CronSummary.Monthly -> stringResource(
            R.string.schedules_cron_monthly,
            summary.day,
            summary.time.format(SCHEDULE_TIME_FORMAT),
        )

        is CronSummary.Yearly -> stringResource(
            R.string.schedules_cron_yearly,
            summary.month,
            summary.day,
            summary.time.format(SCHEDULE_TIME_FORMAT),
        )

        is CronSummary.Custom -> summary.expression
    }
}

private fun weekdayLabel(days: Set<DayOfWeek>, locale: Locale, separator: String): String =
    DayOfWeek.entries
        .filter { it in days }
        .joinToString(separator) { it.getDisplayName(TextStyle.SHORT, locale) }

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
    DraftError.PAST_TIME -> R.string.schedule_error_past_time
    DraftError.INVALID_CRON -> R.string.schedule_error_invalid_cron
    DraftError.NO_CONVERSATION -> R.string.schedule_error_no_conversation
}

internal fun filterSchedules(
    schedules: List<Schedule>,
    titles: Map<String, String?>,
    query: String,
): List<Schedule> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return schedules
    return schedules.filter { schedule ->
        schedule.name.contains(normalized, ignoreCase = true) ||
            schedule.prompt.contains(normalized, ignoreCase = true) ||
            titles[schedule.sessionId]?.contains(normalized, ignoreCase = true) == true
    }
}
