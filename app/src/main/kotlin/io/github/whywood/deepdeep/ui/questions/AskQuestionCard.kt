package io.github.whywood.deepdeep.ui.questions

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.UserQuestion

private val RECOMMENDED_SUFFIXES = listOf(
    "(Recommended)",
    "（Recommended）",
    "（推荐）",
    "(推荐)",
)

private data class Draft(
    val index: Int,
    val selections: List<List<String>>,
    val customs: List<String>,
    val skipped: List<Boolean>,
)

private val DraftSaver: Saver<Draft, Bundle> = Saver(
    save = { draft ->
        Bundle().apply {
            putInt("index", draft.index)
            putInt("count", draft.selections.size)
            draft.selections.forEachIndexed { index, labels ->
                putStringArrayList("selected_$index", ArrayList(labels))
            }
            draft.customs.forEachIndexed { index, text -> putString("custom_$index", text) }
            draft.skipped.forEachIndexed { index, value -> putBoolean("skipped_$index", value) }
        }
    },
    restore = { bundle ->
        val count = bundle.getInt("count")
        Draft(
            index = bundle.getInt("index"),
            selections = List(count) { index -> bundle.getStringArrayList("selected_$index").orEmpty() },
            customs = List(count) { index -> bundle.getString("custom_$index").orEmpty() },
            skipped = List(count) { index -> bundle.getBoolean("skipped_$index") },
        )
    },
)

