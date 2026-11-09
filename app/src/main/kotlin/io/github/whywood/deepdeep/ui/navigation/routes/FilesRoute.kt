package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.ui.files.FileBrowserScreen
import io.github.whywood.deepdeep.ui.files.sharedStorageRoot
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
    var sharedRoot by remember { mutableStateOf(sharedStorageRoot()) }
    LifecycleResumeEffect(Unit) {
        sharedRoot = sharedStorageRoot()
        onPauseOrDispose {}
    }
    FileBrowserScreen(
        root = root,
        rootLabel = chat.workspaces.firstOrNull { it.cwd == rootPath }?.name
            ?: root.name.ifBlank { rootPath },
        sharedRoot = sharedRoot,
        onOpenFile = onOpenFile,
        onBack = onBack,
    )
}
