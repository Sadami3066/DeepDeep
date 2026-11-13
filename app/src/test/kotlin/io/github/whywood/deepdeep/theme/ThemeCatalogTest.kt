package io.github.whywood.deepdeep.theme

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeCatalogTest {
    private lateinit var dir: File
    private lateinit var assets: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("theme-catalog").toFile()
        assets = File(dir, "assets").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun loadsThemesFromPluginAssets() {
        writePack("aurora")
        val catalog = ThemeCatalog()

        catalog.refresh(listOf(source()))

        val pack = catalog.packs().single()
        assertEquals("com.example.plugin", pack.pluginId)
        assertEquals("Demo", pack.pluginName)
        assertEquals("aurora", pack.themes.single().id)
        assertEquals("aurora", catalog.find("com.example.plugin", "aurora")?.id)
        assertNull(catalog.find("com.example.plugin", "missing"))
    }

    @Test
    fun cachesUntilSourceVersionChanges() {
        writePack("aurora")
        val catalog = ThemeCatalog()
        catalog.refresh(listOf(source()))
        assertEquals("aurora", catalog.packs().single().themes.single().id)

        writePack("changed")
        assertEquals("aurora", catalog.packs().single().themes.single().id)

        catalog.refresh(listOf(source(version = "2.0.0")))
        assertEquals("changed", catalog.packs().single().themes.single().id)
    }

    @Test
    fun ignoresMissingOrInvalidFiles() {
        val catalog = ThemeCatalog()
        catalog.refresh(listOf(source()))
        assertTrue(catalog.packs().isEmpty())

        File(assets, "themes.json").writeText("""{"schema":2,"themes":[]}""")
        catalog.refresh(listOf(source(version = "2.0.0")))
        assertTrue(catalog.packs().isEmpty())
    }

    private fun source(version: String = "1.0.0"): PluginThemeSource = PluginThemeSource(
        pluginId = "com.example.plugin",
        pluginName = "Demo",
        version = version,
        assetsDir = assets,
    )

    private fun writePack(name: String) {
        File(assets, "themes.json").writeText(
            """
            {
              "schema": 1,
              "themes": [
                { "id": "$name", "name": { "default": "$name" }, "dark": false, "base": "deepdeep", "tokens": {} }
              ]
            }
            """.trimIndent(),
        )
    }
}
