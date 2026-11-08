package io.github.whywood.deepdeep.ui.miniapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.whywood.deepdeep.runtime.MiniappManifest

internal enum class MiniappShortcutResult {
    Requested,
    Unsupported,
}

internal object MiniappShortcut {
    fun shortcutId(miniappId: String): String = "miniapp_$miniappId"

    fun deepLink(miniappId: String): String = "deepdeep://miniapp/$miniappId"

    fun isPinned(context: Context, manifest: MiniappManifest): Boolean =
        ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_PINNED,
        ).any { it.id == shortcutId(manifest.id) }

    fun request(context: Context, manifest: MiniappManifest): MiniappShortcutResult {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return MiniappShortcutResult.Unsupported
        }
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(manifest.id))
            .setShortLabel(manifest.name)
            .setLongLabel(manifest.name)
            .setIcon(IconCompat.createWithBitmap(icon(manifest.icon)))
            .setIntent(intent(context, manifest.id))
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        return MiniappShortcutResult.Requested
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun intent(context: Context, miniappId: String): Intent = Intent(Intent.ACTION_VIEW)
        .setData(Uri.parse(deepLink(miniappId)))
        .setPackage(context.packageName)

    private fun icon(emoji: String): Bitmap {
        val size = ICON_SIZE
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ICON_BACKGROUND
            style = Paint.Style.FILL
        }
        val radius = size * 0.24f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, background)
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ICON_INK
            textSize = size * 0.52f
            textAlign = Paint.Align.CENTER
        }
        val metrics = glyph.fontMetrics
        val baseline = size / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji.ifBlank { ICON_FALLBACK }, size / 2f, baseline, glyph)
        return bitmap
    }

    private const val ICON_SIZE = 192
    private const val ICON_BACKGROUND = 0xFFF2F5F9.toInt()
    private const val ICON_INK = 0xFF0B1220.toInt()
    private const val ICON_FALLBACK = "✦"
}
