package io.github.whywood.deepdeep.ui.plugins

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.api.IconRef
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi
import io.github.whywood.deepdeep.plugin.manager.PluginSnapshot
import io.github.whywood.deepdeep.plugin.manager.PluginsUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PluginTextResolver(
    private val language: String,
) {
    fun resolve(text: LocalizedText): String = text.resolve(language) ?: text.resolve().orEmpty()
}

@Composable
internal fun rememberPluginTextResolver(): PluginTextResolver {
    val context = LocalContext.current
    val language = remember(context) { LanguagePreferences(context).read().tag }
    return remember(context, language) { PluginTextResolver(language) }
}

@Composable
internal fun PluginIcon(
    icon: IconRef?,
    assetsDir: File?,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (icon != null) {
        val bitmap = rememberPluginBitmap(assetsDir, icon.asset, size)
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
    DdIcon(icon = DdIcons.Puzzle, tint = tint, modifier = modifier, size = size)
}

@Composable
private fun rememberPluginBitmap(assetsDir: File?, path: String, size: Dp): ImageBitmap? {
    if (assetsDir == null) return null
    val density = LocalDensity.current
    val targetPx = with(density) { size.roundToPx() }.coerceAtLeast(1)
    val bucketPx = ((targetPx + 63) / 64) * 64
    val file = remember(assetsDir, path) { File(assetsDir, path).takeIf { it.isFile } }
    val cacheKey = remember(file, bucketPx) { file?.let { it.absolutePath + "|" + bucketPx } }
    val cached = remember(cacheKey) { cacheKey?.let { pluginBitmapCache.get(it) } }
    val bitmap by produceState<ImageBitmap?>(initialValue = cached, key1 = file, key2 = bucketPx) {
        if (file == null) {
            value = null
            return@produceState
        }
        val key = file.absolutePath + "|" + bucketPx
        val hit = pluginBitmapCache.get(key)
        if (hit != null) {
            value = hit
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodePluginBitmap(file, bucketPx) }.getOrNull()
        }
        if (decoded != null) pluginBitmapCache.put(key, decoded)
        value = decoded
    }
    return bitmap
}

private val pluginBitmapCache = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

private fun decodePluginBitmap(file: File, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= targetPx && bounds.outHeight / (sampleSize * 2) >= targetPx) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
}

internal fun pluginAssetsDir(pluginsRoot: File?, snapshot: PluginSnapshot): File? {
    val root = pluginsRoot ?: return null
    val version = snapshot.installedVersion ?: snapshot.version
    return File(File(root, snapshot.id), version).resolve("assets")
}

internal fun pluginPhaseLabelRes(phase: PluginPhaseUi): Int = when (phase) {
    PluginPhaseUi.INSTALLED -> R.string.plugins_phase_installed
    PluginPhaseUi.ACTIVE -> R.string.plugins_phase_active
    PluginPhaseUi.DEGRADED -> R.string.plugins_phase_degraded
    PluginPhaseUi.FAILED -> R.string.plugins_phase_failed
    PluginPhaseUi.DISABLED -> R.string.plugins_phase_disabled
    PluginPhaseUi.PENDING_RESTART -> R.string.plugins_phase_pending_restart
}

internal fun pluginCatalogReady(state: PluginsUiState): Boolean = !state.loading

internal fun pluginListInteractive(state: PluginsUiState): Boolean = !state.busy

internal fun pluginMatchesQuery(query: String, id: String, name: String, summary: String): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return id.contains(normalized, ignoreCase = true) ||
        name.contains(normalized, ignoreCase = true) ||
        summary.contains(normalized, ignoreCase = true)
}

internal fun filterPlugins(plugins: List<PluginSnapshot>, query: String): List<PluginSnapshot> =
    plugins.filter { plugin -> pluginMatchesQuery(query, plugin.id, plugin.name, plugin.summary) }

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
    DdGroupCard(modifier = modifier, content = content)
}
