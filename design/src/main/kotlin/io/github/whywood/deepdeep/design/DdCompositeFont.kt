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
import androidx.compose.ui.text.font.FontFamily
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
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Async,
    typefaceLoader = DdCompositeTypefaceLoader,
    variationSettings = FontVariation.Settings(),
) {
    override val style: FontStyle get() = FontStyle.Normal
}

internal object DdCompositeTypefaceLoader : AndroidFont.TypefaceLoader {
    private val CJK_RES = R.font.noto_sans_sc_subset
    private val CACHE_WEIGHTS = listOf(400, 500, 600)
    private val cache = ConcurrentHashMap<Int, Typeface>()
    private val files = ConcurrentHashMap<Int, File>()
    private val lock = Any()
    private var cjkFamily: PlatformFontFamily? = null

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        val composite = font as? DdCompositeFont ?: return null
        return typeface(context.applicationContext, composite.latinRes, composite.weight.weight)
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? {
        val composite = font as? DdCompositeFont ?: return null
        val weight = composite.weight.weight
        cache[composite.latinRes * 1000 + weight]?.let { return it }
        val application = context.applicationContext
        return withContext(Dispatchers.IO) { typeface(application, composite.latinRes, weight) }
    }

    fun preload(context: Context, latinRes: List<Int>) {
        val application = context.applicationContext
        latinRes.forEach { res ->
            CACHE_WEIGHTS.forEach { weight -> runCatching { typeface(application, res, weight) } }
        }
    }

    private fun typeface(context: Context, latinRes: Int, weight: Int): Typeface {
        val key = latinRes * 1000 + weight
        cache[key]?.let { return it }
        synchronized(lock) {
            cache[key]?.let { return it }
            val typeface = build(context, latinRes, weight)
            cache[key] = typeface
            return typeface
        }
    }

    private fun build(context: Context, latinRes: Int, weight: Int): Typeface = runCatching {
        val latin = PlatformFontFamily.Builder(
            PlatformFont.Builder(fontFile(context, latinRes))
                .setWeight(weight)
                .setFontVariationSettings(variation(weight))
                .build(),
        ).build()
        Typeface.CustomFallbackBuilder(latin)
            .addCustomFallback(cjkFontFamily(context))
            .setSystemFallback("sans-serif")
            .build()
    }.getOrElse {
        Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun cjkFontFamily(context: Context): PlatformFontFamily {
        cjkFamily?.let { return it }
        val file = fontFile(context, CJK_RES)
        val builder = PlatformFontFamily.Builder(
            PlatformFont.Builder(file)
                .setWeight(400)
                .setFontVariationSettings(variation(400))
                .build(),
        )
        CACHE_WEIGHTS.drop(1).forEach { weight ->
            builder.addFont(
                PlatformFont.Builder(file)
                    .setWeight(weight)
                    .setFontVariationSettings(variation(weight))
                    .build(),
            )
        }
        val family = builder.build()
        cjkFamily = family
        return family
    }

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
