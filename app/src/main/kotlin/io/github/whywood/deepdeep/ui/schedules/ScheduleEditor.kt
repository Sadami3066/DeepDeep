package io.github.whywood.deepdeep.ui.schedules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.*
import io.github.whywood.deepdeep.runtime.*
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ScheduleEditor(
    initialDraft: ScheduleDraft,
    isEditing: Boolean,
    externalError: String?,
    saving: Boolean,
    runtime: ConversationQueries,
    onDismiss: () -> Unit,
    onSave: (ScheduleDraft) -> Unit,
    onRunNow: () -> Unit,
    onDelete: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val instantFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }
    var draft by rememberSaveable(initialDraft, stateSaver = ScheduleDraftSaver) { mutableStateOf(initialDraft) }
    var validationError by remember { mutableStateOf<Int?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showConversationPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(draft.sessionId) {
        val sessionId = draft.sessionId
        if (sessionId.isNotBlank() && draft.conversationTitle.isBlank()) {
            val title = runCatching { runtime.sessionTitle(sessionId) }.getOrNull()
            if (!title.isNullOrBlank()) draft = draft.copy(conversationTitle = title)
        }
    }

    fun submit() {
        val prepared = if (draft.name.isBlank() && draft.prompt.isNotBlank()) draft.copy(name = ScheduleDraft.preview(draft.prompt)) else draft
        draft = prepared
        val error = prepared.validate(ZonedDateTime.now())
        if (error != null) {
            validationError = draftErrorRes(error)
            return
        }
        validationError = null
        onSave(prepared.copy(name = prepared.name.trim(), prompt = prepared.prompt.trim()))
    }

    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).testTag("schedule_editor")) {
            DdSheetHeader(stringResource(if (isEditing) R.string.schedules_edit_title else R.string.schedules_new_title), onDismiss)
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                FieldLabel(stringResource(R.string.schedules_name_label))
                DdTextField(draft.name, { draft = draft.copy(name = it); validationError = null }, stringResource(R.string.schedules_name_hint))
                FieldLabel(stringResource(R.string.schedules_prompt_label))
                DdTextField(
                    draft.prompt,
                    { draft = draft.copy(prompt = it); validationError = null },
                    stringResource(R.string.schedules_prompt_hint),
                    singleLine = false,
                )
                RepeatRow(draft.repeat) { draft = draft.copy(repeat = it); validationError = null }
                if (draft.repeat) {
                    Spacer(Modifier.height(12.dp))
                    DdTextField(
                        draft.cronExpression,
                        { draft = draft.copy(cronExpression = it); validationError = null },
                        DEFAULT_CRON_EXPRESSION,
                        testTag = "schedule_cron_expression",
                    )
                    CronSummaryLine(draft.cronExpression)
                } else {
                    EditorRow(
                        stringResource(R.string.schedules_date_label),
                        dateFormatter.format(draft.date),
                        Modifier.testTag("schedule_date_row"),
                        0.dp,
                    ) { showDatePicker = true }
                    TimeRow(draft.time) { showTimePicker = true }
                }
                EditorRow(stringResource(R.string.schedules_deliver_to), conversationLabel(draft)) { showConversationPicker = true }
                if (isEditing && draft.sessionId.isNotBlank()) {
                    DdTextAction(
                        stringResource(R.string.schedules_open_conversation),
                        { onOpenConversation(draft.sessionId) },
                        Modifier.testTag("schedule_open_conversation"),
                        colors.inkSecondary,
                        draft.conversationTitle.isNotBlank() && !saving,
                    )
                }
                EnableRow(draft.enabled) { draft = draft.copy(enabled = it) }
                val nextRun = draft.nextRunPreview(ZonedDateTime.now())
                Text(
                    stringResource(
                        R.string.schedules_next_run_line,
                        nextRun?.format(instantFormatter) ?: stringResource(R.string.schedules_never),
                    ),
                    Modifier.padding(top = 16.dp).testTag("schedule_next_run"),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                validationError?.let { ErrorText(stringResource(it)) }
                externalError?.let { ErrorText(it) }
                Spacer(Modifier.height(24.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (isEditing) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        DdTextAction(stringResource(R.string.schedules_run_now), onRunNow, Modifier.testTag("schedule_run_now"), enabled = !saving)
                        Spacer(Modifier.weight(1f))
                        DdTextAction(stringResource(R.string.schedules_delete), { showDeleteConfirm = true }, Modifier.testTag("schedule_delete"), colors.danger, !saving)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                DdPrimaryButton(stringResource(R.string.schedules_save), { submit() }, Modifier.fillMaxWidth().testTag("schedule_save"), enabled = !saving)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showTimePicker) DdTimePicker(draft.time, { draft = draft.copy(time = it); validationError = null }) { showTimePicker = false }
    if (showDatePicker) DdDatePicker(draft.date, { draft = draft.copy(date = it); validationError = null }) { showDatePicker = false }
    if (showConversationPicker) ConversationPickerSheet(runtime, draft.sessionId, { session ->
        draft = draft.copy(
            sessionId = session.id,
            sessionFile = session.filePath,
            cwd = session.cwd,
            conversationTitle = session.title,
        )
        validationError = null
        showConversationPicker = false
    }) { showConversationPicker = false }
    if (showDeleteConfirm) DdDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = stringResource(R.string.schedules_delete_title),
        content = {
            Text(
                stringResource(R.string.schedules_delete_message, draft.name),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        },
    ) {
        DdTextAction(
            stringResource(R.string.schedules_cancel),
            { showDeleteConfirm = false },
            Modifier.testTag("schedules_delete_cancel"),
            colors.inkSecondary,
        )
        DdTextAction(
            stringResource(R.string.schedules_delete_confirm),
            { showDeleteConfirm = false; onDelete() },
            Modifier.testTag("schedule_delete_confirm"),
            colors.danger,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, Modifier.padding(top = 20.dp, bottom = 8.dp), style = DdType.micro, color = deepDeepColors().inkTertiary)
}

@Composable
private fun TimeRow(time: LocalTime, onClick: () -> Unit) {
    EditorRow(
        stringResource(R.string.schedules_time_label),
        time.format(SCHEDULE_TIME_FORMAT),
        Modifier.testTag("schedule_time_row"),
        onClick = onClick,
    )
}

@Composable
private fun EditorRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(top = topPadding, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DdType.body, color = colors.ink, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(4.dp))
        DdIcon(DdIcons.ChevronRight, colors.inkTertiary, size = 14.dp)
    }
}

@Composable
private fun EnableRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.schedules_enabled_label), Modifier.weight(1f), style = DdType.body, color = deepDeepColors().ink)
        DdSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RepeatRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.schedules_repeat_label), Modifier.weight(1f), style = DdType.body, color = deepDeepColors().ink)
        DdSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, Modifier.padding(top = 12.dp), style = DdType.bodySmall, color = deepDeepColors().danger)
}

