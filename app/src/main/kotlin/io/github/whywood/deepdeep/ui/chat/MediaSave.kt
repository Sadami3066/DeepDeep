package io.github.whywood.deepdeep.ui.chat

import android.content.Context
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
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.saveFileToDownloads
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import java.io.File

@Composable
internal fun SaveToDownloadsSheet(
    name: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(title = name, onClose = onDismiss)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            DdListRow(
                title = stringResource(R.string.files_save_to_downloads),
                icon = DdIcons.Download,
                onClick = onSave,
            )
            DdListRow(
                title = stringResource(R.string.files_cancel),
                onClick = onDismiss,
            )
            Spacer(Modifier.height(28.dp))
        }
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
