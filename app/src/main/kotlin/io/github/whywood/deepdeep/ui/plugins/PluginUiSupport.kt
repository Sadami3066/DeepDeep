package io.github.whywood.deepdeep.ui.plugins

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PluginTextResolver(
    private val context: Context,
    private val language: String,
) {
    fun resolve(text: LocalizedText): String = when (text) {
        is LocalizedText.Resource -> context.getString(text.id)
        is LocalizedText.Literal -> text.resolve(language) ?: text.resolve().orEmpty()
    }
}

@Composable
internal fun rememberPluginTextResolver(): PluginTextResolver {
    val context = LocalContext.current
    val language = remember(context) { LanguagePreferences(context).read().tag }
    return remember(context, language) { PluginTextResolver(context, language) }
}

@Composable
internal fun PluginIcon(
    icon: IconRef?,
    assetsDir: File?,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (icon is IconRef.Asset) {
        val bitmap = rememberPluginBitmap(assetsDir, icon.path)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = modifier.size(size),
                contentScale = ContentScale.Fit,
            )
            return
        }
    }
    val resource = (icon as? IconRef.Resource)?.id
    val resolved = if (resource != null) DdIcon(resource) else DdIcons.Puzzle
    DdIcon(icon = resolved, tint = tint, modifier = modifier, size = size)
}

@Composable
private fun rememberPluginBitmap(assetsDir: File?, path: String): ImageBitmap? {
    if (assetsDir == null) return null
    val file = remember(assetsDir, path) { File(assetsDir, path).takeIf { it.isFile } }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    return bitmap
}

internal fun pluginAssetsDir(pluginsRoot: File?, snapshot: PluginSnapshot): File? {
    val root = pluginsRoot ?: return null
    val version = snapshot.installedVersion ?: snapshot.version
    return File(File(root, snapshot.id), version).resolve("assets")
}

internal fun pluginPhaseLabelRes(phase: PluginPhaseUi): Int = when (phase) {
    PluginPhaseUi.IMPORTED -> R.string.plugins_phase_imported
    PluginPhaseUi.INSTALLED -> R.string.plugins_phase_installed
    PluginPhaseUi.ACTIVE -> R.string.plugins_phase_active
    PluginPhaseUi.DEGRADED -> R.string.plugins_phase_degraded
    PluginPhaseUi.FAILED -> R.string.plugins_phase_failed
    PluginPhaseUi.DISABLED -> R.string.plugins_phase_disabled
    PluginPhaseUi.PENDING_RESTART -> R.string.plugins_phase_pending_restart
}

internal fun pluginTabSource(index: Int): PluginSource =
    if (index == 0) PluginSource.EXTERNAL else PluginSource.BUILTIN

internal data class PluginListItem(val snapshot: PluginSnapshot)

internal fun pluginItems(plugins: List<PluginSnapshot>, source: PluginSource): List<PluginListItem> =
    plugins.filter { it.source == source }.map(::PluginListItem)

internal fun pluginListInteractive(state: PluginsUiState): Boolean = !state.busy && state.interaction == null

internal fun pluginCatalogReady(state: PluginsUiState): Boolean =
    !state.loading && (state.plugins.isNotEmpty() || state.error != null)

internal fun pluginDetailMissing(state: PluginsUiState, pluginId: String): Boolean =
    pluginCatalogReady(state) && state.snapshot(pluginId) == null

internal fun pluginShowsReinstall(snapshot: PluginSnapshot): Boolean =
    snapshot.installable && snapshot.installed

internal fun pluginShowsDelete(snapshot: PluginSnapshot): Boolean =
    snapshot.installable && snapshot.source == PluginSource.EXTERNAL

internal fun pluginMatchesQuery(query: String, id: String, name: String, summary: String): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return id.contains(normalized, ignoreCase = true) ||
        name.contains(normalized, ignoreCase = true) ||
        summary.contains(normalized, ignoreCase = true)
}

internal fun filterPlugins(items: List<PluginListItem>, query: String): List<PluginListItem> =
    items.filter { item ->
        pluginMatchesQuery(query, item.snapshot.id, item.snapshot.name, item.snapshot.summary)
    }

internal fun formatPluginSize(context: Context, bytes: Long): String =
    if (bytes >= MEBIBYTE) {
        context.getString(R.string.plugins_size_mb, bytes / MEBIBYTE.toDouble())
    } else {
        context.getString(R.string.plugins_size_kb, bytes / KIBIBYTE.toDouble())
    }

private const val KIBIBYTE = 1024L

private const val MEBIBYTE = 1024L * 1024L

@Composable
internal fun PluginGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    CompositionLocalProvider(LocalDdRowHorizontalPadding provides 16.dp) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surface)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}
