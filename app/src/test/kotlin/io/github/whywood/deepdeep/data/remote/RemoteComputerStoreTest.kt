package io.github.whywood.deepdeep.data.remote

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteComputerStoreTest {
    private val directory = File(System.getProperty("java.io.tmpdir"), "remote-store-${System.nanoTime()}")
    private val file = File(directory, "remote-computers.json")

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun entry(origin: String, deviceId: String = "device-1") = RemoteEntry(
        deviceId = deviceId,
        origin = origin,
        kind = RemoteEntryKind.Lan,
        deviceExpiresAt = 1000L,
        sessionExpiresAt = 2000L,
        certDerBase64 = "cert",
        certSha256 = "AA:BB",
        lastSeenAt = 3000L,
    )

    private fun computer(instanceId: String, name: String, vararg origins: String) = RemoteComputer(
        instanceId = instanceId,
        name = name,
        lastUsedAt = 0L,
        entries = origins.map { entry(it) },
    )

    @Test
    fun savesAndReloadsComputers() = runTest {
        val store = RemoteComputerStore(file)
        store.save(listOf(computer("abc", "我的电脑", "https://192.168.1.2:3443")))

        val reloaded = RemoteComputerStore(file)
        assertEquals(1, reloaded.cached().size)
        val restored = reloaded.cached().single()
        assertEquals("abc", restored.instanceId)
        assertEquals("我的电脑", restored.name)
        assertEquals("https://192.168.1.2:3443", restored.entries.single().origin)
        assertEquals(RemoteEntryKind.Lan, restored.entries.single().kind)
    }

    @Test
    fun persistsBrowserAuthEntries() = runTest {
        val store = RemoteComputerStore(file)
        store.save(
            listOf(
                RemoteComputer(
                    instanceId = "abc",
                    name = "我的电脑",
                    lastUsedAt = 0L,
                    entries = listOf(
                        entry("https://192.168.1.2:3443"),
                        entry("https://dsh.example.com:8443", deviceId = "browser-1").copy(
                            kind = RemoteEntryKind.Public,
                            auth = RemoteEntryAuth.Browser,
                        ),
                    ),
                ),
            ),
        )

        val restored = RemoteComputerStore(file).cached().single()
        assertEquals(RemoteEntryAuth.Native, restored.entries[0].auth)
        assertEquals(RemoteEntryAuth.Browser, restored.entries[1].auth)
        assertEquals(RemoteEntryKind.Public, restored.entries[1].kind)
    }

    @Test
    fun keepsNameAndMergesEntriesOnUpsert() = runTest {
        val store = RemoteComputerStore(file)
        store.save(listOf(computer("abc", "电脑", "https://192.168.1.2:3443")))

        store.upsert(
            computer(
                "abc",
                "改名",
                "https://192.168.1.2:3443",
                "https://example.com",
            ),
        )

        val restored = store.cached().single()
        assertEquals("电脑", restored.name)
        assertEquals(2, restored.entries.size)
        assertTrue(restored.entries.any { it.origin == "https://example.com" })
    }

    @Test
    fun mergesComputersWithSameInstallationId() = runTest {
        val store = RemoteComputerStore(file)
        store.save(
            listOf(
                computer("lan-a", "电脑", "https://192.168.1.2:3443")
                    .copy(installationId = "a".repeat(64)),
            ),
        )

        store.upsert(
            computer("remote-b", "另一台", "https://example.com")
                .copy(installationId = "a".repeat(64)),
        )

        val restored = store.cached().single()
        assertEquals(2, restored.entries.size)
        assertEquals("电脑", restored.name)
        assertEquals("a".repeat(64), restored.installationId)
    }

    @Test
    fun learnInstallationMergesRows() = runTest {
        val store = RemoteComputerStore(file)
        store.save(
            listOf(
                computer("lan-a", "电脑", "https://192.168.1.2:3443"),
                computer("remote-b", "电脑", "https://example.com")
                    .copy(installationId = "b".repeat(64)),
            ),
        )

        store.learnInstallation("lan-a", "b".repeat(64))

        val restored = store.cached().single()
        assertEquals(2, restored.entries.size)
        assertEquals("b".repeat(64), restored.installationId)
    }

    @Test
    fun renamesAndRemovesComputer() = runTest {
        val store = RemoteComputerStore(file)
        store.save(listOf(computer("abc", "电脑", "https://192.168.1.2:3443")))

        store.rename("abc", "新名字")
        assertEquals("新名字", store.cached().single().name)

        store.remove("abc")
        assertTrue(store.cached().isEmpty())
        assertTrue(RemoteComputerStore(file).cached().isEmpty())
    }

    @Test
    fun normalizationDropsInvalidEntriesAndDuplicates() = runTest {
        val store = RemoteComputerStore(file)
        store.save(
            listOf(
                computer("abc", "电脑", "http://insecure:3443", "https://192.168.1.2:3443", "https://192.168.1.2:3443/"),
                computer("abc", "重复", "https://10.0.0.2:3443"),
                computer("", "无 id", "https://10.0.0.3:3443"),
            ),
        )

        assertEquals(1, store.cached().size)
        val origins = store.cached().single().entries.map { it.origin }.toSet()
        assertEquals(setOf("https://192.168.1.2:3443", "https://10.0.0.2:3443"), origins)
    }

    @Test
    fun nameIsTrimmedAndCapped() = runTest {
        val store = RemoteComputerStore(file)
        store.save(listOf(computer("abc", "  ${"长".repeat(50)}  ", "https://192.168.1.2:3443")))

        val name = store.cached().single().name
        assertEquals(32, name.length)
    }
}
