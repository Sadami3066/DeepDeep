package io.github.whywood.deepdeep.ui.files

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.FilePreviewKind
import io.github.whywood.deepdeep.data.filePreviewKind
import io.github.whywood.deepdeep.data.formatAttachmentSize
import io.github.whywood.deepdeep.data.HostFiles
import io.github.whywood.deepdeep.data.guessMime
import io.github.whywood.deepdeep.data.hasSharedStorageAccess
import io.github.whywood.deepdeep.data.sharedStorageDirectory
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.LocalHostFiles
import java.io.File
import java.util.Locale
import androidx.core.net.toUri
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

private enum class FileBrowserRoot {
    PRIVATE,
    SHARED,
    SYSTEM,
}

@Composable
fun FileBrowserScreen(
    initialPath: String,
    privateRoot: File,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val hostFiles = LocalHostFiles.current
    val sharedRoot = remember { sharedStorageDirectory() }
    val systemRoot = remember { File(File.separator) }
    val initialRoot = remember(initialPath, privateRoot, sharedRoot) {
        when {
            initialPath == systemRoot.absolutePath -> FileBrowserRoot.SYSTEM
            initialPath == sharedRoot.absolutePath ||
                initialPath.startsWith(sharedRoot.absolutePath + File.separator) -> FileBrowserRoot.SHARED
            else -> FileBrowserRoot.PRIVATE
        }
    }
    val initialRelative = remember(initialPath, initialRoot, privateRoot, sharedRoot) {
        val base = when (initialRoot) {
            FileBrowserRoot.PRIVATE -> privateRoot
            FileBrowserRoot.SHARED -> sharedRoot
            FileBrowserRoot.SYSTEM -> systemRoot
        }
        initialPath
            .takeIf { it.startsWith(base.absolutePath + File.separator) }
            ?.removePrefix(base.absolutePath + File.separator)
            .orEmpty()
    }
    var browsingRootName by rememberSaveable { mutableStateOf(initialRoot.name) }
    val browsingRoot = remember(browsingRootName) { FileBrowserRoot.valueOf(browsingRootName) }
    var pendingShared by remember { mutableStateOf(false) }
    var sharedStorageAccess by remember { mutableStateOf(hasSharedStorageAccess(context)) }
    val accessHint = stringResource(R.string.settings_linux_shared_storage_grant)
    val permissionHint = stringResource(R.string.settings_linux_shared_storage_permission)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        sharedStorageAccess = granted || hasSharedStorageAccess(context)
        if (granted) {
            pendingShared = false
            browsingRootName = FileBrowserRoot.SHARED.name
        } else {
            Toast.makeText(context, permissionHint, Toast.LENGTH_SHORT).show()
        }
    }
    LifecycleResumeEffect(Unit) {
        sharedStorageAccess = hasSharedStorageAccess(context)
        if (pendingShared && sharedStorageAccess) {
            pendingShared = false
            browsingRootName = FileBrowserRoot.SHARED.name
        }
        onPauseOrDispose {}
    }
    val activeRoot = when (browsingRoot) {
        FileBrowserRoot.PRIVATE -> privateRoot
        FileBrowserRoot.SHARED -> sharedRoot
        FileBrowserRoot.SYSTEM -> systemRoot
    }
    val initialRootKey = remember(initialRoot) { initialRoot.name }
    var relativePath by rememberSaveable(browsingRootName) {
        mutableStateOf(if (browsingRootName == initialRootKey) initialRelative else "")
    }
    val directory = remember(activeRoot, relativePath) {
        if (relativePath.isEmpty()) activeRoot else File(activeRoot, relativePath)
    }
    var listing by remember(directory, hostFiles) {
        mutableStateOf<FileBrowserListing>(FileBrowserListing.Loading)
    }
    LaunchedEffect(directory, hostFiles) {
        listing = withContext(Dispatchers.IO) { readFileBrowserDirectory(directory, hostFiles) }
    }
    val canGoUp = relativePath.isNotEmpty()
    val goUp = { relativePath = FileBrowserPaths.parentPath(relativePath) }
    val selectRoot: (FileBrowserRoot) -> Unit = { target ->
        if (target == FileBrowserRoot.SHARED && !sharedStorageAccess) {
            pendingShared = true
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    "package:${context.packageName}".toUri(),
                )
                if (runCatching { context.startActivity(intent) }.isFailure) {
                    Toast.makeText(context, accessHint, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            browsingRootName = target.name
        }
    }
    val label = when {
        canGoUp -> relativePath
        browsingRoot == FileBrowserRoot.SHARED -> stringResource(R.string.files_root_shared)
        browsingRoot == FileBrowserRoot.SYSTEM -> stringResource(R.string.files_root_system)
        else -> stringResource(R.string.files_root_private)
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
            Spacer(Modifier.width(DdSpacing.Xs))
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
                .padding(horizontal = DdSpacing.Xxl),
        ) {
            DdSegmentedControl(
                options = listOf(
                    FileBrowserRoot.PRIVATE,
                    FileBrowserRoot.SHARED,
                    FileBrowserRoot.SYSTEM,
                ),
                selected = browsingRoot,
                onSelect = selectRoot,
                modifier = Modifier.fillMaxWidth().padding(top = DdSpacing.Xs),
            ) { root ->
                when (root) {
                    FileBrowserRoot.PRIVATE -> stringResource(R.string.files_root_private)
                    FileBrowserRoot.SHARED -> stringResource(R.string.files_root_shared)
                    FileBrowserRoot.SYSTEM -> stringResource(R.string.files_root_system)
                }
            }
            Spacer(Modifier.height(DdSpacing.Sm))
            PathRow(
                label = label,
                canGoUp = canGoUp,
                onUp = goUp,
            )
            Spacer(Modifier.height(DdSpacing.Xs))
            DdHairline()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(DdSpacing.Xs))
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
                Spacer(Modifier.height(DdSpacing.Xxl))
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
            Spacer(Modifier.width(DdSpacing.Sm))
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
                Spacer(Modifier.width(DdSpacing.Sm))
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
        Spacer(Modifier.height(DdSpacing.Md))
        Text(
            text = text,
            style = DdType.bodySmall,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun readFileBrowserDirectory(directory: File, hostFiles: HostFiles): FileBrowserListing {
    val children = hostFiles.list(directory) ?: return FileBrowserListing.Failed
    val visible = children.filter { !it.name.startsWith(".") }
    val directories = visible
        .filter { hostFiles.isDirectory(it) }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { FileBrowserEntry(it.name, it, directory = true, size = 0L) }
    val files = visible
        .filter { hostFiles.isFile(it) }
        .sortedBy { it.name.lowercase(Locale.US) }
        .map { FileBrowserEntry(it.name, it, directory = false, size = hostFiles.length(it)) }
    return FileBrowserListing.Ready(directories + files)
}
