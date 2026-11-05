package io.github.whywood.deepdeep.plugin.host

import io.github.whywood.deepdeep.plugin.api.LocalizedText
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginTextResolverTest {
    private val englishResolver = PluginTextResolver({ id -> "resource:$id" }, { "en" })

    @Test
    fun resolvesLiteralByLanguageTag() {
        val text = LocalizedText.Literal(mapOf("default" to "default text", "en" to "english text", "zh" to "中文"))
        assertEquals("english text", englishResolver.resolve(text))
        assertEquals("中文", PluginTextResolver({ "" }, { "zh" }).resolve(text))
    }

    @Test
    fun fallsBackToDefaultLanguage() {
        val text = LocalizedText.Literal(mapOf("default" to "default text", "en" to "english text"))
        assertEquals("default text", PluginTextResolver({ "" }, { "fr" }).resolve(text))
        assertEquals("default text", PluginTextResolver({ "" }, { "" }).resolve(text))
        assertEquals("default text", PluginTextResolver({ "" }).resolve(text))
    }

    @Test
    fun missingTextResolvesToEmptyString() {
        assertEquals("", englishResolver.resolve(LocalizedText.Literal(emptyMap())))
    }

    @Test
    fun resolvesResourceThroughInjectedResolver() {
        assertEquals("resource:42", englishResolver.resolve(LocalizedText.Resource(42)))
    }

    @Test
    fun formatsArgumentsWhenPresent() {
        val text = LocalizedText.Literal("hello %s #%d")

        assertEquals("hello world #7", englishResolver.resolve(text, arrayOf<Any>("world", 7)))
        assertEquals("100% done", englishResolver.resolve(LocalizedText.Literal("100%% %s"), arrayOf<Any>("done")))
    }

    @Test
    fun leavesTemplateVerbatimWithoutArguments() {
        assertEquals("100%", englishResolver.resolve(LocalizedText.Literal("100%")))
        assertEquals("a %s b", englishResolver.resolve(LocalizedText.Literal("a %s b")))
    }

    @Test
    fun formatsResourceTextArguments() {
        val resolver = PluginTextResolver({ id -> "resource %s" }, { "en" })
        assertEquals("resource value", resolver.resolve(LocalizedText.Resource(1), arrayOf<Any>("value")))
    }
}
