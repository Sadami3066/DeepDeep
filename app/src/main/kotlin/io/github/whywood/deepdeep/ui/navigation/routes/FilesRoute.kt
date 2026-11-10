package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.whywood.deepdeep.data.appHomeDirectory
import io.github.whywood.deepdeep.ui.files.FileBrowserScreen

@Composable
internal fun FilesRoute(
    rootPath: String,
    onOpenFile: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val privateRoot = remember(context) { appHomeDirectory(context) }
    FileBrowserScreen(
        initialPath = rootPath,
        privateRoot = privateRoot,
        onOpenFile = onOpenFile,
        onBack = onBack,
    )
}
