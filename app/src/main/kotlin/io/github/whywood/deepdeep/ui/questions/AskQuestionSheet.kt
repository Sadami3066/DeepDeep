package io.github.whywood.deepdeep.ui.questions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.QuestionOption

private val RECOMMENDED_SUFFIXES = listOf(
    "(Recommended)",
    "（Recommended）",
    "（推荐）",
    "(推荐)",
)

private fun recommendedSuffix(label: String): String? =
    RECOMMENDED_SUFFIXES.firstOrNull { label.endsWith(it, ignoreCase = true) }

@Composable
fun AskQuestionSheet(
    pending: PendingQuestion,
    onAnswer: (List<QuestionAnswer>) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = deepDeepColors()
    val questions = pending.questions
    val count = questions.size
    var index by remember(pending.requestId) { mutableStateOf(0) }
    var selections by remember(pending.requestId) {
        mutableStateOf(List(count) { emptyList<String>() })
    }
    var customs by remember(pending.requestId) { mutableStateOf(List(count) { "" }) }
    var skipped by remember(pending.requestId) { mutableStateOf(List(count) { false }) }
    var showError by remember(pending.requestId) { mutableStateOf(false) }

    if (count == 0) {
        LaunchedEffect(pending.requestId) { onCancel() }
        return
    }
    val safeIndex = index.coerceIn(0, count - 1)
    val question = questions[safeIndex]
    val selected = selections[safeIndex]
    val custom = customs[safeIndex]
    val hasAnswer = selected.isNotEmpty() || custom.isNotBlank()
    val last = safeIndex == count - 1

    fun updateSelection(next: List<String>) {
        selections = selections.toMutableList().also { it[safeIndex] = next }
        skipped = skipped.toMutableList().also { it[safeIndex] = false }
        showError = false
    }

    fun updateCustom(next: String) {
        customs = customs.toMutableList().also { it[safeIndex] = next }
        skipped = skipped.toMutableList().also { it[safeIndex] = false }
        showError = false
    }

    fun buildAnswers(): List<QuestionAnswer> = questions.mapIndexed { i, item ->
        QuestionAnswer(
            questionId = item.id,
            selected = selections[i],
            custom = customs[i].takeIf { it.isNotBlank() },
            skipped = skipped[i],
        )
    }

    fun submit() {
        onAnswer(buildAnswers())
    }

    fun advance(markSkipped: Boolean) {
        if (markSkipped) {
            selections = selections.toMutableList().also { it[safeIndex] = emptyList() }
            customs = customs.toMutableList().also { it[safeIndex] = "" }
            skipped = skipped.toMutableList().also { it[safeIndex] = true }
            showError = false
        } else if (!hasAnswer) {
            showError = true
            return
        }
        if (last) submit() else index = safeIndex + 1
    }

    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    val progress by animateFloatAsState(
        targetValue = (safeIndex + 1).toFloat() / count,
        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
        label = "ask-progress",
    )

    DdSheet(onDismiss = onCancel) {
        Column(Modifier.fillMaxWidth().testTag("ask_question_sheet")) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ask_title),
                    modifier = Modifier.weight(1f),
                    style = DdType.heading,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.ask_progress, safeIndex + 1, count),
                    modifier = Modifier.padding(end = 6.dp),
                    style = DdType.numeric,
                    color = colors.inkTertiary,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = onCancel,
                    contentDescription = stringResource(R.string.ask_cancel),
                    tint = colors.inkSecondary,
                    iconSize = 20.dp,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.hairline),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(2.dp).background(colors.accent))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                question.header?.takeIf { it.isNotBlank() }?.let { header ->
                    Text(
                        text = header,
                        style = DdType.micro,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = question.question,
                    style = DdType.body,
                    color = colors.ink,
                )
                if (question.multiSelect) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.ask_multi_hint),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                if (question.options.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        question.options.forEachIndexed { optionIndex, option ->
                            QuestionOptionRow(
                                option = option,
                                selected = option.label in selected,
                                onClick = {
                                    if (question.multiSelect) {
                                        updateSelection(
                                            if (option.label in selected) {
                                                selected - option.label
                                            } else {
                                                selected + option.label
                                            },
                                        )
                                    } else {
                                        updateSelection(
                                            if (option.label in selected) emptyList() else listOf(option.label),
                                        )
                                    }
                                },
                                testTag = "ask_option_$optionIndex",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.ask_custom_label),
                    style = DdType.label,
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.height(8.dp))
                DdTextArea(
                    value = custom,
                    onValueChange = { updateCustom(it) },
                    placeholder = stringResource(R.string.ask_custom_placeholder),
                    minHeight = 96.dp,
                    maxHeight = 180.dp,
                    testTag = "ask_custom",
                )
                if (showError && !hasAnswer) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.ask_validation),
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DdTextAction(
                    text = stringResource(R.string.ask_skip),
                    onClick = { advance(markSkipped = true) },
                    modifier = Modifier.testTag("ask_skip"),
                    color = colors.inkSecondary,
                )
                Spacer(Modifier.weight(1f))
                if (safeIndex > 0) {
                    DdTextAction(
                        text = stringResource(R.string.ask_previous),
                        onClick = {
                            index = safeIndex - 1
                            showError = false
                        },
                        modifier = Modifier.testTag("ask_previous"),
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                DdPrimaryButton(
                    text = stringResource(if (last) R.string.ask_submit else R.string.ask_next),
                    onClick = { advance(markSkipped = false) },
                    modifier = Modifier
                        .widthIn(min = 112.dp)
                        .testTag("ask_submit"),
                )
            }
        }
    }
}

@Composable
private fun QuestionOptionRow(
    option: QuestionOption,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    val shape = RoundedCornerShape(16.dp)
    val suffix = recommendedSuffix(option.label)
    val recommended = suffix != null
    val label = if (suffix == null) option.label else option.label.removeSuffix(suffix).trimEnd()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = shape,
        background = if (selected) colors.accentSoft else colors.raised,
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = DdType.label,
                        color = if (selected) colors.accent else colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (recommended) {
                        Spacer(Modifier.width(6.dp))
                        RecommendedTag()
                    }
                }
                option.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
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
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = DdType.chip,
        color = colors.accent,
        maxLines = 1,
    )
}
