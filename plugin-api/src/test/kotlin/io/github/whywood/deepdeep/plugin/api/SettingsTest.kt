package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val title = LocalizedText.Literal("Setting")

    @Test
    fun toggleNormalizesStrictBooleans() {
        val spec = SettingSpec.Toggle("enabled", title, defaultValue = true)
        assertEquals("true", spec.default)
        assertEquals("true", spec.normalize("true"))
        assertEquals("false", spec.normalize("false"))
        assertNull(spec.normalize("1"))
        assertNull(spec.normalize("yes"))
    }

    @Test
    fun choiceNormalizesDeclaredOptions() {
        val spec = SettingSpec.Choice(
            key = "mode",
            title = title,
            options = listOf(SettingOption("fast", title), SettingOption("slow", title)),
            default = "fast",
        )
        assertEquals("slow", spec.normalize("slow"))
        assertNull(spec.normalize("medium"))
    }

    @Test
    fun numberClampsToRange() {
        val spec = SettingSpec.Number("rounds", title, min = 1, max = 5, defaultValue = 3)
        assertEquals("3", spec.default)
        assertEquals("1", spec.normalize("0"))
        assertEquals("5", spec.normalize("9"))
        assertEquals("4", spec.normalize("4"))
        assertNull(spec.normalize("many"))
    }

    @Test
    fun textTrimsUnlessSecret() {
        val plain = SettingSpec.Text("name", title)
        assertEquals("hi", plain.normalize("  hi  "))

        val secret = SettingSpec.Text("token", title, default = "", secret = true)
        assertEquals("  hi  ", secret.normalize("  hi  "))
    }

    @Test
    fun groupIsContainerWithoutValue() {
        val group = SettingSpec.Group(
            key = "advanced",
            title = title,
            items = listOf(SettingSpec.Toggle("beta", title)),
        )
        assertEquals("", group.default)
        assertNull(group.normalize("true"))
        assertEquals("beta", (group.items.single() as SettingSpec.Toggle).key)
    }

    @Test
    fun summariesAndRestartFlagsAreOptional() {
        val spec = SettingSpec.Toggle("a", title)
        assertNull(spec.summary)
        assertEquals(false, spec.requiresRestart)

        val restart = SettingSpec.Text(
            key = "b",
            title = title,
            requiresRestart = true,
            summary = LocalizedText.Literal("Applies after restart"),
        )
        assertTrue(restart.requiresRestart)
        assertEquals("Applies after restart", (restart.summary as LocalizedText.Literal).resolve())
    }

    @Test
    fun normalizesWholeSettingsMapWithDefaults() {
        val specs = listOf(
            SettingSpec.Group(
                key = "group",
                title = title,
                items = listOf(SettingSpec.Toggle("enabled", title)),
            ),
            SettingSpec.Number("rounds", title, min = 1, max = 5, defaultValue = 3),
            SettingSpec.Text("name", title, default = "deepdeep"),
        )
        val normalized = specs.normalize(mapOf("enabled" to "false", "rounds" to "99"))
        assertEquals(mapOf("enabled" to "false", "rounds" to "5", "name" to "deepdeep"), normalized)
    }

    @Test
    fun dropsInvalidStoredValues() {
        val specs = listOf(SettingSpec.Toggle("enabled", title))
        assertEquals(emptyMap<String, String>(), specs.normalize(mapOf("enabled" to "maybe")))
        assertEquals(mapOf("enabled" to "false"), specs.normalize(mapOf("unknown" to "x")))
    }
}
