package io.github.whywood.deepdeep.plugin.host.extensions.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import io.github.whywood.deepdeep.plugin.api.ClipboardService

internal class ClipboardPluginService(private val context: Context) : ClipboardService {
    private val manager: ClipboardManager?
        get() = runCatching {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        }.getOrNull()

    override fun text(): String? = runCatching {
        val clip = manager?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        clip.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    override fun setText(text: String) {
        runCatching {
            manager?.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        }
    }

    override fun uri(): String? = runCatching {
        val clip = manager?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0) ?: return null
        item.uri?.toString()?.takeIf { it.isNotEmpty() }
            ?: item.coerceToText(context)?.toString()?.takeIf { it.startsWith("content://") || it.startsWith("file://") }
    }.getOrNull()

    override fun setUri(uri: String) {
        runCatching {
            manager?.setPrimaryClip(ClipData.newRawUri(CLIP_LABEL, Uri.parse(uri)))
        }
    }

    override fun has(): Boolean = runCatching {
        val clip = manager?.primaryClip ?: return false
        clip.itemCount > 0
    }.getOrDefault(false)

    override fun clear() {
        runCatching {
            val clipboard = manager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, ""))
            }
        }
    }

    private companion object {
        const val CLIP_LABEL = "DeepDeep"
    }
}
