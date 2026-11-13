package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.host.PluginPaths
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PluginThemeInstallTest {
    private lateinit var dir: File
    private lateinit var pluginsRoot: File
    private lateinit var trustStore: PublisherTrustStore
    private lateinit var recordStore: ExternalPluginRecordStore
    private lateinit var key: SyntheticDdp.SigningKey

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("plugin-theme-install").toFile()
        pluginsRoot = File(dir, "plugins")
        trustStore = PublisherTrustStore(File(pluginsRoot, "trusted_publishers.json"))
        recordStore = ExternalPluginRecordStore(File(pluginsRoot, "plugins.json"))
        key = SyntheticDdp.newSigningKey()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun acceptsDeclaredThemePack() = runBlocking {
        val pkg = packageWithThemes(
            declared = listOf("aurora"),
            themesJson = themePack(listOf("aurora")),
        )

        val inspection = installer().inspect(pkg)
        assertTrue(inspection is InspectionResult.Ready)
        assertEquals(listOf("aurora"), (inspection as InspectionResult.Ready).manifest.provides.themes)

        val result = installer().install(pkg, acceptPublisher = true)
        assertTrue(result is InstallResult.Installed)
        val record = (result as InstallResult.Installed).record
        val installedThemes = File(
            File(PluginPaths.versionRoot(pluginsRoot, record.id, record.version), PluginPaths.ASSETS_DIR),
            "themes.json",
        )
        assertTrue(installedThemes.isFile)
    }

    @Test
    fun rejectsLowContrastTheme() = runBlocking {
        val json = themePack(listOf("aurora"), tokens = "\"paper\": \"#222222\"")
        val pkg = packageWithThemes(declared = listOf("aurora"), themesJson = json)

        val inspection = installer().inspect(pkg)
        assertTrue(inspection is InspectionResult.Rejected)
        assertEquals(InstallRejection.THEMES_INVALID, (inspection as InspectionResult.Rejected).code)

        val result = installer().install(pkg, acceptPublisher = true)
        assertTrue(result is InstallResult.Rejected)
        assertEquals(InstallRejection.THEMES_INVALID, (result as InstallResult.Rejected).code)
        assertTrue(result.reason.startsWith("invalid plugin themes:"))
    }

    @Test
    fun rejectsMissingThemeFile() = runBlocking {
        val pkg = SyntheticDdp.build(
            dir,
            "missing.dpp",
            SyntheticDdp.BuildRequest(
                manifest = SyntheticDdp.manifestJson(themes = listOf("aurora")),
                key = key,
            ),
        )

        val inspection = installer().inspect(pkg)
        assertTrue(inspection is InspectionResult.Rejected)
        assertEquals(InstallRejection.THEMES_INVALID, (inspection as InspectionResult.Rejected).code)
    }

    @Test
    fun rejectsThemeFileWithoutDeclaration() = runBlocking {
        val pkg = packageWithThemes(declared = emptyList(), themesJson = themePack(listOf("aurora")))

        val inspection = installer().inspect(pkg)
        assertTrue(inspection is InspectionResult.Rejected)
        assertEquals(InstallRejection.THEMES_INVALID, (inspection as InspectionResult.Rejected).code)
    }

    @Test
    fun rejectsUndeclaredTheme() = runBlocking {
        val pkg = packageWithThemes(
            declared = listOf("aurora"),
            themesJson = themePack(listOf("aurora", "midnight")),
        )

        val inspection = installer().inspect(pkg)
        assertTrue(inspection is InspectionResult.Rejected)
        val reason = (inspection as InspectionResult.Rejected).reason
        assertTrue(reason.contains("midnight"))
    }

    @Test
    fun rejectsMissingDeclaredTheme() = runBlocking {
        val pkg = packageWithThemes(
            declared = listOf("aurora", "midnight"),
            themesJson = themePack(listOf("aurora")),
        )

        val inspection = installer().inspect(pkg)
        assertTrue(inspection is InspectionResult.Rejected)
        val reason = (inspection as InspectionResult.Rejected).reason
        assertTrue(reason.contains("midnight"))
    }

    private fun installer(): PluginInstaller = PluginInstaller(
        pluginsRoot = pluginsRoot,
        trustStore = trustStore,
        recordStore = recordStore,
        hostVersion = "0.2.0-dev",
    )

    private fun packageWithThemes(declared: List<String>, themesJson: String): File =
        SyntheticDdp.build(
            dir,
            "themed.dpp",
            SyntheticDdp.BuildRequest(
                manifest = SyntheticDdp.manifestJson(themes = declared),
                key = key,
                extraEntries = mapOf(ThemeEntry to themesJson.toByteArray(Charsets.UTF_8)),
            ),
        )

    private fun themePack(ids: List<String>, tokens: String = ""): String {
        val themes = ids.joinToString(",") { id ->
            """{ "id": "$id", "name": { "default": "$id" }, "dark": false, "base": "deepdeep", "tokens": { $tokens } }"""
        }
        return """{"schema":1,"themes":[$themes]}"""
    }

    private companion object {
        const val ThemeEntry = "assets/themes.json"
    }
}
