package io.github.whywood.deepdeep.design

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontVariationAxis
import androidx.annotation.FontRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTextApi::class)
@Immutable
class DdCompositeFont(
    @FontRes internal val latinRes: Int,
    override val weight: FontWeight,
    internal val variable: Boolean,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Async,
    typefaceLoader = DdCompositeTypefaceLoader,
    variationSettings = FontVariation.Settings(),
) {
    override val style: FontStyle get() = FontStyle.Normal
}

internal const val SYSTEM_LATIN_RES = 0

internal object DdCompositeTypefaceLoader : AndroidFont.TypefaceLoader {
    private val cache = ConcurrentHashMap<Int, Typeface>()
    private val files = ConcurrentHashMap<Int, File>()
    private val systemLatin = File("/system/fonts/Roboto-Regular.ttf")
    private val lock = Any()

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        val composite = font as? DdCompositeFont ?: return null
        return typeface(context.applicationContext, composite)
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? {
        val composite = font as? DdCompositeFont ?: return null
        cache[cacheKey(composite)]?.let { return it }
        val application = context.applicationContext
        return withContext(Dispatchers.IO) { typeface(application, composite) }
    }

    fun preload(context: Context, fonts: List<DdCompositeFont>) {
        val application = context.applicationContext
        fonts.forEach { font -> runCatching { typeface(application, font) } }
    }

    private fun cacheKey(font: DdCompositeFont): Int = font.latinRes * 1000 + font.weight.weight

    private fun typeface(context: Context, font: DdCompositeFont): Typeface {
        val key = cacheKey(font)
        cache[key]?.let { return it }
        synchronized(lock) {
            cache[key]?.let { return it }
            val typeface = build(context, font)
            cache[key] = typeface
            return typeface
        }
    }

    private fun build(context: Context, font: DdCompositeFont): Typeface = runCatching {
        val file = if (font.latinRes == SYSTEM_LATIN_RES) systemLatin else fontFile(context, font.latinRes)
        val platformFont = PlatformFont.Builder(file)
            .setWeight(font.weight.weight)
            .apply {
                if (font.variable) setFontVariationSettings(variation(font.weight.weight))
            }
            .build()
        val typeface = compositeTypeface(PlatformFontFamily.Builder(platformFont).build())
        if (font.weight.weight >= 600 && font.latinRes == SYSTEM_LATIN_RES) {
            Typeface.create(typeface, Typeface.BOLD)
        } else {
            typeface
        }
    }.getOrElse {
        Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun compositeTypeface(latin: PlatformFontFamily): Typeface =
        Typeface.CustomFallbackBuilder(latin).setSystemFallback("sans-serif").build()

    private fun fontFile(context: Context, resId: Int): File {
        files[resId]?.let { return it }
        val dir = File(context.cacheDir, "fonts")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, context.resources.getResourceEntryName(resId) + ".ttf")
        val temp = File(dir, target.name + ".tmp")
        context.resources.openRawResource(resId).use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        files[resId] = target
        return target
    }

    private fun variation(weight: Int): Array<FontVariationAxis> =
        arrayOf(FontVariationAxis("wght", weight.toFloat()))
}
