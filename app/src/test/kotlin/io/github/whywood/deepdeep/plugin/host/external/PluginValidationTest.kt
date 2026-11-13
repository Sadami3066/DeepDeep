package io.github.whywood.deepdeep.plugin.host.external

import io.github.whywood.deepdeep.plugin.api.DEFAULT_LANGUAGE
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.MIN_SUPPORTED_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.deepdeep.plugin.api.PluginCapability
import io.github.whywood.deepdeep.plugin.api.PluginManifest
import io.github.whywood.deepdeep.plugin.api.PluginPermission
import io.github.whywood.deepdeep.plugin.api.assetIcon
import io.github.whywood.deepdeep.plugin.api.pluginSkill
import io.github.whywood.deepdeep.plugin.api.pluginSkillFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginValidationTest {
    private val name = hostLocalizedText("Plugin")
    private val summary = hostLocalizedText("Summary")

    private fun manifest(
        id: String = "com.example.plugin",
        version: String = "1.0.0",
        apiLevel: Int = PLUGIN_API_LEVEL,
        minHostVersion: String? = null,
        ui: io.github.whywood.deepdeep.plugin.api.UiManifest? = null,
        name: LocalizedText = this.name,
        summary: LocalizedText = this.summary,
        entryClass: String? = "com.example.plugin.Plugin",
        provides: io.github.whywood.deepdeep.plugin.api.Provides = HostProvides(),
        homepage: String? = null,
        icon: io.github.whywood.deepdeep.plugin.api.IconRef? = null,
        declaredCapabilities: List<String>? = PluginCapability.ALL.map { it.wire },
        capabilities: Set<PluginCapability> = declaredCapabilities.orEmpty().mapNotNull(PluginCapability::fromWire).toSet(),
    ): ManifestWrapper = ManifestWrapper(
        manifest = HostPluginManifest(
            id = id,
            version = version,
            apiLevel = apiLevel,
            minHostVersion = minHostVersion,
            ui = ui,
            name = name,
            summary = summary,
            homepage = homepage,
            icon = icon,
            entryClass = entryClass,
            provides = provides,
            capabilities = capabilities,
        ),
        declaredCapabilities = declaredCapabilities,
    )

    private data class ManifestWrapper(val manifest: PluginManifest, val declaredCapabilities: List<String>?)

    private fun issues(
        wrapper: ManifestWrapper,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
        hostVersion: String? = null,
    ) = PluginValidation.validate(wrapper.manifest, wrapper.declaredCapabilities, hostApiLevel, hostVersion)

    private fun codes(
        wrapper: ManifestWrapper,
        hostApiLevel: Int = PLUGIN_API_LEVEL,
    ): List<PluginValidation.Code> = issues(wrapper, hostApiLevel).map { it.code }

    @Test
    fun validManifestHasNoIssues() {
        assertEquals(emptyList<PluginValidation.Issue>(), issues(manifest()))
        assertEquals(emptyList<PluginValidation.Issue>(), issues(manifest(minHostVersion = "0.2.0"), hostVersion = "0.2.0-dev"))
    }

    @Test
    fun validatesIdFormat() {
        assertEquals(listOf(PluginValidation.Code.INVALID_ID), codes(manifest(id = "NoDots")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ID), codes(manifest(id = "Com.Example")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ID), codes(manifest(id = "com..example")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ID), codes(manifest(id = "timer")))
        assertFalse(
            codes(manifest(id = "io.github.whywood.deepdeep.pluginsample")).contains(PluginValidation.Code.INVALID_ID),
        )
    }

    @Test
    fun validatesVersion() {
        assertEquals(listOf(PluginValidation.Code.INVALID_VERSION), codes(manifest(version = "1.2")))
        assertEquals(listOf(PluginValidation.Code.INVALID_VERSION), codes(manifest(version = "")))
        assertTrue(issues(manifest(version = "1.2.3-dev")).isEmpty())
    }

    @Test
    fun validatesApiLevel() {
        assertEquals(listOf(PluginValidation.Code.API_LEVEL_UNSUPPORTED), codes(manifest(apiLevel = 7)))
        assertEquals(listOf(PluginValidation.Code.API_LEVEL_UNSUPPORTED), codes(manifest(apiLevel = 5)))
        assertEquals(listOf(PluginValidation.Code.API_LEVEL_UNSUPPORTED), codes(manifest(apiLevel = 0)))
        assertTrue(issues(manifest(apiLevel = MIN_SUPPORTED_API_LEVEL)).isEmpty())
        assertEquals(
            listOf(PluginValidation.Code.API_LEVEL_UNSUPPORTED),
            codes(manifest(apiLevel = MIN_SUPPORTED_API_LEVEL), hostApiLevel = MIN_SUPPORTED_API_LEVEL - 1),
        )
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
    fun requiresEntryClass() {
        assertEquals(listOf(PluginValidation.Code.ENTRY_CLASS_MISSING), codes(manifest(entryClass = null)))
        assertEquals(listOf(PluginValidation.Code.ENTRY_CLASS_MISSING), codes(manifest(entryClass = " ")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ENTRY_CLASS), codes(manifest(entryClass = "Plugin")))
        assertEquals(listOf(PluginValidation.Code.INVALID_ENTRY_CLASS), codes(manifest(entryClass = "com.example.1Plugin")))
    }

    @Test
    fun requiredTextNeedsDefaultLanguage() {
        val missing = manifest(name = HostLocalizedText(emptyMap()))
        assertTrue(codes(missing).contains(PluginValidation.Code.MISSING_TEXT))

        val blank = manifest(summary = HostLocalizedText(mapOf(DEFAULT_LANGUAGE to "  ")))
        assertTrue(codes(blank).contains(PluginValidation.Code.MISSING_TEXT))
    }

    @Test
    fun validatesHomepageAndIcon() {
        assertEquals(listOf(PluginValidation.Code.INVALID_URL), codes(manifest(homepage = "example.com")))
        assertTrue(issues(manifest(homepage = "https://example.com")).isEmpty())

        assertEquals(listOf(PluginValidation.Code.INVALID_ICON), codes(manifest(icon = assetIcon("/abs/icon.png"))))
        assertEquals(listOf(PluginValidation.Code.INVALID_ICON), codes(manifest(icon = assetIcon("../icon.png"))))
        assertTrue(issues(manifest(icon = assetIcon("icon.png"))).isEmpty())
    }

    @Test
    fun validatesCapabilities() {
        assertEquals(
            listOf(PluginValidation.Code.MISSING_CAPABILITIES),
            codes(manifest(declaredCapabilities = null, capabilities = emptySet())),
        )
        assertEquals(
            listOf(PluginValidation.Code.INVALID_CAPABILITY),
            codes(manifest(declaredCapabilities = listOf("app", "teleport"))),
        )
        assertTrue(issues(manifest(declaredCapabilities = emptyList(), capabilities = emptySet())).isEmpty())
    }

    @Test
    fun validatesProvidesEntriesAndSlots() {
        val provides = HostProvides(
            tools = listOf("echo", "Bad Tool", "echo"),
            uiSlots = listOf("setup.content", "plugin.page", "tool.card.content:echo", "composer.action"),
            pages = listOf("home", "bad page", "home"),
        )
        val codes = codes(manifest(provides = provides))
        assertEquals(4, codes.count { it == PluginValidation.Code.INVALID_PROVIDES })
        assertEquals(3, codes.count { it == PluginValidation.Code.INVALID_UI_SLOT })
    }

    @Test
    fun uiApiLevelMismatchIsOnlyWarning() {
        val result = issues(manifest(ui = HostUiManifest(apiLevel = 3)))
        assertEquals(listOf(PluginValidation.Code.UI_API_LEVEL_UNSUPPORTED), result.map { it.code })
        assertEquals(PluginValidation.Severity.WARNING, result.single().severity)
    }

    @Test
    fun formatHelpersMatchManifestRules() {
        assertTrue(PluginValidation.isValidId("io.github.whywood.deepdeep.pluginsample"))
        assertFalse(PluginValidation.isValidId("timer"))
        assertFalse(PluginValidation.isValidId("io.github.whywood.deepdeep.pluginsample.Bad"))
        assertTrue(PluginValidation.isValidEntryClass("com.example.HelloPlugin"))
        assertFalse(PluginValidation.isValidEntryClass("HelloPlugin"))
        assertTrue(PluginValidation.isValidToolName("screen_tap"))
        assertFalse(PluginValidation.isValidToolName("Screen Tap"))
        assertTrue(PluginValidation.isSafeRelativePath("skills/miniapp/SKILL.md"))
        assertFalse(PluginValidation.isSafeRelativePath("../escape.md"))
        assertFalse(PluginValidation.isSafeRelativePath("/absolute.md"))
        assertFalse(PluginValidation.isSafeRelativePath("a\\b.md"))
    }

    @Test
    fun validatesSkillFiles() {
        val valid = pluginSkill(
            id = "miniapp",
            markdown = "# Skill",
            files = listOf(
                pluginSkillFile(path = "references/api.md", content = "body"),
                pluginSkillFile(path = "assets/diagram.png", assetPath = "assets/diagram.png"),
            ),
        )
        assertEquals(emptyList<PluginValidation.Issue>(), PluginValidation.validateSkill(valid))

        val invalid = pluginSkill(
            id = "Bad Skill",
            markdown = " ",
            files = listOf(
                pluginSkillFile(path = "../escape.md", content = "x"),
                pluginSkillFile(path = "both.md", content = "x", assetPath = "both.md"),
                pluginSkillFile(path = "neither.md"),
                pluginSkillFile(path = "neither.md", content = "dup"),
            ),
        )
        val codes = PluginValidation.validateSkill(invalid).map { it.code }
        assertTrue(codes.count { it == PluginValidation.Code.INVALID_SKILL } >= 6)
    }
}

class PluginManifestExtensionValidationTest {
    private val baseCapabilities = setOf(
        PluginCapability.LOCATION,
        PluginCapability.NOTIFICATIONS,
        PluginCapability.BACKGROUND,
        PluginCapability.SHARE,
        PluginCapability.DEEPLINK,
        PluginCapability.PERMISSIONS,
        PluginCapability.ACCESSIBILITY,
    )

    private fun manifest(
        capabilities: Set<PluginCapability> = baseCapabilities,
        permissions: List<String> = emptyList(),
        components: HostPluginComponentDeclaration? = null,
    ): HostPluginManifest = HostPluginManifest(
        id = "com.example.plugin",
        version = "1.0.0",
        apiLevel = PLUGIN_API_LEVEL,
        name = hostLocalizedText("Plugin"),
        summary = hostLocalizedText("Summary"),
        entryClass = "com.example.plugin.Plugin",
        capabilities = capabilities,
        permissions = permissions,
        components = components,
    )

    private fun codes(
        capabilities: Set<PluginCapability> = baseCapabilities,
        permissions: List<String> = emptyList(),
        components: HostPluginComponentDeclaration? = null,
    ): List<PluginValidation.Code> =
        PluginValidation.validate(
            manifest(capabilities, permissions, components),
            capabilities.map { it.wire },
        ).map { it.code }

    @Test
    fun acceptsPermissionsAndComponentsWhenCapabilitiesMatch() {
        val issues = codes(
            permissions = listOf(PluginPermission.LOCATION_FINE, PluginPermission.NOTIFICATIONS_LISTEN),
            components = HostPluginComponentDeclaration(
                background = true,
                foregroundService = true,
                notificationListener = true,
                share = true,
                deeplinks = listOf("mail", "mail/compose"),
            ),
        )
        assertEquals(emptyList<PluginValidation.Code>(), issues)
    }

    @Test
    fun rejectsUnknownAndDuplicatePermissions() {
        assertEquals(
            listOf(PluginValidation.Code.INVALID_PERMISSION),
            codes(permissions = listOf("unknown.permission")),
        )
        assertEquals(
            listOf(PluginValidation.Code.INVALID_PERMISSION),
            codes(permissions = listOf(PluginPermission.LOCATION_FINE, PluginPermission.LOCATION_FINE)),
        )
    }

    @Test
    fun rejectsPermissionWithoutCapability() {
        assertEquals(
            listOf(PluginValidation.Code.PERMISSION_WITHOUT_CAPABILITY),
            codes(capabilities = setOf(PluginCapability.APP), permissions = listOf(PluginPermission.LOCATION_FINE)),
        )
    }

    @Test
    fun rejectsComponentWithoutCapability() {
        assertEquals(
            listOf(PluginValidation.Code.COMPONENT_WITHOUT_CAPABILITY),
            codes(capabilities = setOf(PluginCapability.APP), components = HostPluginComponentDeclaration(background = true)),
        )
        assertEquals(
            listOf(PluginValidation.Code.COMPONENT_WITHOUT_CAPABILITY),
            codes(capabilities = setOf(PluginCapability.APP), components = HostPluginComponentDeclaration(deeplinks = listOf("mail"))),
        )
    }

    @Test
    fun rejectsInvalidDeepLinks() {
        assertEquals(
            listOf(PluginValidation.Code.INVALID_DEEPLINK),
            codes(components = HostPluginComponentDeclaration(deeplinks = listOf("Mail Page"))),
        )
        assertEquals(
            listOf(PluginValidation.Code.INVALID_DEEPLINK),
            codes(components = HostPluginComponentDeclaration(deeplinks = listOf("mail", "mail"))),
        )
    }
}
