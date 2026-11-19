package io.github.whywood.deepdeep.theme

import io.github.whywood.deepdeep.design.DdTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeChoiceTest {
    @Test
    fun parsesBuiltInChoices() {
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse("pi"))
        assertEquals(ThemeChoice.System, ThemeChoice.parse("system"))
        assertEquals(ThemeChoice.DeepDeep, ThemeChoice.parse("deepdeep"))
        assertEquals(ThemeChoice.Santorini, ThemeChoice.parse("santorini"))
        assertEquals(ThemeChoice.Oceanic, ThemeChoice.parse("oceanic"))
    }

    @Test
    fun parsesPackChoice() {
        val choice = ThemeChoice.parse("pack:com.example.plugin/aurora")
        assertEquals(ThemeChoice.Pack("com.example.plugin", "aurora"), choice)
        assertEquals("pack:com.example.plugin/aurora", choice.key)
    }

    @Test
    fun invalidValuesFallBackToPi() {
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse(null))
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse(""))
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse("unknown"))
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse("pack:missing"))
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse("pack:com.example.plugin/"))
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse("pack:com.example.plugin/a/b"))
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse("pack:Com.Example/Aurora"))
        assertEquals(ThemeChoice.Pi, ThemeChoice.parse("pack:plain/aurora"))
    }

    @Test
    fun packFactoryValidatesIds() {
        assertEquals(ThemeChoice.Pack("com.example.plugin", "aurora"), ThemeChoice.pack("com.example.plugin", "aurora"))
        assertNull(ThemeChoice.pack("com.example.plugin", "Aurora"))
        assertNull(ThemeChoice.pack("com.example.plugin", ""))
        assertNull(ThemeChoice.pack("plugin", "aurora"))
    }

    @Test
    fun keyRoundTrips() {
        ThemeChoice.builtIn.forEach { choice ->
            assertEquals(choice, ThemeChoice.fromKey(choice.key))
        }
        val pack = ThemeChoice.Pack("com.example.plugin", "aurora")
        assertEquals(pack, ThemeChoice.fromKey(pack.key))
    }

    @Test
    fun paletteCodecRoundTrips() {
        val light = DdTheme.lightPi()
        val dark = DdTheme.darkPi()
        assertEquals(light, ThemePaletteCodec.decode(ThemePaletteCodec.encode(light)))
        assertEquals(dark, ThemePaletteCodec.decode(ThemePaletteCodec.encode(dark)))
        assertNull(ThemePaletteCodec.decode(null))
        assertNull(ThemePaletteCodec.decode("nonsense"))
    }
}
