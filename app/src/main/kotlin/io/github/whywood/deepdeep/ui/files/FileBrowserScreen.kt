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
import io.github.whywood.deepdeep.data.FilePreviewKind
import io.github.whywood.deepdeep.data.filePreviewKind
import io.github.whywood.deepdeep.data.formatAttachmentSize
import io.github.whywood.deepdeep.data.guessMime
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FILE_BROWSER_ROW_MIN_HEIGHT_DP = 52

internal object FileBrowserPaths {
    fun childPath(current: String, name: String): String {
        val parent = normalize(current)
        val child = name.trim('/')
        return when {
            child.isEmpty() -> parent
            parent.isEmpty() -> child
            else -> "$parent/$child"
        }
    }

    fun parentPath(current: String): String = normalize(current).substringBeforeLast('/', "")

    private fun normalize(path: String): String =
        path.split('/').filter { it.isNotEmpty() }.joinToString("/")
}

private sealed interface FileBrowserListing {
    data object Loading : FileBrowserListing
    data class Ready(val entries: List<FileBrowserEntry>) : FileBrowserListing
    data object Failed : FileBrowserListing
}

private data class FileBrowserEntry(
    val name: String,
    val file: File,
    val directory: Boolean,
    val size: Long,
)

@Composable
fun FileBrowserScreen(
    root: File,
    rootLabel: String,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var relativePath by rememberSaveable(root.absolutePath) { mutableStateOf("") }
    val directory = remember(root, relativePath) {
        if (relativePath.isEmpty()) root else File(root, relativePath)
    }
    val listing by produceState<FileBrowserListing>(
        initialValue = FileBrowserListing.Loading,
        root.absolutePath,
        relativePath,
    ) {
        value = withContext(Dispatchers.IO) { readFileBrowserDirectory(directory) }
    }
    val canGoUp = relativePath.isNotEmpty()
    val goUp = { relativePath = FileBrowserPaths.parentPath(relativePath) }
    val label = if (canGoUp) {
        relativePath
    } else {
        rootLabel.ifBlank { root.name.ifBlank { root.absolutePath } }
    }

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.files_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.files_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
        },
        modifier = modifier,
    ) {
        BackHandler(enabled = canGoUp, onBack = goUp)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            PathRow(
                label = label,
                canGoUp = canGoUp,
                onUp = goUp,
            )
            Spacer(Modifier.height(4.dp))
            DdHairline()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(4.dp))
                when (val current = listing) {
                    FileBrowserListing.Loading -> BrowserLoading()

                    FileBrowserListing.Failed -> BrowserMessage(
                        icon = DdIcons.Folder,
                        text = stringResource(R.string.files_unreadable),
                    )

                    is FileBrowserListing.Ready -> {
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
                                            relativePath = FileBrowserPaths.childPath(relativePath, entry.name)
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
            Modifier.fillMaxWidth(),
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
private fun FileRow(entry: FileBrowserEntry, onClick: () -> Unit) {
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
                .heightIn(min = FILE_BROWSER_ROW_MIN_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = if (entry.directory) DdIcons.Folder else fileEntryIcon(entry.name),
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

private fun fileEntryIcon(name: String): DdIcon = when (filePreviewKind(name, guessMime(name))) {
    FilePreviewKind.IMAGE -> DdIcons.Image
    FilePreviewKind.VIDEO -> DdIcons.Play
    FilePreviewKind.HTML -> DdIcons.Globe
    FilePreviewKind.CODE -> DdIcons.Terminal
    FilePreviewKind.MARKDOWN, FilePreviewKind.UNSUPPORTED -> DdIcons.File
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

private fun readFileBrowserDirectory(directory: File): FileBrowserListing {
    val children = runCatching { directory.listFiles() }.getOrNull() ?: return FileBrowserListing.Failed
    val visible = children.filter { !it.name.startsWith(".") }
    val directories = visible
        .filter { it.isDirectory }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { FileBrowserEntry(it.name, it, directory = true, size = 0L) }
    val files = visible
        .filter { it.isFile }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { FileBrowserEntry(it.name, it, directory = false, size = it.length()) }
    return FileBrowserListing.Ready(directories + files)
}
