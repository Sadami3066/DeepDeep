package io.github.whywood.deepdeep.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.formatAttachmentSize
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FILE_ROW_MIN_HEIGHT_DP = 52

private sealed interface DirectoryListing {
    data object Loading : DirectoryListing
    data class Ready(val entries: List<FileEntry>) : DirectoryListing
    data object Failed : DirectoryListing
}

private data class FileEntry(
    val name: String,
    val file: File,
    val directory: Boolean,
    val size: Long,
)

@Composable
fun FileBrowserSheet(
    root: File,
    rootLabel: String,
    onOpenFile: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var relativePath by rememberSaveable(root.absolutePath) { mutableStateOf("") }
    val directory = remember(root, relativePath) {
        if (relativePath.isEmpty()) root else File(root, relativePath)
    }
    val listing by produceState<DirectoryListing>(
        initialValue = DirectoryListing.Loading,
        root.absolutePath,
        relativePath,
    ) {
        value = withContext(Dispatchers.IO) { readDirectory(directory) }
    }
    val canGoUp = relativePath.isNotEmpty()
    val goUp = {
        relativePath = relativePath.substringBeforeLast('/', "")
    }
    DdSheet(onDismiss = onDismiss) {
        BackHandler(enabled = canGoUp, onBack = goUp)
        DdSheetHeader(
            title = stringResource(R.string.files_title),
            onClose = onDismiss,
        )
        PathRow(
            label = if (canGoUp) relativePath else rootLabel.ifBlank { root.name.ifBlank { root.absolutePath } },
            canGoUp = canGoUp,
            onUp = goUp,
        )
        DdHairline()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            when (val current = listing) {
                DirectoryListing.Loading -> BrowserLoading()

                DirectoryListing.Failed -> BrowserMessage(
                    icon = DdIcons.Folder,
                    text = stringResource(R.string.files_unreadable),
                )

                is DirectoryListing.Ready -> {
                    if (current.entries.isEmpty()) {
                        BrowserMessage(
                            icon = DdIcons.Folder,
                            text = stringResource(R.string.files_empty),
                        )
                    } else {
                        current.entries.forEachIndexed { index, entry ->
                            if (index > 0) DdHairline()
                            FileRow(
                                entry = entry,
                                onClick = {
                                    if (entry.directory) {
                                        relativePath = childPath(relativePath, entry.name)
                                    } else {
                                        onOpenFile(entry.file.absolutePath)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PathRow(
    label: String,
    canGoUp: Boolean,
    onUp: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onUp,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(if (canGoUp) Modifier.testTag("files_up") else Modifier),
        shape = RectangleShape,
        enabled = canGoUp,
        contentDescription = if (canGoUp) stringResource(R.string.files_up) else label,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = if (canGoUp) DdIcons.ArrowLeft else DdIcons.Folder,
                tint = if (canGoUp) colors.inkSecondary else colors.inkTertiary,
                size = 14.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = if (canGoUp) colors.inkSecondary else colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canGoUp) {
                Text(
                    text = stringResource(R.string.files_up),
                    modifier = Modifier.padding(start = 10.dp),
                    style = DdType.micro,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onClick: () -> Unit) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("file_row_${entry.name}"),
        shape = RectangleShape,
        contentDescription = entry.name,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = FILE_ROW_MIN_HEIGHT_DP.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = if (entry.directory) DdIcons.Folder else DdIcons.File,
                tint = if (entry.directory) colors.inkSecondary else colors.inkTertiary,
                size = 16.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = entry.name,
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.directory) {
                Spacer(Modifier.width(8.dp))
                DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 14.dp)
            } else {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = formatAttachmentSize(entry.size),
                    style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BrowserLoading() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        DdSpinner(size = 18.dp)
    }
}

@Composable
private fun BrowserMessage(icon: DdIcon, text: String) {
    val colors = deepDeepColors()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = icon, tint = colors.inkTertiary, size = 22.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = DdType.bodySmall,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun readDirectory(directory: File): DirectoryListing {
    val children = runCatching { directory.listFiles() }.getOrNull() ?: return DirectoryListing.Failed
    val visible = children.filter { !it.name.startsWith(".") }
    val directories = visible
        .filter { it.isDirectory }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { FileEntry(it.name, it, directory = true, size = 0L) }
    val files = visible
        .filter { it.isFile }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { FileEntry(it.name, it, directory = false, size = it.length()) }
    return DirectoryListing.Ready(directories + files)
}

private fun childPath(parent: String, name: String): String =
    if (parent.isEmpty()) name else "$parent/$name"
