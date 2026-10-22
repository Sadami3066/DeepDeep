package io.github.whywood.deepdeep.ui.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.roundToInt

private val MessageMenuWidth = 150.dp

@Composable
internal fun ChatActionBar(
    messageId: Long,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
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
            size = 34.dp,
            iconSize = 16.dp,
            tint = colors.inkTertiary,
        )
        DdIconButton(
            icon = DdIcons.Quote,
            onClick = onQuote,
            contentDescription = stringResource(R.string.assistant_quote),
            modifier = Modifier.testTag("message_${messageId}_quote"),
            size = 34.dp,
            iconSize = 16.dp,
            tint = colors.inkTertiary,
        )
        DdIconButton(
            icon = DdIcons.Share,
            onClick = onShare,
            contentDescription = stringResource(R.string.assistant_action_share),
            modifier = Modifier.testTag("message_${messageId}_share"),
            size = 34.dp,
            iconSize = 16.dp,
            tint = colors.inkTertiary,
        )
    }
}

@Composable
internal fun MessageActionMenu(
    messageId: Long,
    expanded: Boolean,
    anchor: Offset,
    showShare: Boolean,
    onDismissRequest: () -> Unit,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    onShare: () -> Unit,
) {
    Box(
        Modifier.offset {
            IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt())
        },
    ) {
        DdMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            offset = DpOffset(x = -(MessageMenuWidth / 2), y = 10.dp),
            menuWidth = MessageMenuWidth,
        ) {
            DdMenuItem(
                text = stringResource(R.string.assistant_action_copy),
                icon = DdIcons.Copy,
                testTag = "message_${messageId}_menu_copy",
                onClick = {
                    onDismissRequest()
                    onCopy()
                },
            )
            DdMenuItem(
                text = stringResource(R.string.assistant_quote),
                icon = DdIcons.Quote,
                testTag = "message_${messageId}_menu_quote",
                onClick = {
                    onDismissRequest()
                    onQuote()
                },
            )
            if (showShare) {
                DdMenuItem(
                    text = stringResource(R.string.assistant_action_share),
                    icon = DdIcons.Share,
                    testTag = "message_${messageId}_menu_share",
                    onClick = {
                        onDismissRequest()
                        onShare()
                    },
                )
            }
        }
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
