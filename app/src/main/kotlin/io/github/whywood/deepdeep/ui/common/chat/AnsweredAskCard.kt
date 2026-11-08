package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun AnsweredAskCard(
    record: QuestionRecord,
    toolCallId: String,
    modifier: Modifier = Modifier,
) {
    if (record.questions.isEmpty()) return
    val colors = deepDeepColors()
    val questionKey = record.questions.joinToString("|") { it.id }
    var expanded by rememberSaveable(questionKey) { mutableStateOf(false) }
    val answerById = record.answers.associateBy { it.questionId }
    val answeredCount = record.questions.count { question -> answerText(answerById[question.id]) != null }
    val skippedCount = record.questions.size - answeredCount
    val headerText = run {
        val base = stringResource(R.string.ask_card_answered_count, answeredCount)
        if (skippedCount > 0) "$base · ${stringResource(R.string.ask_card_skipped)}" else base
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "answered-ask-chevron",
    )
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DdSpacing.Xs)
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { stateDescription = state }
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(horizontal = DdSpacing.Sm)
                .testTag("answered_ask_toggle_$toolCallId"),
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
            Spacer(Modifier.width(DdSpacing.Sm))
            DdIcon(
                icon = DdIcons.ChevronRight,
                modifier = Modifier.rotate(rotation),
                tint = colors.inkTertiary.copy(alpha = DdAlpha.Secondary),
                size = 12.dp,
            )
            Spacer(Modifier.width(DdSpacing.Xxs))
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
            Column(Modifier.fillMaxWidth().padding(top = DdSpacing.Sm, bottom = 6.dp)) {
                record.questions.forEachIndexed { index, question ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    val answer = answerText(answerById[question.id])
                    question.header?.takeIf { it.isNotBlank() }?.let { header ->
                        Text(
                            text = header,
                            style = DdType.micro,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(DdSpacing.Xs))
                    }
                    Text(
                        text = question.question,
                        style = DdType.body,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(DdSpacing.Xs))
                    Text(
                        text = answer ?: stringResource(R.string.ask_card_skipped),
                        style = DdType.bodySmall,
                        color = if (answer == null) colors.inkTertiary else colors.inkSecondary,
                    )
                }
            }
        }
    }
}
