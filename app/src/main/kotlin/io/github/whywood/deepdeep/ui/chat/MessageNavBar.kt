package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.deepDeepColors

private val NavHitWidth = 24.dp
private val NavTickWidth = 8.dp
private val NavTickHeight = 2.dp
private val NavTickRowHeight = 16.dp
private val NavTickShape = RoundedCornerShape(NavTickHeight / 2)

@Composable
internal fun MessageNavBar(
    segments: List<Int>,
    activeSegmentIndex: Int,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val colors = deepDeepColors()
    val latestOnJump by rememberUpdatedState(onJump)
    val navDescription = stringResource(R.string.chat_history_nav)
    Column(
        modifier = modifier
            .width(NavHitWidth)
            .semantics { contentDescription = navDescription }
            .testTag("chat_message_nav"),
    ) {
        segments.forEachIndexed { index, messageIndex ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NavTickRowHeight)
                    .clickable(role = Role.Button) { latestOnJump(messageIndex) },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(NavTickWidth)
                        .height(NavTickHeight)
                        .clip(NavTickShape)
                        .background(
                            if (index == activeSegmentIndex) {
                                colors.accent
                            } else {
                                colors.inkTertiary.copy(alpha = 0.30f)
                            },
                        ),
                )
            }
        }
    }
}