@Composable
fun AskQuestionCard(
    questions: List<UserQuestion>,
    answers: List<QuestionAnswer>?,
    submitted: Boolean,
    onAnswer: (List<QuestionAnswer>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (questions.isEmpty()) return
    val colors = deepDeepColors()
    val count = questions.size
    val pendingKey = questions.joinToString("|") { it.id }
    var expanded by rememberSaveable(pendingKey) { mutableStateOf(answers == null) }
    var draft by rememberSaveable(pendingKey, stateSaver = DraftSaver) {
        mutableStateOf(
            Draft(
                index = 0,
                selections = List(count) { emptyList() },
                customs = List(count) { "" },
                skipped = List(count) { false },
            ),
        )
    }
    var showValidation by remember(pendingKey) { mutableStateOf(false) }

    val safeIndex = draft.index.coerceIn(0, count - 1)
    val question = questions[safeIndex]
    val selected = draft.selections.getOrElse(safeIndex) { emptyList() }
    val custom = draft.customs.getOrElse(safeIndex) { "" }
    val last = safeIndex == count - 1
    val locked = submitted && answers == null
    val answerById = answers?.associateBy { it.questionId }
    val answeredCount = if (answers == null) {
        count
    } else {
        questions.count { answerText(answerById?.get(it.id)) != null }
    }
    val skippedCount = if (answers == null) 0 else count - answeredCount
    val resolvedCount = if (answers == null) {
        (0 until count).count { index ->
            draft.selections.getOrElse(index) { emptyList() }.isNotEmpty() ||
                draft.customs.getOrElse(index) { "" }.isNotBlank() ||
                draft.skipped.getOrElse(index) { false }
        }
    } else {
        answeredCount
    }
    val progress by animateFloatAsState(
        targetValue = resolvedCount.toFloat() / count,
        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "ask-card-progress",
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "ask-card-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val headerText = when {
        answers != null -> {
            val base = stringResource(R.string.ask_card_answered_count, answeredCount)
            if (skippedCount > 0) "$base · ${stringResource(R.string.ask_card_skipped)}" else base
        }

        submitted -> stringResource(R.string.ask_card_answered_count, count)

        else -> stringResource(R.string.ask_card_question)
    }

    fun select(option: QuestionOption) {
        if (locked) return
        if (question.multiSelect) {
            val current = draft.selections.getOrElse(safeIndex) { emptyList() }
            val next = if (option.label in current) current - option.label else current + option.label
            draft = draft.copy(
                selections = draft.selections.replaced(safeIndex, next),
                skipped = draft.skipped.replaced(safeIndex, false),
            )
        } else {
            draft = draft.copy(
                selections = draft.selections.replaced(safeIndex, listOf(option.label)),
                customs = draft.customs.replaced(safeIndex, ""),
                skipped = draft.skipped.replaced(safeIndex, false),
            )
            if (!last) draft = draft.copy(index = safeIndex + 1)
        }
        showValidation = false
    }

    fun updateCustom(value: String) {
        if (locked) return
        draft = draft.copy(
            customs = draft.customs.replaced(safeIndex, value),
            selections = if (question.multiSelect) {
                draft.selections
            } else {
                draft.selections.replaced(safeIndex, emptyList())
            },
            skipped = draft.skipped.replaced(safeIndex, false),
        )
        showValidation = false
    }

    fun buildAnswers(): List<QuestionAnswer> = questions.mapIndexed { index, item ->
        QuestionAnswer(
            questionId = item.id,
            selected = draft.selections.getOrElse(index) { emptyList() },
            custom = draft.customs.getOrElse(index) { "" }.trim().takeIf { it.isNotEmpty() },
            skipped = draft.skipped.getOrElse(index) { false },
        )
    }

    fun submit() {
        onAnswer(buildAnswers())
    }

    fun advance() {
        val answered = selected.isNotEmpty() ||
            custom.isNotBlank() ||
            draft.skipped.getOrElse(safeIndex) { false }
        if (!answered) {
            showValidation = true
            return
        }
        showValidation = false
        if (last) submit() else draft = draft.copy(index = safeIndex + 1)
    }

    fun skip() {
        draft = draft.copy(
            selections = draft.selections.replaced(safeIndex, emptyList()),
            customs = draft.customs.replaced(safeIndex, ""),
            skipped = draft.skipped.replaced(safeIndex, true),
        )
        showValidation = false
        if (last) submit() else draft = draft.copy(index = safeIndex + 1)
    }

    Column(modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("ask_card")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .semantics { stateDescription = state }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = 8.dp)
                .testTag("ask_toggle"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headerText,
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (answers == null) {
                Text(
                    text = stringResource(R.string.ask_progress, safeIndex + 1, count),
                    style = DdType.numeric,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.width(6.dp))
            }
            DdIcon(
                icon = DdIcons.ChevronDown,
                modifier = Modifier.rotate(rotation),
                tint = colors.inkTertiary.copy(alpha = 0.7f),
                size = 13.dp,
            )
            Spacer(Modifier.width(2.dp))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(DdMotion.FAST)),
            exit = shrinkVertically(
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(DdMotion.FAST)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ProgressLine(progress)
                if (answers == null) {
                    Spacer(Modifier.height(12.dp))
                    question.header?.takeIf { it.isNotBlank() }?.let { header ->
                        Text(
                            text = header,
                            style = DdType.micro,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = question.question,
                        style = DdType.body,
                        color = colors.ink,
                    )
                    if (question.multiSelect) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.ask_multi_hint),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                    if (question.options.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            question.options.forEachIndexed { optionIndex, option ->
                                OptionRow(
                                    option = option,
                                    number = optionIndex + 1,
                                    selected = option.label in selected,
                                    multiSelect = question.multiSelect,
                                    enabled = !locked,
                                    onClick = { select(option) },
                                    testTag = "ask_option_$optionIndex",
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        DdTextField(
                            value = custom,
                            onValueChange = { updateCustom(it) },
                            placeholder = stringResource(R.string.ask_card_custom_hint),
                            modifier = Modifier.alpha(if (locked) 0.55f else 1f),
                            testTag = "ask_custom",
                        )
                    } else {
                        Spacer(Modifier.height(12.dp))
                        DdTextArea(
                            value = custom,
                            onValueChange = { updateCustom(it) },
                            placeholder = stringResource(R.string.ask_card_custom_hint),
                            modifier = Modifier.alpha(if (locked) 0.55f else 1f),
                            minHeight = 96.dp,
                            maxHeight = 180.dp,
                            testTag = "ask_custom",
                        )
                    }
                    if (showValidation) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.ask_card_validation),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (locked) {
                        Text(
                            text = stringResource(R.string.ask_card_submitted),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DdTextAction(
                                text = stringResource(R.string.ask_skip),
                                onClick = { skip() },
                                modifier = Modifier.testTag("ask_skip"),
                                color = colors.inkSecondary,
                            )
                            Spacer(Modifier.weight(1f))
                            if (safeIndex > 0) {
                                DdTextAction(
                                    text = stringResource(R.string.ask_previous),
                                    onClick = {
                                        showValidation = false
                                        draft = draft.copy(index = safeIndex - 1)
                                    },
                                    modifier = Modifier.testTag("ask_previous"),
                                    color = colors.inkSecondary,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            DdPrimaryButton(
                                text = stringResource(if (last) R.string.ask_submit else R.string.ask_next),
                                onClick = { advance() },
                                modifier = Modifier
                                    .height(40.dp)
                                    .widthIn(min = 96.dp)
                                    .testTag(if (last) "ask_submit" else "ask_next"),
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    questions.forEachIndexed { index, item ->
                        if (index > 0) Spacer(Modifier.height(14.dp))
                        val text = answerText(answerById?.get(item.id))
                        item.header?.takeIf { it.isNotBlank() }?.let { header ->
                            Text(
                                text = header,
                                style = DdType.micro,
                                color = colors.inkTertiary,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = item.question,
                            style = DdType.body,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = text ?: stringResource(R.string.ask_card_skipped),
                            style = DdType.bodySmall,
                            color = if (text == null) colors.inkTertiary else colors.inkSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressLine(progress: Float) {
    val colors = deepDeepColors()
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(colors.hairline.copy(alpha = 0.45f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(colors.accent),
        )
    }
}

@Composable
private fun OptionRow(
    option: QuestionOption,
    number: Int,
    selected: Boolean,
    multiSelect: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(12.dp)
    val suffix = recommendedSuffix(option.label)
    val label = displayLabel(option.label)
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag(testTag),
        shape = shape,
        background = if (selected) colors.accentSoft else Color.Transparent,
        enabled = enabled,
        contentDescription = label,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (selected) colors.accent else colors.hairline.copy(alpha = 0.6f),
                    shape = shape,
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(18.dp)) {
                if (multiSelect) {
                    if (selected) {
                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 15.dp)
                    }
                } else {
                    Text(
                        text = number.toString(),
                        style = DdType.numeric,
                        color = if (selected) colors.accent else colors.inkTertiary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f, fill = false),
                        style = DdType.label,
                        color = if (selected) colors.accent else colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (suffix != null) {
                        Spacer(Modifier.width(6.dp))
                        RecommendedTag()
                    }
                }
                option.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = description,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendedTag() {
    val colors = deepDeepColors()
    Text(
        text = stringResource(R.string.ask_recommended),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accentSoft)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = DdType.chip,
        color = colors.accent,
        maxLines = 1,
    )
}

private fun recommendedSuffix(label: String): String? =
    RECOMMENDED_SUFFIXES.firstOrNull { label.endsWith(it, ignoreCase = true) }

private fun displayLabel(label: String): String {
    val suffix = recommendedSuffix(label) ?: return label
    return label.removeSuffix(suffix).trimEnd()
}

internal fun answerText(answer: QuestionAnswer?): String? {
    if (answer == null || answer.skipped) return null
    val parts = answer.selected.map(::displayLabel) +
        listOfNotNull(answer.custom?.trim()?.takeIf { it.isNotEmpty() })
    return parts.takeIf { it.isNotEmpty() }?.joinToString("、")
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }
