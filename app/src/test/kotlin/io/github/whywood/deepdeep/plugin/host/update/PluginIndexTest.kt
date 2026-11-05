package io.github.whywood.deepdeep.plugin.host.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginIndexTest {
    @Test
    fun parsesValidIndexAndSortsById() {
        val text = UpdateFixtures.indexJson(
            UpdateFixtures.entryJson(
                id = "io.github.whywood.deepdeep.beta",
                version = "2.0.0",
                minHostVersion = "0.2.0",
                apiLevel = 1,
            ),
            UpdateFixtures.entryJson(
                id = "io.github.whywood.deepdeep.alpha",
                version = "1.0.0",
                sha256 = "A".repeat(64),
                publisherKeyId = UpdateFixtures.PUBLISHER_KEY_ID.uppercase(),
                size = 4096L,
            ),
        )

        val result = PluginIndexCodec.parse(text)

        assertTrue(result is PluginIndexParseResult.Valid)
        val index = (result as PluginIndexParseResult.Valid).index
        assertEquals(1, index.schemaVersion)
        assertEquals(2, index.plugins.size)
        assertEquals("io.github.whywood.deepdeep.alpha", index.plugins[0].id)
        assertEquals("beta", index.plugins[1].id.substringAfterLast("."))
        assertEquals("a".repeat(64), index.plugins[0].sha256)
        assertEquals(4096L, index.plugins[0].size)
        assertEquals(UpdateFixtures.PUBLISHER_KEY_ID, index.plugins[0].publisherKeyId)
        assertEquals("0.2.0", index.plugins[1].minHostVersion)
    }

    @Test
    fun rejectsGarbage() {
        assertTrue(PluginIndexCodec.parse("not json") is PluginIndexParseResult.Invalid)
        assertTrue(PluginIndexCodec.parse("[]") is PluginIndexParseResult.Invalid)
        assertTrue(PluginIndexCodec.parse("""{"schemaVersion":1}""") is PluginIndexParseResult.Invalid)
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val text = """{"schemaVersion":2,"plugins":[]}"""

        val result = PluginIndexCodec.parse(text)

        assertTrue(result is PluginIndexParseResult.Invalid)
        assertTrue((result as PluginIndexParseResult.Invalid).reason.contains("schemaVersion"))
    }

    @Test
    fun rejectsDuplicatePluginIds() {
        val text = UpdateFixtures.indexJson(
            UpdateFixtures.entryJson(version = "1.0.0"),
            UpdateFixtures.entryJson(version = "1.1.0"),
        )

        val result = PluginIndexCodec.parse(text)

        assertTrue(result is PluginIndexParseResult.Invalid)
        assertTrue((result as PluginIndexParseResult.Invalid).reason.contains("duplicate"))
    }

    @Test
    fun rejectsTooManyEntries() {
        val entries = (0..PluginUpdateDefaults.MAX_INDEX_ENTRIES).map {
            UpdateFixtures.entryJson(id = "io.github.whywood.deepdeep.p$it")
        }
        val text = UpdateFixtures.indexJson(*entries.toTypedArray())

        val result = PluginIndexCodec.parse(text)

        assertTrue(result is PluginIndexParseResult.Invalid)
        assertTrue((result as PluginIndexParseResult.Invalid).reason.contains("limit"))
    }

    @Test
    fun rejectsInvalidFields() {
        val invalidEntries = listOf(
            UpdateFixtures.entryJson(id = "NotAReverseDomain"),
            UpdateFixtures.entryJson(version = "1.0"),
            UpdateFixtures.entryJson(sha256 = "abc"),
            UpdateFixtures.entryJson(publisherKeyId = "ed25519:xyz"),
            UpdateFixtures.entryJson(url = "ftp://example.test/plugin.ddp"),
            UpdateFixtures.entryJson(size = 0L),
            UpdateFixtures.entryJson(apiLevel = 0),
            UpdateFixtures.entryJson(minHostVersion = "latest"),
        )

        invalidEntries.forEach { entry ->
            val result = PluginIndexCodec.parse(UpdateFixtures.indexJson(entry))
            assertTrue("expected rejection for $entry", result is PluginIndexParseResult.Invalid)
        }
    }
}
