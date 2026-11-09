package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.whywood.deepdeep.data.appHomeDirectory
import io.github.whywood.deepdeep.ui.files.FileBrowserScreen
import io.github.whywood.deepdeep.ui.navigation.ChatRouteData
import java.io.File

@Composable
internal fun FilesRoute(
    rootPath: String,
    chat: ChatRouteData,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val privateRoot = remember(context) { appHomeDirectory(context) }
    val root = File(rootPath)
    FileBrowserScreen(
        root = root,
        rootLabel = chat.workspaces.firstOrNull { it.cwd == rootPath }?.name
            ?: root.name.ifBlank { rootPath },
        privateRoot = privateRoot,
        onOpenFile = onOpenFile,
        onBack = onBack,
    )
}
