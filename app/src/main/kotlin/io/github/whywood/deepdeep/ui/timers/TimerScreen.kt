package io.github.whywood.deepdeep.ui.timers

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
import io.github.whywood.deepdeep.runtime.Timer
import io.github.whywood.deepdeep.runtime.TimerError
import io.github.whywood.deepdeep.runtime.TimerService
import io.github.whywood.deepdeep.runtime.TimerTrigger
import io.github.whywood.deepdeep.runtime.summarizeCron
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

internal val TIMER_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val WEEKDAY_SET = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND_SET = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
private val NEXT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
fun TimerScreen(
    service: TimerService,
    runtime: ConversationQueries,
    initialConversationId: String?,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val holder = remember(service, runtime, initialConversationId) {
        TimersStateHolder(
            source = TimerServiceDataSource(service, runtime),
            scope = scope,
            initialConversationId = initialConversationId,
        )
    }
    LaunchedEffect(holder) { holder.load() }
    val state by holder.state.collectAsStateWithLifecycle()

    TimersContent(
        state = state,
        onBack = onBack,
        onQueryChange = holder::setQuery,
        onNew = holder::openNew,
        onRetry = { scope.launch { holder.retry() } },
        onOpen = { timer -> scope.launch { holder.openExisting(timer) } },
        onToggle = { timer, enabled -> scope.launch { holder.setEnabled(timer.id, enabled) } },
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
internal fun TimersContent(
    state: TimersUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNew: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Timer) -> Unit,
    onToggle: (Timer, Boolean) -> Unit,
    onDismissEditor: () -> Unit,
    onSave: (TimerDraft) -> Unit,
    onRunNow: (TimerDraft) -> Unit,
    onDelete: (TimerDraft) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier.fillMaxSize().background(colors.paper).statusBarsPadding().navigationBarsPadding()
            .testTag("timers_screen"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(DdIcons.ArrowLeft, onBack, stringResource(R.string.timers_back), tint = colors.ink, iconSize = 21.dp)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.timers_title), Modifier.weight(1f), style = DdType.heading, color = colors.ink)
            DdIconButton(
                DdIcons.Plus, { onNew() }, stringResource(R.string.timers_new),
                Modifier.testTag("timers_new"), tint = colors.ink, iconSize = 21.dp,
            )
        }
        DdSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.timers_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "timers_search_field",
            clearTestTag = "timers_search_clear",
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

            state.items.isEmpty() -> EmptyTimers(Modifier.weight(1f))

            state.filtered.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.search_no_match), style = DdType.bodySmall, color = colors.inkTertiary)
            }

            else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                if (state.active.isNotEmpty()) {
                    TimerGroup(
                        stringResource(R.string.timers_group_active),
                        state.active,
                        state.titles,
                        onOpen,
                        onToggle,
                    )
                }
                if (state.inactive.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    TimerGroup(
                        stringResource(R.string.timers_group_inactive),
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
        TimerEditor(
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
private fun TimerGroup(
    label: String,
    timers: List<Timer>,
    titles: Map<String, String?>,
    onOpen: (Timer) -> Unit,
    onToggle: (Timer, Boolean) -> Unit,
) {
    Text(label, Modifier.padding(top = 24.dp, bottom = 4.dp), style = DdType.micro, color = deepDeepColors().inkTertiary)
    timers.forEachIndexed { index, timer ->
        val conversationText = when {
            timer.sessionId.isBlank() -> null
            titles.containsKey(timer.sessionId) -> titles[timer.sessionId] ?: stringResource(R.string.timers_conversation_deleted)
            else -> null
        }
        TimerRow(timer, conversationText, { onOpen(timer) }, { onToggle(timer, it) })
        if (index != timers.lastIndex) DdHairline()
    }
}

@Composable
private fun TimerRow(timer: Timer, conversationText: String?, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = deepDeepColors()
    val next = nextDeliveryLabel(timer.nextRunAt)
    val rule = ruleLabel(timer.trigger)
    val subtitle = if (conversationText == null) {
        stringResource(R.string.timers_subtitle, next, rule)
    } else {
        stringResource(R.string.timers_row_subtitle, next, rule, conversationText)
    }
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("timer_row_${timer.id}"),
        shape = RoundedCornerShape(12.dp),
        contentDescription = timer.name,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (timer.enabled) 1f else 0.55f)) {
                Text(timer.name, style = DdType.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = DdType.bodySmall, color = colors.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            DdSwitch(timer.enabled, onToggle, Modifier.testTag("timer_switch_${timer.id}"))
        }
    }
}

@Composable
private fun EmptyTimers(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.timers_empty_title), style = DdType.body, color = deepDeepColors().ink)
    }
}