@Composable
private fun CronSummaryLine(expression: String) {
    val colors = deepDeepColors()
    val summary = cronSummaryLabel(expression)
    Text(
        summary ?: stringResource(R.string.schedules_cron_invalid),
        Modifier.padding(top = 6.dp),
        style = DdType.bodySmall,
        color = if (summary == null) colors.danger else colors.inkTertiary,
    )
}

@Composable
private fun conversationLabel(draft: ScheduleDraft): String = when {
    draft.conversationTitle.isNotBlank() -> draft.conversationTitle
    draft.sessionId.isNotBlank() -> stringResource(R.string.schedules_conversation_deleted)
    else -> stringResource(R.string.schedules_conversation_label)
}

@Composable
private fun ConversationPickerSheet(
    runtime: ConversationQueries,
    selectedId: String,
    onSelect: (ConversationSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    var loading by remember { mutableStateOf(true) }
    var sessions by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var workspaces by remember { mutableStateOf<List<RuntimeWorkspace>>(emptyList()) }
    LaunchedEffect(Unit) {
        workspaces = runCatching { runtime.workspaces() }.getOrDefault(emptyList())
        sessions = runCatching { runtime.allSessions() }.getOrDefault(emptyList())
            .filterNot { it.archived }
            .sortedByDescending { it.updatedAt }
        loading = false
    }
    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).testTag("schedule_conversation_picker")) {
            DdSheetHeader(stringResource(R.string.schedules_conversation_picker_title), onDismiss)
            when {
                loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    DdSpinner(size = 20.dp, strokeWidth = 2.dp)
                }

                sessions.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.schedules_conversation_empty), style = DdType.bodySmall, color = colors.inkTertiary)
                }

                else -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    sessions.forEachIndexed { index, session ->
                        val workspaceName = workspaces.firstOrNull { it.cwd == session.cwd }?.name.orEmpty()
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(session) }.heightIn(min = 56.dp).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(session.title, style = DdType.body, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (workspaceName.isNotBlank()) {
                                    Text(workspaceName, style = DdType.bodySmall, color = colors.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (session.id == selectedId) DdIcon(DdIcons.Check, colors.accent, size = 18.dp)
                        }
                        if (index != sessions.lastIndex) DdHairline()
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
