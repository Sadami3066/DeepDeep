package io.github.whywood.deepdeep.plugin.host.update

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginUpdateSelectorTest {
    @Test
    fun selectsNewerSamePublisherOnly() {
        val index = UpdateFixtures.index(
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.a", version = "1.1.0"),
            UpdateFixtures.entry(
                id = "io.github.whywood.deepdeep.b",
                version = "1.1.0",
                publisherKeyId = UpdateFixtures.OTHER_KEY_ID,
            ),
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.c", version = "1.0.0"),
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.d", version = "0.9.0"),
            UpdateFixtures.entry(id = "io.github.whywood.deepdeep.e", version = "1.1.0"),
        )
        val installed = mapOf(
            "io.github.whywood.deepdeep.a" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.a"),
            "io.github.whywood.deepdeep.b" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.b"),
            "io.github.whywood.deepdeep.c" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.c"),
            "io.github.whywood.deepdeep.d" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.d"),
            "io.github.whywood.deepdeep.e" to UpdateFixtures.record(
                id = "io.github.whywood.deepdeep.e",
                pendingVersion = "1.1.0",
            ),
            "io.github.whywood.deepdeep.f" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.f"),
            "io.github.whywood.deepdeep.g" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.g"),
        )
        val policy = UpdatePolicy(hostApiLevel = 4, hostVersion = "0.2.0")
        val fullIndex = PluginIndex(
            schemaVersion = 1,
            plugins = index.plugins + listOf(
                UpdateFixtures.entry(
                    id = "io.github.whywood.deepdeep.f",
                    version = "1.1.0",
                    apiLevel = 5,
                ),
                UpdateFixtures.entry(
                    id = "io.github.whywood.deepdeep.g",
                    version = "1.1.0",
                    minHostVersion = "9.0.0",
                ),
                UpdateFixtures.entry(
                    id = "io.github.whywood.deepdeep.h",
                    version = "1.1.0",
                    minHostVersion = "0.2.0",
                ),
            ),
        )
        val installedWithPolicy = installed + ("io.github.whywood.deepdeep.h" to UpdateFixtures.record(id = "io.github.whywood.deepdeep.h"))

        val candidates = PluginUpdateSelector.select(fullIndex, installedWithPolicy, policy)

        assertEquals(listOf("io.github.whywood.deepdeep.a", "io.github.whywood.deepdeep.h"), candidates.map { it.entry.id })
        assertEquals("1.0.0", candidates.first().installedVersion)
    }
}
