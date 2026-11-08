package io.github.whywood.deepdeep.ui.timers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ConversationSummary
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun TimerEditor(
    initialDraft: TimerDraft,
    isEditing: Boolean,
    externalError: String?,
    saving: Boolean,
    conversations: List<ConversationSummary>,
    workspaceNames: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (TimerDraft) -> Unit,
    onRunNow: (TimerDraft) -> Unit,
    onDelete: (TimerDraft) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val colors = deepDeepColors()
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val instantFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }
    var draft by rememberSaveable(initialDraft, stateSaver = TimerDraftSaver) { mutableStateOf(initialDraft) }
    var validationError by remember { mutableStateOf<Int?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showConversationPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun submit() {
        val prepared = if (draft.name.isBlank() && draft.prompt.isNotBlank()) draft.copy(name = TimerDraft.preview(draft.prompt)) else draft
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
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).testTag("timer_editor")) {
            DdSheetHeader(stringResource(if (isEditing) R.string.timers_edit_title else R.string.timers_new_title), onDismiss)
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DdSpacing.Xxl)) {
                FieldLabel(stringResource(R.string.timers_name_label))
                DdTextField(draft.name, { draft = draft.copy(name = it); validationError = null }, stringResource(R.string.timers_name_hint))
                FieldLabel(stringResource(R.string.timers_prompt_label))
                DdTextField(
                    draft.prompt,
                    { draft = draft.copy(prompt = it); validationError = null },
                    stringResource(R.string.timers_prompt_hint),
                    singleLine = false,
                )
                RepeatRow(draft.repeat) { draft = draft.copy(repeat = it); validationError = null }
                if (draft.repeat) {
                    Spacer(Modifier.height(DdSpacing.Md))
                    DdTextField(
                        draft.cronExpression,
                        { draft = draft.copy(cronExpression = it); validationError = null },
                        DEFAULT_CRON_EXPRESSION,
                        testTag = "timer_cron_expression",
                    )
                    CronSummaryLine(draft.cronExpression)
                } else {
                    EditorRow(
                        stringResource(R.string.timers_date_label),
                        dateFormatter.format(draft.date),
                        Modifier.testTag("timer_date_row"),
                        0.dp,
                    ) { showDatePicker = true }
                    TimeRow(draft.time) { showTimePicker = true }
                }
                EditorRow(stringResource(R.string.timers_deliver_to), conversationLabel(draft)) { showConversationPicker = true }
                if (isEditing && draft.sessionId.isNotBlank()) {
                    DdTextAction(
                        stringResource(R.string.timers_open_conversation),
                        { onOpenConversation(draft.sessionId) },
                        Modifier.testTag("timer_open_conversation"),
                        colors.inkSecondary,
                        draft.conversationTitle.isNotBlank() && !saving,
                    )
                }
                EnableRow(draft.enabled) { draft = draft.copy(enabled = it) }
                val nextRun = draft.nextRunPreview(ZonedDateTime.now())
                Text(
                    stringResource(
                        R.string.timers_next_run_line,
                        nextRun?.format(instantFormatter) ?: stringResource(R.string.timers_never),
                    ),
                    Modifier.padding(top = DdSpacing.Lg).testTag("timer_next_run"),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                validationError?.let { ErrorText(stringResource(it)) }
                externalError?.let { ErrorText(it) }
                Spacer(Modifier.height(DdSpacing.Xxl))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Xxl, vertical = DdSpacing.Sm)) {
                if (isEditing) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        DdTextAction(stringResource(R.string.timers_run_now), { onRunNow(draft) }, Modifier.testTag("timer_run_now"), enabled = !saving)
                        Spacer(Modifier.weight(1f))
                        DdTextAction(stringResource(R.string.timers_delete), { showDeleteConfirm = true }, Modifier.testTag("timer_delete"), colors.danger, !saving)
                    }
                    Spacer(Modifier.height(DdSpacing.Sm))
                }
                DdPrimaryButton(stringResource(R.string.timers_save), { submit() }, Modifier.fillMaxWidth().testTag("timer_save"), enabled = !saving)
                Spacer(Modifier.height(DdSpacing.Sm))
            }
        }
    }

    if (showTimePicker) DdTimePicker(draft.time, { draft = draft.copy(time = it); validationError = null }) { showTimePicker = false }
    if (showDatePicker) DdDatePicker(draft.date, { draft = draft.copy(date = it); validationError = null }) { showDatePicker = false }
    if (showConversationPicker) ConversationPickerSheet(conversations, workspaceNames, draft.sessionId, { session ->
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
        title = stringResource(R.string.timers_delete_title),
        content = {
            Text(
                stringResource(R.string.timers_delete_message, draft.name),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
        },
    ) {
        DdTextAction(
            stringResource(R.string.timers_cancel),
            { showDeleteConfirm = false },
            Modifier.testTag("timers_delete_cancel"),
            colors.inkSecondary,
        )
        DdTextAction(
            stringResource(R.string.timers_delete_confirm),
            { showDeleteConfirm = false; onDelete(draft) },
            Modifier.testTag("timer_delete_confirm"),
            colors.danger,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, Modifier.padding(top = DdSpacing.Xl, bottom = DdSpacing.Sm), style = DdType.micro, color = deepDeepColors().inkTertiary)
}

@Composable
private fun TimeRow(time: LocalTime, onClick: () -> Unit) {
    EditorRow(
        stringResource(R.string.timers_time_label),
        time.format(TIMER_TIME_FORMAT),
        Modifier.testTag("timer_time_row"),
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
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(top = topPadding, bottom = DdSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = DdType.body, color = colors.ink, maxLines = 1)
        Spacer(Modifier.width(DdSpacing.Md))
        Text(
            value,
            Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(DdSpacing.Xs))
        DdIcon(DdIcons.ChevronRight, colors.inkTertiary, size = 14.dp)
    }
}

@Composable
private fun EnableRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = DdSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.timers_enabled_label), Modifier.weight(1f), style = DdType.body, color = deepDeepColors().ink)
        DdSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RepeatRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = DdSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.timers_repeat_label), Modifier.weight(1f), style = DdType.body, color = deepDeepColors().ink)
        DdSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, Modifier.padding(top = DdSpacing.Md), style = DdType.bodySmall, color = deepDeepColors().danger)
}

