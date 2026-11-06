package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
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
    val root = File(rootPath)
    FileBrowserScreen(
        root = root,
        rootLabel = chat.workspaces.firstOrNull { it.cwd == rootPath }?.name
            ?: root.name.ifBlank { rootPath },
        onOpenFile = onOpenFile,
        onBack = onBack,
    )
}
