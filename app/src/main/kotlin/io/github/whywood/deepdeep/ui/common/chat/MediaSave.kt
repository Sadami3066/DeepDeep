package io.github.whywood.deepdeep.ui.common.chat

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.guessMime
import io.github.whywood.deepdeep.data.saveFileToDownloads
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.ui.common.shareChooser
import java.io.File



@Composable
internal fun SaveToDownloadsSheet(
    name: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: (() -> Unit)? = null,
    onScanQr: (() -> Unit)? = null,
) {
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(title = name, onClose = onDismiss)
        Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Xxl)) {
            Spacer(Modifier.height(DdSpacing.Sm))
            DdListRow(
                title = stringResource(R.string.files_save_to_downloads),
                icon = DdIcons.Download,
                onClick = onSave,
            )
            if (onShare != null) {
                DdListRow(
                    title = stringResource(R.string.files_share),
                    icon = DdIcons.Share,
                    onClick = onShare,
                )
            }
            if (onScanQr != null) {
                DdListRow(
                    title = stringResource(R.string.files_qr_action),
                    icon = DdIcons.Scan,
                    onClick = onScanQr,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

internal fun shareMediaFile(context: Context, file: File, mime: String?) {
    val shared = runCatching {
        val uri = FileProvider.getUriForFile(context, "${'$'}{context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime?.takeIf { it.isNotBlank() } ?: guessMime(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(shareChooser(context, intent, context.getString(R.string.assistant_attach_share)))
    }.isSuccess
    if (!shared) {
        Toast.makeText(context, R.string.files_share_failed, Toast.LENGTH_SHORT).show()
    }
}

internal fun shareMediaUrl(context: Context, url: String) {
    val shared = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(shareChooser(context, intent, context.getString(R.string.assistant_attach_share)))
    }.isSuccess
    if (!shared) {
        Toast.makeText(context, R.string.files_share_failed, Toast.LENGTH_SHORT).show()
    }
}

internal fun saveMediaToDownloads(context: Context, file: File, mime: String?) {
    val saved = saveFileToDownloads(context, file, mime)
    Toast.makeText(
        context,
        if (saved) R.string.files_saved else R.string.files_save_failed,
        Toast.LENGTH_SHORT,
    ).show()
}