@Composable
private fun CronSummaryLine(expression: String) {
    val colors = deepDeepColors()
    val summary = cronSummaryLabel(expression)
    Text(
        summary ?: stringResource(R.string.timers_cron_invalid),
        Modifier.padding(top = 6.dp),
        style = DdType.bodySmall,
        color = if (summary == null) colors.danger else colors.inkTertiary,
    )
}

@Composable
private fun conversationLabel(draft: TimerDraft): String = when {
    draft.conversationTitle.isNotBlank() -> draft.conversationTitle
    draft.sessionId.isNotBlank() -> stringResource(R.string.timers_conversation_deleted)
    else -> stringResource(R.string.timers_conversation_label)
}

@Composable
private fun ConversationPickerSheet(
    conversations: List<ConversationSummary>,
    workspaceNames: Map<String, String>,
    selectedId: String,
    onSelect: (ConversationSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).testTag("timer_conversation_picker")) {
            DdSheetHeader(stringResource(R.string.timers_conversation_picker_title), onDismiss)
            if (conversations.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.timers_conversation_empty), style = DdType.bodySmall, color = colors.inkTertiary)
                }
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = DdSpacing.Xxl)) {
                    conversations.forEachIndexed { index, session ->
                        val workspaceName = workspaceNames[session.cwd].orEmpty()
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
                        if (index != conversations.lastIndex) DdHairline()
                    }
                    Spacer(Modifier.height(DdSpacing.Xxl))
                }
            }
        }
    }
}
