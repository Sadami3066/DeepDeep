package io.github.whywood.deepdeep.plugin.host.services

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.ShareRequest
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PluginPageRequest(
    val pluginId: String,
    val pageId: String,
)

internal object PluginUiBridge {
    private val pageRequests = MutableStateFlow<PluginPageRequest?>(null)

    val pendingPage: StateFlow<PluginPageRequest?> = pageRequests.asStateFlow()

    fun requestPage(pluginId: String, pageId: String) {
        require(pluginId.isNotBlank()) { "plugin page request requires a plugin id" }
        require(pageId.isNotBlank()) { "plugin page request requires a page id" }
        pageRequests.value = PluginPageRequest(pluginId, pageId)
    }

    fun consumePage(request: PluginPageRequest) {
        pageRequests.compareAndSet(request, null)
    }

    fun openPage(context: Context, pluginId: String, pageId: String) {
        requestPage(pluginId, pageId)
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun share(context: Context, request: ShareRequest) {
        val send = Intent(Intent.ACTION_SEND)
        val filePath = request.filePath
        if (filePath.isNullOrBlank()) {
            val text = request.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("share requires text or a file path")
            send.type = request.mimeType?.takeIf { it.isNotBlank() } ?: "text/plain"
            send.putExtra(Intent.EXTRA_TEXT, text)
        } else {
            val file = File(filePath)
            require(file.isFile) { "share file does not exist: $filePath" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            send.type = request.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            send.putExtra(Intent.EXTRA_STREAM, uri)
            request.text?.takeIf { it.isNotBlank() }?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.files_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
