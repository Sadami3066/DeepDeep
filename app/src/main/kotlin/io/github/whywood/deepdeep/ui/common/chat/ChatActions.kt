package io.github.whywood.deepdeep.ui.common.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

internal fun copyToClipboard(context: Context, text: String): Boolean {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("deepdeep", text))
    return true
}