@Composable
private fun ruleLabel(trigger: TimerTrigger): String = when (trigger) {
    is TimerTrigger.Once -> stringResource(R.string.timers_frequency_once)
    is TimerTrigger.Daily -> stringResource(R.string.timers_frequency_daily)
    is TimerTrigger.Weekly -> stringResource(
        when {
            trigger.days == WEEKDAY_SET -> R.string.timers_frequency_weekdays
            trigger.days == WEEKEND_SET -> R.string.timers_frequency_weekends
            else -> R.string.timers_frequency_weekly
        },
    )

    is TimerTrigger.Cron -> cronSummaryLabel(trigger.expression) ?: trigger.expression
}

@Composable
internal fun cronSummaryLabel(expression: String): String? {
    val locale = LocalConfiguration.current.locales[0]
    val separator = stringResource(R.string.timers_cron_weekday_separator)
    return when (val summary = summarizeCron(expression)) {
        null -> null
        CronSummary.EveryMinute -> stringResource(R.string.timers_cron_every_minute)
        is CronSummary.EveryMinutes -> stringResource(R.string.timers_cron_every_minutes, summary.every)
        is CronSummary.EveryHours -> if (summary.every == 1) {
            if (summary.minute == 0) {
                stringResource(R.string.timers_cron_hourly)
            } else {
                stringResource(R.string.timers_cron_hourly_at, summary.minute)
            }
        } else {
            stringResource(R.string.timers_cron_every_hours, summary.every, summary.minute)
        }

        is CronSummary.Daily -> stringResource(R.string.timers_cron_daily, summary.time.format(TIMER_TIME_FORMAT))
        is CronSummary.Weekly -> stringResource(
            R.string.timers_cron_weekly,
            weekdayLabel(summary.days, locale, separator),
            summary.time.format(TIMER_TIME_FORMAT),
        )

        is CronSummary.Monthly -> stringResource(
            R.string.timers_cron_monthly,
            summary.day,
            summary.time.format(TIMER_TIME_FORMAT),
        )

        is CronSummary.Yearly -> stringResource(
            R.string.timers_cron_yearly,
            summary.month,
            summary.day,
            summary.time.format(TIMER_TIME_FORMAT),
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
    if (timestamp == null) return stringResource(R.string.timers_never)
    val next = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (next.toLocalDate()) {
        today -> stringResource(R.string.timers_today)
        today.plusDays(1) -> stringResource(R.string.timers_tomorrow)
        else -> next.format(NEXT_DATE_FORMAT)
    }
    return stringResource(R.string.timers_day_time, day, next.format(TIMER_TIME_FORMAT))
}

internal fun deliveryErrorRes(error: TimerError?): Int = when (error) {
    TimerError.SESSION_MISSING -> R.string.timer_error_session_missing
    TimerError.OPEN_FAILED -> R.string.timer_error_open_failed
    TimerError.DELIVERY_FAILED -> R.string.timer_error_delivery_failed
    null -> R.string.timer_error_run_failed
}

internal fun draftErrorRes(error: DraftError): Int = when (error) {
    DraftError.EMPTY_NAME -> R.string.timer_error_empty_name
    DraftError.EMPTY_PROMPT -> R.string.timer_error_empty_prompt
    DraftError.PAST_TIME -> R.string.timer_error_past_time
    DraftError.INVALID_CRON -> R.string.timer_error_invalid_cron
    DraftError.NO_CONVERSATION -> R.string.timer_error_no_conversation
}

internal fun filterTimers(
    timers: List<Timer>,
    titles: Map<String, String?>,
    query: String,
): List<Timer> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return timers
    return timers.filter { timer ->
        timer.name.contains(normalized, ignoreCase = true) ||
            timer.prompt.contains(normalized, ignoreCase = true) ||
            titles[timer.sessionId]?.contains(normalized, ignoreCase = true) == true
    }
}
