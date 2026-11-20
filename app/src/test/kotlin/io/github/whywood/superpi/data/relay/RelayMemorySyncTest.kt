package io.github.whywood.superpi.data.relay

import io.github.whywood.superpi.runtime.MemoryWriteOutcome
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RelayMemorySyncTest {
    @Test
    fun uploadsLocalMemoryOnFirstLogin() = runTest {
        val env = environment()
        env.localProfile("- Likes dark mode")
        env.login()
        env.sync.refreshIfStale(maxAgeMillis = 0)

        assertTrue(env.server.files.getValue("profile.md").contains("Likes dark mode"))
        assertTrue(env.syncedAccount())
        env.sync.state.value.active
    }

    @Test
    fun pullsServerSnapshotWhenLocalMemoryIsEmpty() = runTest {
        val env = environment()
        env.server.put("context.md", "# Now & Ahead\n\n## Now & Ahead\n\n- Ship the shell\n")
        env.login()
        env.sync.refreshIfStale(maxAgeMillis = 0)

        assertTrue(File(env.root, "context.md").readText().contains("Ship the shell"))
        assertEquals(1L, env.sync.state.value.version)
        assertNull(env.sync.state.value.notice)
    }

    @Test
    fun backsUpLocalMemoryWhenBothSidesHaveContent() = runTest {
        val env = environment()
        env.localProfile("- Local only\n")
        env.server.put("profile.md", "# You & I\n\n- Server side\n")
        env.login()
        env.sync.refreshIfStale(maxAgeMillis = 0)

        assertTrue(File(env.root, "profile.md").readText().contains("Server side"))
        val backups = env.root.listFiles().orEmpty().filter { it.name.startsWith(".conflict-") }
        assertEquals(1, backups.size)
        assertTrue(File(backups.single(), "profile.md").readText().contains("Local only"))
        assertEquals(RelayMemoryNotice.LocalBackedUp, env.sync.state.value.notice)
    }

    @Test
    fun queuesWritesWhileOfflineAndReplaysThem() = runTest {
        val env = environment()
        env.login()
        env.sync.refreshIfStale(maxAgeMillis = 0)

        env.server.offline = true
        val outcome = env.sync.append("profile.md", null, "Offline entry")
        assertEquals(MemoryWriteOutcome.Queued, outcome)
        assertTrue(File(env.filesDir, "relay-memory-outbox.json").isFile)
        assertEquals(1, env.sync.state.value.pending)

        env.server.offline = false
        env.sync.refreshIfStale(maxAgeMillis = 0)

        assertTrue(env.server.files.getValue("profile.md").contains("Offline entry"))
        assertEquals(0, env.sync.state.value.pending)
    }

    @Test
    fun replaysQueuedWritesWithoutDuplicates() = runTest {
        val env = environment()
        env.login()
        env.sync.refreshIfStale(maxAgeMillis = 0)

        env.server.offline = true
        env.sync.append("profile.md", null, "Once only")
        env.server.offline = false
        env.server.applyThenOffline = true
        env.sync.refreshIfStale(maxAgeMillis = 0)
        assertEquals(1, env.sync.state.value.pending)

        env.sync.refreshIfStale(maxAgeMillis = 0)

        assertEquals(1, Regex("Once only").findAll(env.server.files.getValue("profile.md")).count())
        assertEquals(0, env.sync.state.value.pending)
    }

    @Test
    fun rejectsWritesThatKeepConflicting() = runTest {
        val env = environment()
        env.login()
        env.sync.refreshIfStale(maxAgeMillis = 0)

        env.server.alwaysConflict = true
        val outcome = env.sync.overwrite("profile.md", "# You & I\n\n- Mine\n")

        assertTrue(outcome is MemoryWriteOutcome.Rejected)
        assertTrue((outcome as MemoryWriteOutcome.Rejected).message.contains("changed on another device"))
    }

    @Test
    fun fallsBackToLocalMemoryWhenSignedOut() = runTest {
        val env = environment()
        assertEquals(MemoryWriteOutcome.Inactive, env.sync.append("profile.md", null, "Local entry"))
        assertEquals(MemoryWriteOutcome.Inactive, env.sync.overwrite("profile.md", "# You & I\n"))
    }

    @Test
    fun clearsSharedStateWhenSignedOut() = runTest {
        val env = environment()
        env.server.put("profile.md", "# You & I\n\n- Server side\n")
        env.login()
        env.sync.refreshIfStale(maxAgeMillis = 0)
        assertTrue(env.sync.state.value.active)

        env.accounts.value.value = null
        testScheduler.advanceUntilIdle()

        assertTrue(!env.sync.state.value.active)
        assertNull(env.sync.state.value.lastSyncedAt)
    }

    private fun TestScope.environment(): Environment {
        val base = File.createTempFile("relay-memory", "")
        base.delete()
        base.mkdirs()
        val filesDir = File(base, "files").apply { mkdirs() }
        val root = File(base, "memory").apply { mkdirs() }
        val accounts = FakeAccounts()
        val server = FakeMemoryApi()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val sync = RelayMemorySync(
            filesDir = filesDir,
            rootDir = root,
            accounts = accounts,
            client = server,
            scope = scope,
            io = UnconfinedTestDispatcher(testScheduler),
        )
        return Environment(base, filesDir, root, accounts, server, sync)
    }

    private class Environment(
        private val base: File,
        val filesDir: File,
        val root: File,
        val accounts: FakeAccounts,
        val server: FakeMemoryApi,
        val sync: RelayMemorySync,
    ) {
        init {
            base.deleteOnExit()
        }

        fun localProfile(body: String) {
            File(root, "profile.md").writeText("# You & I\n\n$body")
        }

        fun login() {
            accounts.value.value = account
        }

        fun syncedAccount(): Boolean {
            val prefs = File(filesDir, "relay-memory.json")
            return prefs.isFile && prefs.readText().contains(account.accountId)
        }
    }

    private class FakeAccounts : RelayAccountStore {
        val value = MutableStateFlow<RelayAccount?>(null)

        override val account: Flow<RelayAccount?> = value

        override suspend fun read(): RelayAccount? = value.value

        override suspend fun token(): String? = value.value?.let { "token" }

        override suspend fun save(account: RelayAccount, token: String) {
            value.value = account
        }

        override suspend fun clear() {
            value.value = null
        }
    }

    private class FakeMemoryApi : RelayMemoryApi {
        val files = LinkedHashMap<String, String>()
        private val versions = HashMap<String, Long>()
        private val writes = HashSet<String>()
        private var version = 0L

        @Volatile
        var offline = false

        @Volatile
        var alwaysConflict = false

        @Volatile
        var applyThenOffline = false

        fun put(path: String, text: String) {
            files[path] = text
            version += 1
            versions[path] = version
        }

        override suspend fun snapshot(account: RelayAccount, token: String): RelayMemorySnapshot {
            if (offline) throw RelayMemoryException(RelayMemoryFailure.Unreachable)
            return RelayMemorySnapshot(
                version = version,
                files = files.toMap(),
                versions = versions.toMap(),
            )
        }

        override suspend fun write(account: RelayAccount, token: String, request: RelayMemoryWrite): Long {
            if (offline) throw RelayMemoryException(RelayMemoryFailure.Unreachable)
            if (request.idempotencyKey in writes) return versions[request.path] ?: 0L
            if (alwaysConflict) throw RelayMemoryException(RelayMemoryFailure.Conflict)
            val current = files[request.path].orEmpty()
            val currentVersion = versions[request.path] ?: 0L
            if (request.op != "append" && request.baseVersion != currentVersion) {
                throw RelayMemoryException(RelayMemoryFailure.Conflict)
            }
            val next = when (request.op) {
                "append" -> appendBlock(current, request.section, request.text)
                "replace_section" -> replaceSection(current, request.section.orEmpty(), request.text)
                "overwrite" -> request.text.trimEnd() + "\n"
                else -> throw RelayMemoryException(RelayMemoryFailure.Rejected, "unknown op")
            }
            files[request.path] = next
            version += 1
            versions[request.path] = version
            writes += request.idempotencyKey
            if (applyThenOffline) {
                applyThenOffline = false
                throw RelayMemoryException(RelayMemoryFailure.Unreachable)
            }
            return version
        }

        private fun appendBlock(content: String, section: String?, text: String): String {
            if (section == null) {
                val base = content.trimEnd()
                return (if (base.isEmpty()) "" else "$base\n\n") + text + "\n"
            }
            val heading = "## $section"
            val index = content.indexOf(heading)
            if (index < 0) {
                val base = content.trimEnd()
                return (if (base.isEmpty()) "" else "$base\n\n") + "$heading\n\n$text\n"
            }
            val after = content.indexOf("\n## ", index + heading.length)
            val head = content.substring(0, if (after < 0) content.length else after).trimEnd()
            val tail = if (after < 0) "" else content.substring(after)
            return "$head\n$text\n$tail"
        }

        private fun replaceSection(content: String, section: String, text: String): String {
            val heading = "## $section"
            val index = content.indexOf(heading)
            if (index < 0) {
                val base = content.trimEnd()
                return (if (base.isEmpty()) "" else "$base\n\n") + "$heading\n\n$text\n"
            }
            val after = content.indexOf("\n## ", index + heading.length)
            val head = content.substring(0, index + heading.length).trimEnd()
            val tail = if (after < 0) "" else content.substring(after)
            return "$head\n\n$text\n$tail"
        }
    }

    private companion object {
        val account = RelayAccount(
            origin = "https://relay.example:8443",
            fingerprint = "ab".repeat(32),
            caPem = "PEM",
            deviceId = "dev",
            deviceName = "Pixel",
            accountId = "acct",
            enrolledAt = 1L,
        )
    }
}
