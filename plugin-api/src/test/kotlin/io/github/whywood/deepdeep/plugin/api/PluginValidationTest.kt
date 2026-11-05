package io.github.whywood.deepdeep.plugin.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginValidationTest {
    private val name = LocalizedText.Literal("Plugin")
    private val summary = LocalizedText.Literal("Summary")

    private fun manifest(
        id: String = "com.example.plugin",
        version: String = "1.0.0",
        apiLevel: Int = PLUGIN_API_LEVEL,
        minHostVersion: String? = null,
        ui: UiManifest? = null,
        name: LocalizedText = this.name,
        summary: LocalizedText = this.summary,
        entryClass: String? = "com.example.plugin.Plugin",
        source: PluginSource = PluginSource.EXTERNAL,
        requires: List<String> = emptyList(),
        provides: Provides = Provides(),
        homepage: String? = null,
        icon: IconRef? = null,
    ) = PluginManifest(
        id = id,
        version = version,
        apiLevel = apiLevel,
        minHostVersion = minHostVersion,
        ui = ui,
        name = name,
        summary = summary,
        homepage = homepage,
        icon = icon,
        source = source,
        entryClass = entryClass,
        requires = requires,
        provides = provides,
    )

    private fun issues(
        manifest: PluginManifest,
        source: PluginSource = manifest.source,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ) = PluginValidation.validate(manifest, source, hostApiLevel, hostVersion)

    private fun codes(manifest: PluginManifest): List<PluginValidation.Code> = issues(manifest).map { it.code }

    @Test
    fun validManifestHasNoIssues() {
        assertEquals(emptyList<PluginValidation.Issue>(), issues(manifest()))
        assertEquals(emptyList<PluginValidation.Issue>(), issues(manifest(minHostVersion = "0.2.0"), hostVersion = "0.2.0-dev"))
    }

    @Test
    fun validatesIdFormatPerSource() {
        assertEquals(listOf(PluginValidation.Code.INVALID_ID), codes(manifest(id = "NoDots")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ID), codes(manifest(id = "Com.Example")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ID), codes(manifest(id = "com..example")))
        assertFalse(
            codes(manifest(id = "io.github.whywood.deepdeep.pluginsample", entryClass = null))
                .contains(PluginValidation.Code.INVALID_ID),
        )

        val builtin = manifest(id = "timer", entryClass = null, source = PluginSource.BUILTIN)
        assertTrue(issues(builtin).isEmpty())
        assertTrue(codes(manifest(id = "Timer Plugin", source = PluginSource.BUILTIN, entryClass = null)).contains(PluginValidation.Code.INVALID_ID))
    }

    @Test
    fun validatesVersion() {
        assertEquals(listOf(PluginValidation.Code.INVALID_VERSION), codes(manifest(version = "1.2")))
        assertEquals(listOf(PluginValidation.Code.INVALID_VERSION), codes(manifest(version = "")))
        assertTrue(issues(manifest(version = "1.2.3-dev")).isEmpty())
    }

    @Test
    fun validatesApiLevel() {
        assertEquals(listOf(PluginValidation.Code.API_LEVEL_UNSUPPORTED), codes(manifest(apiLevel = 2)))
        assertEquals(listOf(PluginValidation.Code.API_LEVEL_UNSUPPORTED), codes(manifest(apiLevel = 0)))
        assertTrue(issues(manifest(apiLevel = 1), hostApiLevel = 2).isEmpty())
    }

    @Test
    fun validatesMinHostVersion() {
        assertEquals(
            listOf(PluginValidation.Code.HOST_VERSION_UNSUPPORTED),
            issues(manifest(minHostVersion = "0.3.0"), hostVersion = "0.2.0").map { it.code },
        )
        assertEquals(
            listOf(PluginValidation.Code.INVALID_MIN_HOST_VERSION),
            issues(manifest(minHostVersion = "next")).map { it.code },
        )
        assertTrue(issues(manifest(minHostVersion = "0.2.0"), hostVersion = "0.2.0").isEmpty())
    }

    @Test
    fun externalPluginRequiresEntryClass() {
        assertEquals(listOf(PluginValidation.Code.ENTRY_CLASS_MISSING), codes(manifest(entryClass = null)))
        assertEquals(listOf(PluginValidation.Code.ENTRY_CLASS_MISSING), codes(manifest(entryClass = " ")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ENTRY_CLASS), codes(manifest(entryClass = "Plugin")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ENTRY_CLASS), codes(manifest(entryClass = "com.example.1Plugin")))
    }

    @Test
    fun externalPluginCannotUseResourceText() {
        val external = manifest(name = LocalizedText.Resource(1))
        assertTrue(codes(external).contains(PluginValidation.Code.INVALID_TEXT))

        val builtin = manifest(
            id = "timer",
            name = LocalizedText.Resource(1),
            summary = LocalizedText.Resource(2),
            entryClass = null,
            source = PluginSource.BUILTIN,
        )
        assertTrue(issues(builtin).isEmpty())
    }

    @Test
    fun requiredTextNeedsDefaultLanguage() {
        val missing = manifest(name = LocalizedText.Literal(emptyMap()))
        assertTrue(codes(missing).contains(PluginValidation.Code.MISSING_TEXT))

        val blank = manifest(summary = LocalizedText.Literal(mapOf(DEFAULT_LANGUAGE to "  ")))
        assertTrue(codes(blank).contains(PluginValidation.Code.MISSING_TEXT))
    }

    @Test
    fun validatesHomepageAndIcon() {
        assertEquals(listOf(PluginValidation.Code.INVALID_URL), codes(manifest(homepage = "example.com")))
        assertTrue(issues(manifest(homepage = "https://example.com")).isEmpty())

        assertEquals(listOf(PluginValidation.Code.INVALID_ICON), codes(manifest(icon = IconRef.Asset("/abs/icon.png"))))
        assertEquals(listOf(PluginValidation.Code.INVALID_ICON), codes(manifest(icon = IconRef.Asset("../icon.png"))))
        assertEquals(listOf(PluginValidation.Code.INVALID_ICON), codes(manifest(icon = IconRef.Resource(1))))
        assertTrue(issues(manifest(icon = IconRef.Asset("icon.png"))).isEmpty())
    }

    @Test
    fun validatesProvidesEntriesAndSlots() {
        val provides = Provides(
            tools = listOf("echo", "Bad Tool", "echo"),
            uiSlots = listOf("setup.content", "plugin.detail.section", "tool.card.content:echo", "composer.action"),
        )
        val codes = codes(manifest(provides = provides))
        assertEquals(2, codes.count { it == PluginValidation.Code.INVALID_PROVIDES })
        assertEquals(2, codes.count { it == PluginValidation.Code.INVALID_UI_SLOT })
    }

    @Test
    fun rejectsBlankRequiresEntry() {
        assertEquals(listOf(PluginValidation.Code.INVALID_REQUIRES), codes(manifest(requires = listOf("host:linux", " "))))
    }

    @Test
    fun uiApiLevelMismatchIsOnlyWarning() {
        val result = issues(manifest(ui = UiManifest(apiLevel = 2)))
        assertEquals(listOf(PluginValidation.Code.UI_API_LEVEL_UNSUPPORTED), result.map { it.code })
        assertEquals(PluginValidation.Severity.WARNING, result.single().severity)
    }

    @Test
    fun compatibilityChecksAbiFields() {
        assertTrue(PluginValidation.isCompatible(manifest()))
        assertFalse(PluginValidation.isCompatible(manifest(apiLevel = 2)))
        assertFalse(PluginValidation.isCompatible(manifest(ui = UiManifest(apiLevel = 2))))
        assertFalse(PluginValidation.isCompatible(manifest(minHostVersion = "0.3.0"), hostVersion = "0.2.0"))
        assertTrue(PluginValidation.isCompatible(manifest(minHostVersion = "0.2.0"), hostVersion = "0.2.0-dev"))
        assertFalse(PluginValidation.isCompatible(manifest(minHostVersion = "broken")))
    }

    @Test
    fun formatHelpersMatchManifestRules() {
        assertTrue(PluginValidation.isValidExternalId("io.github.whywood.deepdeep.pluginsample"))
        assertFalse(PluginValidation.isValidExternalId("timer"))
        assertTrue(PluginValidation.isValidBuiltinId("timer"))
        assertTrue(PluginValidation.isValidBuiltinId("web-search"))
        assertFalse(PluginValidation.isValidBuiltinId("Web Search"))
        assertTrue(PluginValidation.isValidEntryClass("com.example.HelloPlugin"))
        assertFalse(PluginValidation.isValidEntryClass("HelloPlugin"))
        assertTrue(PluginValidation.isValidToolName("screen_tap"))
        assertFalse(PluginValidation.isValidToolName("Screen Tap"))
        assertTrue(PluginValidation.isSafeRelativePath("skills/workshop/SKILL.md"))
        assertFalse(PluginValidation.isSafeRelativePath("../escape.md"))
        assertFalse(PluginValidation.isSafeRelativePath("/absolute.md"))
        assertFalse(PluginValidation.isSafeRelativePath("a\\b.md"))
    }

    @Test
    fun validatesSkillFiles() {
        val valid = PluginSkill(
            id = "workshop",
            markdown = "# Skill",
            files = listOf(
                PluginSkillFile(path = "references/api.md", content = "body"),
                PluginSkillFile(path = "assets/diagram.png", assetPath = "assets/diagram.png"),
            ),
        )
        assertEquals(emptyList<PluginValidation.Issue>(), PluginValidation.validateSkill(valid))

        val invalid = valid.copy(
            id = "Bad Skill",
            markdown = " ",
            files = listOf(
                PluginSkillFile(path = "../escape.md", content = "x"),
                PluginSkillFile(path = "both.md", content = "x", assetPath = "both.md"),
                PluginSkillFile(path = "neither.md"),
                PluginSkillFile(path = "neither.md", content = "dup"),
            ),
        )
        val codes = PluginValidation.validateSkill(invalid).map { it.code }
        assertTrue(codes.count { it == PluginValidation.Code.INVALID_SKILL } >= 6)
    }

    @Test
    fun localizedTextHelpers() {
        val text = LocalizedText.Literal(mapOf(DEFAULT_LANGUAGE to "fallback", "zh" to "中文"))
        assertEquals("中文", text.resolve("zh"))
        assertEquals("fallback", text.resolve("en"))
        assertEquals("fallback", text.resolve())
        assertTrue(text.hasDefault())

        val shorthand = LocalizedText.Literal("only default")
        assertEquals(mapOf(DEFAULT_LANGUAGE to "only default"), shorthand.values)
    }
}
