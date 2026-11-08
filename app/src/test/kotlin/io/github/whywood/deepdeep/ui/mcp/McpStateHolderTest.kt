package io.github.whywood.deepdeep.ui.mcp

import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.mcp.config.McpServerConfig
import io.github.whywood.deepdeep.sdk.mcp.oauth.McpOAuthInteraction
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.McpConfigSnapshot
import io.github.whywood.deepdeep.runtime.McpProbeResult
import io.github.whywood.deepdeep.runtime.McpProbeState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class McpStateHolderTest {
    private val stdio = McpServerConfig.Stdio(command = "npx file-server", description = "Local files", enabled = false)
    private val search = McpServerConfig.Http(
        url = "https://search.example.com/mcp",
        description = "Web search",
        enabled = false,
    )
    private val github = McpServerConfig.Http(url = "https://api.github.com/mcp", enabled = false)
    private val oauth = McpServerConfig.Http(url = "https://oauth.example.com/mcp", enabled = false)

    private class FakeSource : McpDataSource {
        var snapshot = McpConfigSnapshot(emptyMap(), emptyMap(), emptyList())
        var loadError: Exception? = null
        var loadCalls = 0
        var activeLoads = 0
        var maxActiveLoads = 0
        var loadGate: CompletableDeferred<Unit>? = null
        var probeError: Exception? = null
        var probeGate: CompletableDeferred<Unit>? = null
        var probeResult = McpProbeResult(McpProbeState.OK, toolCount = 2)
        var saveError: Exception? = null
        var removeError: Exception? = null
        var setEnabledError: Exception? = null
        var signInResult: Result<Unit> = Result.success(Unit)
        var signOutCalls = 0
        val saved = mutableListOf<Pair<String, McpServerConfig>>()
        val removed = mutableListOf<String>()
        val enabledCalls = mutableListOf<Pair<String, Boolean>>()

        override suspend fun load(): McpConfigSnapshot {
            loadCalls++
            activeLoads++
            maxActiveLoads = maxOf(maxActiveLoads, activeLoads)
            try {
                loadGate?.await()
                loadError?.let { throw it }
                return snapshot
            } finally {
                activeLoads--
            }
        }

        override suspend fun setEnabled(name: String, enabled: Boolean) {
            setEnabledError?.let { throw it }
            enabledCalls += name to enabled
        }

        override suspend fun save(name: String, config: McpServerConfig) {
            saveError?.let { throw it }
            saved += name to config
        }

        override suspend fun remove(name: String) {
            removeError?.let { throw it }
            removed += name
        }

        override suspend fun probe(name: String, config: McpServerConfig): McpProbeResult {
            probeError?.let { throw it }
            probeGate?.await()
            return probeResult
        }

        override suspend fun signIn(
            server: String,
            config: McpServerConfig.Http,
            interaction: McpOAuthInteraction,
            redirectUri: String,
            signal: RunSignal?,
        ): Result<Unit> = signInResult

        override suspend fun signOut(server: String, config: McpServerConfig.Http) {
            signOutCalls++
        }
    }

    private object FakeInteraction : McpOAuthInteraction {
        override suspend fun authorize(url: String, redirectUri: String): String = redirectUri
    }

    private fun holder(source: FakeSource, scope: TestScope): McpStateHolder =
        McpStateHolder(source, scope)

    private fun snapshot(vararg servers: Pair<String, McpServerConfig>, errors: List<String> = emptyList()) =
        McpConfigSnapshot(emptyMap(), servers.toMap(), errors)

    private fun source(vararg servers: Pair<String, McpServerConfig>) = FakeSource().also {
        it.snapshot = snapshot(*servers)
    }

    @Test
    fun loadPopulatesServersAndClearsLoading() = runTest {
        val fake = FakeSource().also {
            it.snapshot = snapshot("search" to search, "files" to stdio, errors = listOf("bad entry"))
        }
        val holder = holder(fake, this)
        holder.load()

        assertFalse(holder.state.value.loading)
        assertEquals(listOf("files", "search"), holder.state.value.servers.map { it.name })
        assertEquals(listOf("bad entry"), holder.state.value.notices)
        assertNull(holder.state.value.error)
        assertEquals(1, fake.loadCalls)
    }

    @Test
    fun loadFailureSetsVisibleRetryableError() = runTest {
        val fake = source("files" to stdio)
        fake.loadError = IOException("disk failed")
        val holder = holder(fake, this)
        holder.load()

        val error = holder.state.value.error
        assertNotNull(error)
        assertEquals(R.string.mcp_error_load_failed, error!!.messageRes)
        assertTrue(error.retryable)
        assertFalse(holder.state.value.loading)
    }

    @Test
    fun retryAfterLoadFailureReloadsServers() = runTest {
        val fake = source("files" to stdio)
        fake.loadError = IOException("disk failed")
        val holder = holder(fake, this)
        holder.load()
        assertNotNull(holder.state.value.error)

        fake.loadError = null
        holder.retry()
        advanceUntilIdle()

        assertNull(holder.state.value.error)
        assertEquals(listOf("files"), holder.state.value.servers.map { it.name })
    }

    @Test
    fun probeTogglesProbingIdsThenStoresResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        val result = McpProbeResult(McpProbeState.OK, toolCount = 2)
        val enabled = McpServerConfig.Http(url = "https://search.example.com/mcp", enabled = true)
        val fake = source("search" to enabled)
        fake.probeGate = gate
        fake.probeResult = result
        val holder = holder(fake, this)

        val loading = async { holder.load() }
        advanceUntilIdle()
        assertEquals(setOf("search"), holder.state.value.probingIds)
        assertTrue(holder.state.value.probes.isEmpty())

        gate.complete(Unit)
        loading.await()

        assertTrue(holder.state.value.probingIds.isEmpty())
        assertEquals(result, holder.state.value.probes["search"])
    }

    @Test
    fun saveFailureSetsRetryableError() = runTest {
        val fake = source("files" to stdio)
        fake.saveError = IOException("read only")
        val holder = holder(fake, this)
        holder.load()

        val saved = holder.save("files", McpServerConfig.Stdio(command = "npx other", enabled = false))

        assertFalse(saved)
        val error = holder.state.value.error
        assertNotNull(error)
        assertEquals(R.string.mcp_error_save_failed, error!!.messageRes)
        assertTrue(error.retryable)
        assertTrue(fake.saved.isEmpty())
    }

    @Test
    fun setEnabledFailureSetsRetryableError() = runTest {
        val fake = source("files" to stdio)
        fake.setEnabledError = IOException("read only")
        val holder = holder(fake, this)
        holder.load()

        val enabled = holder.setEnabled("files", true)

        assertFalse(enabled)
        val error = holder.state.value.error
        assertNotNull(error)
        assertEquals(R.string.mcp_error_toggle_failed, error!!.messageRes)
        assertTrue(error.retryable)
        assertNull(holder.state.value.busyName)
    }

    @Test
    fun queryFiltersVisibleServers() = runTest {
        val holder = holder(source("files" to stdio, "search" to search, "github" to github), this)
        holder.load()

        holder.setQuery("EXAMPLE")
        assertEquals(listOf("search"), holder.state.value.visibleServers.map { it.name })

        holder.setQuery("web search")
        assertEquals(listOf("search"), holder.state.value.visibleServers.map { it.name })

        holder.setQuery("local files")
        assertEquals(listOf("files"), holder.state.value.visibleServers.map { it.name })

        holder.setQuery("")
        assertEquals(listOf("files", "github", "search"), holder.state.value.visibleServers.map { it.name })
    }

    @Test
    fun concurrentRefreshesCoalesceIntoOneFollowUp() = runTest {
        val fake = source("files" to stdio)
        val holder = holder(fake, this)
        holder.load()
        fake.loadCalls = 0
        fake.activeLoads = 0
        fake.maxActiveLoads = 0
        val gate = CompletableDeferred<Unit>()
        fake.loadGate = gate

        val first = async { holder.refresh() }
        val second = async { holder.refresh() }
        val third = async { holder.refresh() }
        advanceUntilIdle()
        assertEquals(1, fake.loadCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()
        third.await()

        assertEquals(2, fake.loadCalls)
        assertEquals(1, fake.maxActiveLoads)
        fake.loadGate = null
    }

    @Test
    fun loginThenLogoutUpdatesOauthState() = runTest {
        val fake = source("oauth" to oauth)
        val holder = holder(fake, this)
        holder.load()

        assertTrue(holder.login("oauth", oauth, FakeInteraction, "http://127.0.0.1:1/callback"))
        assertEquals(true, holder.state.value.oauthSignedIn["oauth"])
        assertNull(holder.state.value.oauthBusyServer)

        assertTrue(holder.logout("oauth", oauth))
        assertEquals(false, holder.state.value.oauthSignedIn["oauth"])
        assertEquals(1, fake.signOutCalls)
    }
}
