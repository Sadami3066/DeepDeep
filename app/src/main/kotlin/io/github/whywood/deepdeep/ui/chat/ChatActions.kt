package io.github.whywood.deepdeep.ui.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
internal fun ChatActionBar(
    messageId: Long,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DdIconButton(
            icon = DdIcons.Copy,
            onClick = onCopy,
            contentDescription = stringResource(R.string.assistant_action_copy),
            modifier = Modifier.testTag("message_${messageId}_copy"),
            size = 38.dp,
            iconSize = 17.dp,
            tint = colors.inkTertiary,
        )
        DdIconButton(
            icon = DdIcons.Share,
            onClick = onShare,
            contentDescription = stringResource(R.string.assistant_action_share),
            modifier = Modifier.testTag("message_${messageId}_share"),
            size = 38.dp,
            iconSize = 17.dp,
            tint = colors.inkTertiary,
        )
    }
}

internal fun copyToClipboard(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("deepdeep", text))
    return true
}

internal fun shareText(context: Context, text: String, chooserTitle: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    return try {
        context.startActivity(Intent.createChooser(send, chooserTitle))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
