package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestCodecTest {
    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    private fun codes(result: PluginManifestCodec.Result): List<PluginValidation.Code> =
        when (result) {
            is PluginManifestCodec.Result.Valid -> result.warnings.map { it.code }
            is PluginManifestCodec.Result.Invalid -> result.issues.map { it.code }
        }

    private val sampleJson = json(
        """
        {
          "id": "io.github.whywood.deepdeep.pluginsample",
          "version": "1.0.0",
          "apiLevel": 4,
          "capabilities": ["app", "files", "notifications"],
          "minHostVersion": "0.2.0",
          "entryClass": "io.github.whywood.deepdeep.pluginsample.SamplePlugin",
          "name": { "default": "Sample Plugin", "zh": "示例插件", "en": "Sample Plugin" },
          "summary": { "default": "External plugin sample", "zh": "外部插件示例", "en": "External plugin sample" },
          "icon": "asset:icon.png",
          "provides": {
            "tools": ["sample_echo"],
            "uiSlots": ["tool.card.content:sample_echo"],
            "pages": ["home"]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun parsesSampleManifest() {
        val manifest = PluginManifestCodec.parse(sampleJson)
        assertEquals("io.github.whywood.deepdeep.pluginsample", manifest.id)
        assertEquals("1.0.0", manifest.version)
        assertEquals(PLUGIN_API_LEVEL, manifest.apiLevel)
        assertEquals("0.2.0", manifest.minHostVersion)
        assertEquals("io.github.whywood.deepdeep.pluginsample.SamplePlugin", manifest.entryClass)
        assertEquals(IconRef.Asset("icon.png"), manifest.icon)
        assertEquals(listOf("sample_echo"), manifest.provides.tools)
        assertEquals(listOf("tool.card.content:sample_echo"), manifest.provides.uiSlots)
        assertEquals(listOf("home"), manifest.provides.pages)
        assertEquals(setOf("app", "files", "notifications"), manifest.capabilities)
        assertEquals(true, manifest.defaultEnabled)
        assertEquals(false, manifest.requiresPrivilege)
        assertNull(manifest.ui)
        assertNull(manifest.publisher)
        assertNull(manifest.description)
        assertNull(manifest.homepage)
        assertNull(manifest.changelog)
    }

    @Test
    fun parsesLocalizedText() {
        val text = PluginManifestCodec.parse(sampleJson).name as LocalizedText.Literal
        assertEquals("Sample Plugin", text.resolve(DEFAULT_LANGUAGE))
        assertEquals("示例插件", text.resolve("zh"))
        assertEquals("Sample Plugin", text.resolve("en"))
    }

    @Test
    fun parsesStringTextShorthand() {
        val manifest = PluginManifestCodec.parse(
            json(
                """
                {
                  "id": "com.example.min",
                  "version": "1.0.0",
                  "entryClass": "com.example.min.MinPlugin",
                  "name": "Minimal",
                  "summary": { "default": "Minimal plugin" }
                }
                """.trimIndent(),
            ),
        )
        assertEquals(LocalizedText.Literal("Minimal"), manifest.name)
        assertEquals(BASELINE_API_LEVEL, manifest.apiLevel)
    }

    @Test
    fun parsesFullOptionalFields() {
        val manifest = PluginManifestCodec.parse(
            json(
                """
                {
                  "id": "com.example.demo",
                  "version": "1.2.3",
                  "apiLevel": 4,
                  "minHostVersion": "0.2.0",
                  "ui": { "apiLevel": 1, "kotlin": "2.1.0", "compose": "1.8.0" },
                  "name": { "default": "Demo" },
                  "summary": { "default": "Demo plugin" },
                  "description": { "default": "Long description" },
                  "homepage": "https://example.com/demo",
                  "changelog": "Initial release",
                  "icon": "asset:assets/icon.png",
                  "entryClass": "com.example.demo.DemoPlugin",
                  "publisher": { "name": "Example", "keyId": "key-1" },
                  "provides": {
                    "tools": ["demo_echo"],
                    "skills": ["demo-skill"],
                    "uiSlots": ["tool.card.content:demo_echo", "chat.message:demo.card"],
                    "pages": ["home"]
                  },
                  "requiresPrivilege": true,
                  "defaultEnabled": false
                }
                """.trimIndent(),
            ),
        )
        assertEquals(true, manifest.requiresPrivilege)
        assertEquals(false, manifest.defaultEnabled)
        assertEquals(PublisherInfo("Example", "key-1"), manifest.publisher)
        assertEquals("https://example.com/demo", manifest.homepage)
        assertEquals("Initial release", manifest.changelog)
        assertEquals("Long description", (manifest.description as LocalizedText.Literal).resolve())
        assertEquals(UiManifest(apiLevel = 1, kotlin = "2.1.0", compose = "1.8.0"), manifest.ui)
        assertEquals(
            Provides(
                tools = listOf("demo_echo"),
                skills = listOf("demo-skill"),
                uiSlots = listOf("tool.card.content:demo_echo", "chat.message:demo.card"),
                pages = listOf("home"),
            ),
            manifest.provides,
        )
    }

    @Test
    fun toleratesWrongScalarTypesAndStringBooleans() {
        val manifest = PluginManifestCodec.parse(
            json(
                """
                {
                  "id": "com.example.tolerant",
                  "version": "1.0.0",
                  "entryClass": "com.example.tolerant.TolerantPlugin",
                  "name": { "default": "Tolerant" },
                  "summary": { "default": "Tolerant plugin" },
                  "apiLevel": "one",
                  "requiresPrivilege": "maybe"
                }
                """.trimIndent(),
            ),
        )
        assertEquals(BASELINE_API_LEVEL, manifest.apiLevel)
        assertEquals(false, manifest.requiresPrivilege)
    }

    @Test
    fun rejectsLegacyIconWithoutAssetPrefix() {
        val manifest = PluginManifestCodec.parse(
            json(
                """
                {
                  "id": "com.example.icon",
                  "version": "1.0.0",
                  "entryClass": "com.example.icon.IconPlugin",
                  "name": { "default": "Icon" },
                  "summary": { "default": "Icon" },
                  "icon": "icon.png"
                }
                """.trimIndent(),
            ),
        )
        assertNull(manifest.icon)
    }

    @Test
    fun ignoresUnknownFields() {
        val manifest = PluginManifestCodec.parse(
            json(
                """
                {
                  "id": "com.example.future",
                  "version": "1.0.0",
                  "entryClass": "com.example.future.FuturePlugin",
                  "name": { "default": "Future" },
                  "summary": { "default": "Future" },
                  "futureField": { "nested": [1, 2, 3] }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("com.example.future", manifest.id)
    }

    @Test
    fun decodesValidSample() {
        val result = PluginManifestCodec.decode(sampleJson, hostVersion = "0.2.0")
        assertTrue(result is PluginManifestCodec.Result.Valid)
        assertEquals(emptyList<PluginValidation.Code>(), codes(result))
    }

    @Test
    fun decodesValidSampleWithDevHostVersion() {
        val result = PluginManifestCodec.decode(sampleJson, hostVersion = "0.2.0-dev")
        assertTrue(result is PluginManifestCodec.Result.Valid)
    }

    @Test
    fun rejectsManifestMissingId() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "version": "1.0.0",
                  "entryClass": "com.example.missing.MissingPlugin",
                  "name": { "default": "Missing" },
                  "summary": { "default": "Missing" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Invalid)
        assertTrue(codes(result).contains(PluginValidation.Code.INVALID_ID))
    }

    @Test
    fun rejectsInvalidVersion() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.version",
                  "version": "1.2",
                  "entryClass": "com.example.version.VersionPlugin",
                  "name": { "default": "Version" },
                  "summary": { "default": "Version" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Invalid)
        assertTrue(codes(result).contains(PluginValidation.Code.INVALID_VERSION))
    }

    @Test
    fun rejectsTooNewApiLevel() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.future",
                  "version": "1.0.0",
                  "apiLevel": 5,
                  "entryClass": "com.example.future.FuturePlugin",
                  "name": { "default": "Future" },
                  "summary": { "default": "Future" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Invalid)
        assertTrue(codes(result).contains(PluginValidation.Code.API_LEVEL_UNSUPPORTED))
    }

    @Test
    fun rejectsOlderHostThanRequired() {
        val json = json(
            """
            {
              "id": "com.example.newer",
              "version": "1.0.0",
              "minHostVersion": "0.3.0",
              "capabilities": ["app"],
              "entryClass": "com.example.newer.NewerPlugin",
              "name": { "default": "Newer" },
              "summary": { "default": "Newer" }
            }
            """.trimIndent(),
        )
        val rejected = PluginManifestCodec.decode(json, hostVersion = "0.2.0")
        assertTrue(rejected is PluginManifestCodec.Result.Invalid)
        assertTrue(codes(rejected).contains(PluginValidation.Code.HOST_VERSION_UNSUPPORTED))

        val accepted = PluginManifestCodec.decode(json, hostVersion = "0.3.0-dev")
        assertTrue(accepted is PluginManifestCodec.Result.Valid)
    }

    @Test
    fun rejectsManifestWithoutEntryClass() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.noentry",
                  "version": "1.0.0",
                  "name": { "default": "No Entry" },
                  "summary": { "default": "No Entry" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Invalid)
        assertTrue(codes(result).contains(PluginValidation.Code.ENTRY_CLASS_MISSING))
    }

    @Test
    fun rejectsLegacyBuiltinId() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "timer",
                  "version": "1.0.0",
                  "entryClass": "com.example.timer.TimerPlugin",
                  "name": { "default": "Timer" },
                  "summary": { "default": "Timer" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(codes(result).contains(PluginValidation.Code.INVALID_ID))
    }

    @Test
    fun rejectsManifestWithoutCapabilities() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.nocaps",
                  "version": "1.0.0",
                  "entryClass": "com.example.nocaps.NoCapsPlugin",
                  "name": { "default": "No Caps" },
                  "summary": { "default": "No Caps" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Invalid)
        assertTrue(codes(result).contains(PluginValidation.Code.MISSING_CAPABILITIES))
    }

    @Test
    fun rejectsUnknownCapability() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.badcap",
                  "version": "1.0.0",
                  "capabilities": ["app", "teleport"],
                  "entryClass": "com.example.badcap.BadCapPlugin",
                  "name": { "default": "Bad Cap" },
                  "summary": { "default": "Bad Cap" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Invalid)
        assertEquals(1, codes(result).count { it == PluginValidation.Code.INVALID_CAPABILITY })
    }

    @Test
    fun acceptsEmptyCapabilities() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.emptycaps",
                  "version": "1.0.0",
                  "capabilities": [],
                  "entryClass": "com.example.emptycaps.EmptyPlugin",
                  "name": { "default": "Empty" },
                  "summary": { "default": "Empty" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Valid)
    }

    @Test
    fun rejectsInvalidProvidedSlot() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.slot",
                  "version": "1.0.0",
                  "entryClass": "com.example.slot.SlotPlugin",
                  "name": { "default": "Slot" },
                  "summary": { "default": "Slot" },
                  "provides": { "uiSlots": ["composer.action", "plugin.detail.section"] }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Invalid)
        assertEquals(2, codes(result).count { it == PluginValidation.Code.INVALID_UI_SLOT })
    }

    @Test
    fun uiApiLevelMismatchStaysValidWithWarning() {
        val result = PluginManifestCodec.decode(
            json(
                """
                {
                  "id": "com.example.ui",
                  "version": "1.0.0",
                  "ui": { "apiLevel": 2 },
                  "capabilities": ["app"],
                  "entryClass": "com.example.ui.UiPlugin",
                  "name": { "default": "Ui" },
                  "summary": { "default": "Ui" }
                }
                """.trimIndent(),
            ),
        )
        assertTrue(result is PluginManifestCodec.Result.Valid)
        assertTrue(codes(result).contains(PluginValidation.Code.UI_API_LEVEL_UNSUPPORTED))
    }
}
