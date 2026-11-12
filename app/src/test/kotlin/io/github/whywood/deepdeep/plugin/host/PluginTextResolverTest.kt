package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.host.external.HostLocalizedText
import io.github.whywood.deepdeep.plugin.host.external.hostLocalizedText
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginTextResolverTest {
    private val englishResolver = PluginTextResolver({ "en" })

    @Test
    fun resolvesRequestedLanguage() {
        val text = hostLocalizedText(mapOf("default" to "default text", "zh" to "中文", "en" to "english"))
        assertEquals("english", englishResolver.resolve(text))
        assertEquals("中文", PluginTextResolver({ "zh" }).resolve(text))
    }

    @Test
    fun fallsBackToDefaultLanguage() {
        val text = hostLocalizedText(mapOf("default" to "default text", "zh" to "中文"))
        assertEquals("default text", PluginTextResolver({ "fr" }).resolve(text))
        assertEquals("default text", PluginTextResolver({ "" }).resolve(text))
        assertEquals("default text", PluginTextResolver().resolve(text))
    }

    @Test
    fun formatsArguments() {
        val text = hostLocalizedText("hello %s, %d")
        assertEquals("hello world, 3", englishResolver.resolve(text, arrayOf<Any>("world", 3)))
    }

    @Test
    fun emptyValuesResolveToEmptyString() {
        assertEquals("", englishResolver.resolve(HostLocalizedText(emptyMap())))
    }

    @Test
    fun formattingWithoutArgumentsKeepsPercentSigns() {
        val text = hostLocalizedText("100% ready")
        assertEquals("100% ready", englishResolver.resolve(text))
    }

    @Test
    fun formatsStringAndNumberArguments() {
        val text = hostLocalizedText("%1\$s: %2\$d")
        assertEquals("a: 1", englishResolver.resolve(text, arrayOf<Any>("a", 1)))
    }
}
