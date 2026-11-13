package io.github.whywood.deepdeep.plugin.host.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.ShareRequest
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

data class PluginPageRequest(
    val pluginId: String,
    val pageId: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
)

internal object PluginUiBridge {
    private val pageRequests = MutableStateFlow<PluginPageRequest?>(null)
    private val sheetRequests = MutableStateFlow<PluginPageRequest?>(null)
    private val closeRequests = MutableStateFlow(false)

    val pendingPage: StateFlow<PluginPageRequest?> = pageRequests.asStateFlow()

    val pendingSheet: StateFlow<PluginPageRequest?> = sheetRequests.asStateFlow()

    val pendingClosePage: StateFlow<Boolean> = closeRequests.asStateFlow()

    fun requestPage(
        pluginId: String,
        pageId: String,
        arguments: JsonObject = JsonObject(emptyMap()),
    ) {
        require(pluginId.isNotBlank()) { "plugin page request requires a plugin id" }
        require(pageId.isNotBlank()) { "plugin page request requires a page id" }
        pageRequests.value = PluginPageRequest(pluginId, pageId, arguments)
    }

    fun consumePage(request: PluginPageRequest) {
        pageRequests.compareAndSet(request, null)
    }

    fun requestSheet(
        pluginId: String,
        pageId: String,
        arguments: JsonObject = JsonObject(emptyMap()),
    ) {
        require(pluginId.isNotBlank()) { "plugin page sheet request requires a plugin id" }
        require(pageId.isNotBlank()) { "plugin page sheet request requires a page id" }
        sheetRequests.value = PluginPageRequest(pluginId, pageId, arguments)
    }

    fun consumeSheet(request: PluginPageRequest) {
        sheetRequests.compareAndSet(request, null)
    }

    fun requestClosePage() {
        closeRequests.value = true
    }

    fun consumeClosePage() {
        closeRequests.compareAndSet(true, false)
    }

    fun openPage(
        context: Context,
        pluginId: String,
        pageId: String,
        arguments: JsonObject = JsonObject(emptyMap()),
    ) {
        requestPage(pluginId, pageId, arguments)
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
            val uri = shareUri(context, filePath)
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

    fun shareFiles(context: Context, paths: List<String>, mimeType: String?) {
        val files = paths.filter { it.isNotBlank() }
        require(files.isNotEmpty()) { "shareFiles requires at least one file" }
        val uris = files.map { path -> shareUri(context, path) }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType?.takeIf { it.isNotBlank() } ?: "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.files_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun shareUri(context: Context, path: String): Uri {
        val file = File(path)
        require(file.isFile) { "share file does not exist: $path" }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
