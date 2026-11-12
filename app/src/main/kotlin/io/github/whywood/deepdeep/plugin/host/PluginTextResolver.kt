package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.DEFAULT_LANGUAGE
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import java.util.Locale

internal class PluginTextResolver(
    private val language: () -> String = { DEFAULT_LANGUAGE },
) {
    fun resolve(source: LocalizedText, args: Array<out Any> = emptyArray<Any>()): String =
        format(source.resolve(language()).orEmpty(), args)

    private fun format(template: String, args: Array<out Any>): String =
        if (args.isEmpty()) template else String.format(Locale.ROOT, template, *args)
}
