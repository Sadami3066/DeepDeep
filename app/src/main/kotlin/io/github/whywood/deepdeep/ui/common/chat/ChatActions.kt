package io.github.whywood.deepdeep.ui.common.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.ui.common.shareChooser

internal fun copyToClipboard(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("deepdeep", text))
    return true
}

internal fun sharePlainText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(shareChooser(context, intent, context.getString(R.string.assistant_attach_share)))
    }.onFailure {
        Toast.makeText(context, R.string.chat_message_share_failed, Toast.LENGTH_SHORT).show()
    }
}
