package io.github.whywood.deepdeep.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkshopManifestTest {
    private fun manifestJson(vararg fields: Pair<String, String>): String =
        fields.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) -> "\"$key\":$value" }

    private fun string(value: String): String {
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    private fun minimal(): WorkshopManifest = WorkshopManifest.parse(
        manifestJson("id" to string("mini-app"), "name" to string("Mini App")),
    )

    private fun assertManifestFails(text: String) {
        try {
            WorkshopManifest.parse(text)
            fail("expected WorkshopManifestException")
        } catch (_: WorkshopManifestException) {
        }
    }

    private fun assertManifestFailsMessage(fragment: String, text: String) {
        try {
            WorkshopManifest.parse(text)
            fail("expected WorkshopManifestException")
        } catch (error: WorkshopManifestException) {
            assertTrue(
                "expected message to contain '$fragment' but was '${error.message}'",
                error.message.orEmpty().contains(fragment),
            )
        }
    }

    private fun assertManifestFailsWith(fragment: String, block: () -> Unit) {
        try {
            block()
            fail("expected WorkshopManifestException")
        } catch (error: WorkshopManifestException) {
            assertTrue(
                "expected message to contain '$fragment' but was '${error.message}'",
                error.message.orEmpty().contains(fragment),
            )
        }
    }

    @Test
    fun minimalManifestUsesDefaults() {
        val manifest = minimal()
        assertEquals("mini-app", manifest.id)
        assertEquals("Mini App", manifest.name)
        assertEquals("", manifest.icon)
        assertEquals("1.0.0", manifest.version)
        assertEquals("index.html", manifest.entry)
        assertEquals("", manifest.description)
    }

    @Test
    fun fullManifestParsesEveryField() {
        val manifest = WorkshopManifest.parse(
            manifestJson(
                "id" to string("todo-board_2"),
                "name" to string("  Todo Board  "),
                "icon" to string("✅"),
                "version" to string("2.1.0"),
                "entry" to string("pages/main.html"),
                "description" to string("  A small board  "),
            ),
        )
        assertEquals("todo-board_2", manifest.id)
        assertEquals("Todo Board", manifest.name)
        assertEquals("✅", manifest.icon)
        assertEquals("2.1.0", manifest.version)
        assertEquals("pages/main.html", manifest.entry)
        assertEquals("A small board", manifest.description)
    }

    @Test
    fun legacyFieldsAreIgnored() {
        val manifest = WorkshopManifest.parse(
            manifestJson(
                "id" to string("legacy-app"),
                "name" to string("Legacy App"),
                "type" to string("board"),
                "permissions" to "[\"data.kv\",\"agent.run\"]",
                "createdAt" to "1700000000",
                "starter" to "true",
            ),
        )
        assertEquals("legacy-app", manifest.id)
        assertEquals("", manifest.icon)
        val encoded = Json.parseToJsonElement(manifest.toJson()).jsonObject
        assertEquals(
            setOf("id", "name", "icon", "version", "entry", "description"),
            encoded.keys,
        )
    }

    @Test
    fun toJsonEmitsExactlyTheManifestFields() {
        val encoded = Json.parseToJsonElement(minimal().toJson()).jsonObject
        assertEquals(
            listOf("id", "name", "icon", "version", "entry", "description"),
            encoded.keys.toList(),
        )
    }

    @Test
    fun missingRequiredFieldsFail() {
        assertManifestFails("{}")
        assertManifestFails(manifestJson("id" to string("app")))
        assertManifestFails(manifestJson("name" to string("App")))
        assertManifestFailsMessage("id is required", "{}")
        assertManifestFailsMessage("name is required", manifestJson("id" to string("app")))
    }

    @Test
    fun nonObjectOrInvalidJsonFails() {
        assertManifestFails("[]")
        assertManifestFails("not json")
        assertManifestFails("")
    }

    @Test
    fun invalidIdsFail() {
        assertManifestFails(manifestJson("id" to string("Upper"), "name" to string("A")))
        assertManifestFails(manifestJson("id" to string("-lead"), "name" to string("A")))
        assertManifestFails(manifestJson("id" to string("a".repeat(49)), "name" to string("A")))
        assertManifestFails(manifestJson("id" to string("a b"), "name" to string("A")))
    }

    @Test
    fun nameValidation() {
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("  ")))
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("a".repeat(41))))
        WorkshopManifest.parse(manifestJson("id" to string("a"), "name" to string("a".repeat(40))))
    }

    @Test
    fun iconIsOptionalAndLimited() {
        assertEquals("", minimal().icon)
        assertEquals("", WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "icon" to string("   ")),
        ).icon)
        assertEquals("12345678", WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "icon" to string("12345678")),
        ).icon)
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "icon" to string("123456789")))
    }

    @Test
    fun versionAndDescriptionLimits() {
        assertEquals("1".repeat(20), WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "version" to string("1".repeat(20))),
        ).version)
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "version" to string("1".repeat(21))))
        assertEquals(300, WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "description" to string("d".repeat(300))),
        ).description.length)
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "description" to string("d".repeat(301))))
    }

    @Test
    fun entryMustBeRelativeHtmlInsideTheFolder() {
        fun withEntry(entry: String): String = manifestJson(
            "id" to string("a"),
            "name" to string("A"),
            "entry" to string(entry),
        )
        assertManifestFails(withEntry("../evil.html"))
        assertManifestFails(withEntry("pages/../../evil.html"))
        assertManifestFails(withEntry("/absolute.html"))
        assertManifestFails(withEntry("index.htm"))
        assertManifestFails(withEntry("main.js"))
        assertManifestFails(withEntry("a\\b.html"))
        assertEquals("pages/main.html", WorkshopManifest.parse(withEntry("pages/main.html")).entry)
        assertEquals("a".repeat(115) + ".html", WorkshopManifest.parse(withEntry("a".repeat(115) + ".html")).entry)
        assertManifestFails(withEntry("a".repeat(116) + ".html"))
    }

    @Test
    fun wrongFieldTypesFail() {
        assertManifestFails(manifestJson("id" to "1", "name" to string("A")))
        assertManifestFails(manifestJson("id" to string("a"), "name" to "true"))
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "icon" to "1"))
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "entry" to "[]"))
    }

    @Test
    fun oversizedManifestFails() {
        val text = manifestJson(
            "id" to string("a"),
            "name" to string("A"),
            "padding" to string("x".repeat(WorkshopManifest.MAX_BYTES)),
        )
        assertManifestFails(text)
    }

    @Test
    fun toJsonRoundTrips() {
        val original = WorkshopManifest.parse(
            manifestJson(
                "id" to string("round-trip"),
                "name" to string("Round Trip"),
                "icon" to string("⚙️"),
                "version" to string("3.0.0"),
                "entry" to string("app/index.html"),
                "description" to string("desc"),
            ),
        )
        val encoded = original.toJson()
        assertTrue(encoded.contains("\n"))
        assertEquals(original, WorkshopManifest.parse(encoded))
    }

    @Test
    fun withNameTrimsAndValidates() {
        val manifest = minimal()
        val renamed = manifest.withName("  New Name  ")
        assertEquals("New Name", renamed.name)
        assertEquals(manifest.id, renamed.id)
        assertEquals(manifest.icon, renamed.icon)
        assertManifestFailsWith("name must be") { manifest.withName("   ") }
        assertManifestFailsWith("name must be") { manifest.withName("a".repeat(41)) }
    }

    @Test
    fun companionConstants() {
        assertEquals("index.html", WorkshopManifest.DEFAULT_ENTRY)
        assertEquals("1.0.0", WorkshopManifest.DEFAULT_VERSION)
        assertEquals(64 * 1024, WorkshopManifest.MAX_BYTES)
        assertTrue(WorkshopManifest.ID_REGEX.matches("abc-123_x"))
    }
}
