package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun AssistantActionBar(
    messageId: Long,
    answer: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = deepDeepColors()
    if (answer.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DdIconButton(
            icon = DdIcons.Copy,
            onClick = onCopy,
            contentDescription = stringResource(R.string.assistant_action_copy),
            modifier = Modifier.testTag("message_${messageId}_copy"),
            size = 44.dp,
            iconSize = 18.dp,
            tint = colors.inkTertiary,
        )
        DdIconButton(
            icon = DdIcons.Share,
            onClick = onShare,
            contentDescription = stringResource(R.string.assistant_action_share),
            modifier = Modifier.testTag("message_${messageId}_share"),
            size = 44.dp,
            iconSize = 18.dp,
            tint = colors.inkTertiary,
        )
    }
}
