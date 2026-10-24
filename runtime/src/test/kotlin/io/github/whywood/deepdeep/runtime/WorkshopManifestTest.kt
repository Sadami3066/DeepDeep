package io.github.whywood.deepdeep.runtime

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

    private fun assertManifestFails(text: String) {
        try {
            WorkshopManifest.parse(text)
            fail("expected WorkshopManifestException")
        } catch (_: WorkshopManifestException) {
        }
    }

    @Test
    fun minimalManifestUsesDefaults() {
        val manifest = WorkshopManifest.parse(
            manifestJson(
                "id" to string("todo"),
                "name" to string("Todo"),
                "type" to string("board"),
            ),
        )
        assertEquals("todo", manifest.id)
        assertEquals("Todo", manifest.name)
        assertEquals(WorkshopType.BOARD, manifest.type)
        assertEquals("🗂️", manifest.icon)
        assertEquals("1.0.0", manifest.version)
        assertEquals("index.html", manifest.entry)
        assertEquals("", manifest.description)
        assertEquals(emptySet<String>(), manifest.permissions)
    }

    @Test
    fun fullManifestParsesEveryField() {
        val manifest = WorkshopManifest.parse(
            manifestJson(
                "id" to string("todo-board_2"),
                "name" to string("  Todo Board  "),
                "type" to string("workstation"),
                "icon" to string("✅"),
                "version" to string("2.1.0"),
                "entry" to string("pages/main.html"),
                "description" to string("  A small board  "),
                "permissions" to "[\"data.kv\",\"agent.run\"]",
            ),
        )
        assertEquals("todo-board_2", manifest.id)
        assertEquals("Todo Board", manifest.name)
        assertEquals(WorkshopType.WORKSTATION, manifest.type)
        assertEquals("✅", manifest.icon)
        assertEquals("2.1.0", manifest.version)
        assertEquals("pages/main.html", manifest.entry)
        assertEquals("A small board", manifest.description)
        assertEquals(setOf("data.kv", "agent.run"), manifest.permissions)
    }

    @Test
    fun unknownManifestFieldsAreIgnored() {
        val manifest = WorkshopManifest.parse(
            manifestJson(
                "id" to string("app"),
                "name" to string("App"),
                "type" to string("workflow"),
                "extra" to "{\"nested\":true}",
            ),
        )
        assertEquals(WorkshopType.WORKFLOW, manifest.type)
    }

    @Test
    fun missingRequiredFieldsFail() {
        assertManifestFails("{}")
        assertManifestFails(manifestJson("id" to string("app")))
        assertManifestFails(manifestJson("id" to string("app"), "name" to string("App")))
        assertManifestFails(manifestJson("name" to string("App"), "type" to string("board")))
        assertFailsWithMessage("id is required", "{}")
    }

    private fun assertFailsWithMessage(fragment: String, text: String) {
        try {
            WorkshopManifest.parse(text)
            fail("expected WorkshopManifestException")
        } catch (error: WorkshopManifestException) {
            assertTrue("expected message to contain '$fragment' but was '${error.message}'", error.message.orEmpty().contains(fragment))
        }
    }

    @Test
    fun invalidIdsFail() {
        assertManifestFails(manifestJson("id" to string("Upper"), "name" to string("A"), "type" to string("board")))
        assertManifestFails(manifestJson("id" to string("-lead"), "name" to string("A"), "type" to string("board")))
        assertManifestFails(manifestJson("id" to string("a".repeat(49)), "name" to string("A"), "type" to string("board")))
        assertManifestFails(manifestJson("id" to string("a b"), "name" to string("A"), "type" to string("board")))
    }

    @Test
    fun nameAndTypeValidation() {
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("  "), "type" to string("board")))
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("a".repeat(41)), "type" to string("board")))
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "type" to string("Board")))
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "type" to string("game")))
        WorkshopManifest.parse(manifestJson("id" to string("a"), "name" to string("a".repeat(40)), "type" to string("board")))
    }

    @Test
    fun iconVersionAndDescriptionLimits() {
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "type" to string("board"), "icon" to string("123456789")))
        assertEquals("12345678", WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "type" to string("board"), "icon" to string("12345678")),
        ).icon)
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "type" to string("board"), "version" to string("1".repeat(21))))
        assertEquals("1".repeat(20), WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "type" to string("board"), "version" to string("1".repeat(20))),
        ).version)
        assertManifestFails(manifestJson("id" to string("a"), "name" to string("A"), "type" to string("board"), "description" to string("d".repeat(301))))
        assertEquals(300, WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "type" to string("board"), "description" to string("d".repeat(300))),
        ).description.length)
    }

    @Test
    fun entryMustBeRelativeHtmlInsideTheFolder() {
        fun withEntry(entry: String): String = manifestJson(
            "id" to string("a"),
            "name" to string("A"),
            "type" to string("board"),
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
    fun permissionsAreTrimmedDedupedAndKeepUnknownNames() {
        val manifest = WorkshopManifest.parse(
            manifestJson(
                "id" to string("a"),
                "name" to string("A"),
                "type" to string("board"),
                "permissions" to "[\"data.kv\",\" data.kv \",\"custom.permission\",\"agent.run\"]",
            ),
        )
        assertEquals(setOf("data.kv", "custom.permission", "agent.run"), manifest.permissions)
        val none = WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("A"), "type" to string("board")),
        )
        assertTrue(none.permissions.isEmpty())
    }

    @Test
    fun invalidPermissionsFail() {
        fun withPermissions(value: String): String = manifestJson(
            "id" to string("a"),
            "name" to string("A"),
            "type" to string("board"),
            "permissions" to value,
        )
        assertManifestFails(withPermissions("[\"Data.KV\"]"))
        assertManifestFails(withPermissions("[\"bad permission\"]"))
        assertManifestFails(withPermissions("[\"1data\"]"))
        assertManifestFails(withPermissions("\"data.kv\""))
        assertManifestFails(withPermissions("[1]"))
    }

    @Test
    fun oversizedManifestFails() {
        val text = manifestJson(
            "id" to string("a"),
            "name" to string("A"),
            "type" to string("board"),
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
                "type" to string("workflow"),
                "icon" to string("⚙️"),
                "version" to string("3.0.0"),
                "entry" to string("app/index.html"),
                "description" to string("desc"),
                "permissions" to "[\"agent.run\",\"data.kv\"]",
            ),
        )
        val encoded = original.toJson()
        assertTrue(encoded.contains("\n"))
        assertEquals(original, WorkshopManifest.parse(encoded))
    }

    @Test
    fun withNameTrimsAndValidates() {
        val manifest = WorkshopManifest.parse(
            manifestJson("id" to string("a"), "name" to string("Old"), "type" to string("board")),
        )
        val renamed = manifest.withName("  New Name  ")
        assertEquals("New Name", renamed.name)
        assertEquals(manifest.id, renamed.id)
        assertEquals(manifest.permissions, renamed.permissions)
        assertManifestFailsThrown { manifest.withName("   ") }
        assertManifestFailsThrown { manifest.withName("a".repeat(41)) }
    }

    private fun assertManifestFailsThrown(block: () -> Unit) {
        try {
            block()
            fail("expected WorkshopManifestException")
        } catch (_: WorkshopManifestException) {
        }
    }

    @Test
    fun typeWireValues() {
        assertEquals("board", WorkshopType.BOARD.wire)
        assertEquals("workstation", WorkshopType.WORKSTATION.wire)
        assertEquals("workflow", WorkshopType.WORKFLOW.wire)
        assertEquals(WorkshopType.BOARD, WorkshopType.fromWire("board"))
        assertEquals(WorkshopType.WORKSTATION, WorkshopType.fromWire(" workstation "))
        assertEquals(null, WorkshopType.fromWire(null))
        assertEquals(null, WorkshopType.fromWire("game"))
    }

    @Test
    fun permissionsKnownSet() {
        assertEquals(setOf("data.kv", "agent.run"), WorkshopPermissions.KNOWN)
    }
}
