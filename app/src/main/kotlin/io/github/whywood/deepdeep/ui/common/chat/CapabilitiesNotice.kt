package io.github.whywood.deepdeep.ui.common.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
import io.github.whywood.deepdeep.design.LocalDdMotionEnabled
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun CapabilitiesNotice(
    messageId: Long,
    toolsAdded: List<String>,
    toolsRemoved: List<String>,
    skillsAdded: List<String>,
    skillsRemoved: List<String>,
) {
    val colors = deepDeepColors()
    val motionEnabled = LocalDdMotionEnabled.current
    val title = stringResource(R.string.chat_notice_capabilities_changed)
    val counts = listOfNotNull(
        toolsAdded.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.chat_notice_capabilities_tools_added, it.size)
        },
        toolsRemoved.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.chat_notice_capabilities_tools_removed, it.size)
        },
        skillsAdded.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.chat_notice_capabilities_skills_added, it.size)
        },
        skillsRemoved.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.chat_notice_capabilities_skills_removed, it.size)
        },
    )
    val summary = if (counts.isEmpty()) title else "$title · ${counts.joinToString(" / ")}"
    val groups = listOfNotNull(
        toolsAdded.takeIf { it.isNotEmpty() }?.let { R.string.chat_notice_capabilities_added_tools to it },
        toolsRemoved.takeIf { it.isNotEmpty() }?.let { R.string.chat_notice_capabilities_removed_tools to it },
        skillsAdded.takeIf { it.isNotEmpty() }?.let { R.string.chat_notice_capabilities_added_skills to it },
        skillsRemoved.takeIf { it.isNotEmpty() }?.let { R.string.chat_notice_capabilities_removed_skills to it },
    )
    val expandable = groups.isNotEmpty()
    var expanded by rememberSaveable(messageId) { mutableStateOf(false) }
    val state = stringResource(
        if (expanded) R.string.assistant_step_expanded else R.string.assistant_step_collapsed,
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = if (motionEnabled) tween(DdMotion.FAST, easing = DdMotion.EaseOut) else snap(),
        label = "capabilities-chevron",
    )
    val textStyle = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandable) Modifier.heightIn(min = NoticeMinHeight) else Modifier)
                .semantics { stateDescription = state }
                .clickable(enabled = expandable, role = Role.Button) {
                    if (expandable) expanded = !expanded
                }
                .padding(horizontal = DdSpacing.Md, vertical = DdSpacing.Xxs)
                .testTag("capabilities_notice"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = summary,
                style = textStyle,
                color = colors.inkTertiary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (expandable) {
                Spacer(Modifier.width(DdSpacing.Xxs))
                DdIcon(
                    icon = DdIcons.ChevronRight,
                    modifier = Modifier.rotate(rotation),
                    tint = colors.inkTertiary.copy(alpha = DdAlpha.Secondary),
                    size = 12.dp,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (motionEnabled) {
                fadeIn(tween(DdMotion.FAST)) + expandVertically(
                    animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
                    expandFrom = Alignment.Top,
                )
            } else {
                EnterTransition.None
            },
            exit = if (motionEnabled) {
                fadeOut(tween(DdMotion.FAST)) + shrinkVertically(
                    animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
                    shrinkTowards = Alignment.Top,
                )
            } else {
                ExitTransition.None
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DdSpacing.Md, end = DdSpacing.Md, bottom = DdSpacing.Xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                groups.forEach { (labelRes, names) ->
                    Text(
                        text = stringResource(
                            labelRes,
                            names.joinToString(stringResource(R.string.chat_notice_capabilities_separator)),
                        ),
                        style = textStyle,
                        color = colors.inkTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private val NoticeMinHeight = 44.dp
