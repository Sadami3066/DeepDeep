package io.github.whywood.deepdeep.ui.skills

import io.github.whywood.deepdeep.data.InstalledSkill
import io.github.whywood.deepdeep.data.SkillArchiveError
import io.github.whywood.deepdeep.data.SkillArchiveResult
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
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
class SkillsStateHolderTest {
    private val alpha = InstalledSkill("alpha", "Alpha", "Alpha skill", false, false, 1)
    private val beta = InstalledSkill("beta", "Beta", "Search the web", false, true, 2)

    private class FakeSource : SkillsDataSource {
        override val changes = MutableStateFlow(0L)
        var items: List<InstalledSkill> = emptyList()
        var listError: Exception? = null
        var listCalls = 0
        var activeListCalls = 0
        var maxActiveListCalls = 0
        var listGate: CompletableDeferred<Unit>? = null
        var enabledResult = true
        var deleteResult = true
        var installResult: SkillArchiveResult = SkillArchiveResult.Failure(SkillArchiveError.Io)
        var syncActiveCalls = 0
        var syncChangedCalls = 0
        var enabledCalls = mutableListOf<Pair<String, Boolean>>()
        var deletedIds = mutableListOf<String>()

        override suspend fun list(): Result<List<InstalledSkill>> {
            listCalls++
            activeListCalls++
            maxActiveListCalls = maxOf(maxActiveListCalls, activeListCalls)
            try {
                listGate?.await()
                val error = listError
                return if (error != null) Result.failure(error) else Result.success(items)
            } finally {
                activeListCalls--
            }
        }

        override suspend fun syncActiveSkills(): Int {
            syncActiveCalls++
            return 0
        }

        override suspend fun syncIfChanged(): Int {
            syncChangedCalls++
            return 0
        }

        override suspend fun setGlobalEnabled(id: String, enabled: Boolean): Boolean {
            enabledCalls += id to enabled
            if (!enabledResult) return false
            items = items.map { if (it.id == id) it.copy(globalEnabled = enabled) else it }
            changes.value += 1
            return true
        }

        override suspend fun delete(id: String): Boolean {
            deletedIds += id
            if (!deleteResult) return false
            items = items.filterNot { it.id == id }
            changes.value += 1
            return true
        }

        override suspend fun install(bytes: ByteArray, fallbackName: String): SkillArchiveResult = installResult

        override suspend fun readContent(id: String): String? = "content:$id"
    }

    private fun source(): FakeSource = FakeSource().also { it.items = listOf(alpha, beta) }

    private suspend fun TestScope.holder(fake: FakeSource): SkillsStateHolder {
        val holder = SkillsStateHolder(fake, this)
        holder.load()
        fake.listCalls = 0
        fake.activeListCalls = 0
        fake.maxActiveListCalls = 0
        fake.syncActiveCalls = 0
        fake.syncChangedCalls = 0
        fake.enabledCalls.clear()
        fake.deletedIds.clear()
        return holder
    }

    @Test
    fun loadPopulatesItemsAndClearsLoading() = runTest {
        val fake = source()
        val holder = holder(fake)
        holder.load()
        advanceUntilIdle()

        assertFalse(holder.state.value.loading)
        assertEquals(listOf("alpha", "beta"), holder.state.value.items.map { it.id })
        assertEquals(1, fake.syncActiveCalls)
        assertNull(holder.state.value.error)
    }

    @Test
    fun loadFailureSetsVisibleRetryableError() = runTest {
        val fake = source()
        fake.listError = IOException("disk failed")
        val holder = holder(fake)
        holder.load()
        advanceUntilIdle()

        val error = holder.state.value.error
        assertNotNull(error)
        assertTrue(error!!.retryable)
        assertFalse(holder.state.value.loading)
    }

    @Test
    fun retryAfterLoadFailureReloadsItems() = runTest {
        val fake = source()
        fake.listError = IOException("disk failed")
        val holder = holder(fake)
        holder.load()
        advanceUntilIdle()
        assertNotNull(holder.state.value.error)

        fake.listError = null
        holder.retry()
        advanceUntilIdle()

        assertNull(holder.state.value.error)
        assertEquals(listOf("alpha", "beta"), holder.state.value.items.map { it.id })
    }

    @Test
    fun deleteFailureSetsRetryableError() = runTest {
        val fake = source()
        fake.deleteResult = false
        val holder = holder(fake)
        holder.delete(alpha)
        advanceUntilIdle()

        assertEquals(listOf("alpha"), fake.deletedIds)
        assertTrue(holder.state.value.error!!.retryable)
        assertTrue(holder.state.value.togglingIds.isEmpty())
        assertEquals(listOf("alpha", "beta"), holder.state.value.items.map { it.id })
    }

    @Test
    fun deleteSuccessRemovesItem() = runTest {
        val fake = source()
        val holder = holder(fake)
        holder.delete(alpha)
        advanceUntilIdle()

        assertEquals(listOf("beta"), holder.state.value.items.map { it.id })
        assertNull(holder.state.value.error)
    }

    @Test
    fun setEnabledTogglesItemAndClearsBusy() = runTest {
        val fake = source()
        val holder = holder(fake)
        holder.setEnabled(alpha, true)
        advanceUntilIdle()

        assertEquals(listOf("alpha" to true), fake.enabledCalls)
        assertTrue(holder.state.value.items.first { it.id == "alpha" }.globalEnabled)
        assertTrue(holder.state.value.togglingIds.isEmpty())
        assertFalse(holder.state.value.busy)
    }

    @Test
    fun setEnabledFailureSetsError() = runTest {
        val fake = source()
        fake.enabledResult = false
        val holder = holder(fake)
        holder.setEnabled(beta, false)
        advanceUntilIdle()

        assertNotNull(holder.state.value.error)
        assertTrue(holder.state.value.togglingIds.isEmpty())
    }

    @Test
    fun queryFiltersVisibleItems() = runTest {
        val holder = holder(source())
        holder.setQuery("web")
        assertEquals(listOf("beta"), holder.state.value.visibleItems.map { it.id })
        assertEquals(2, holder.state.value.items.size)

        holder.setQuery("alpha")
        assertEquals(listOf("alpha"), holder.state.value.visibleItems.map { it.id })

        holder.setQuery("")
        assertEquals(listOf("alpha", "beta"), holder.state.value.visibleItems.map { it.id })
    }

    @Test
    fun concurrentRefreshesCoalesceIntoOneFollowUp() = runTest {
        val fake = source()
        val holder = holder(fake)
        val gate = CompletableDeferred<Unit>()
        fake.listGate = gate

        val first = async { holder.refresh() }
        val second = async { holder.refresh() }
        val third = async { holder.refresh() }
        advanceUntilIdle()
        assertEquals(1, fake.listCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()
        third.await()

        assertEquals(2, fake.listCalls)
        assertEquals(1, fake.maxActiveListCalls)
        fake.listGate = null
    }

    @Test
    fun installFailureSetsInstallErrorAndDismissClearsIt() = runTest {
        val fake = source()
        fake.installResult = SkillArchiveResult.Failure(SkillArchiveError.NotZip)
        val holder = holder(fake)

        val result = holder.install(byteArrayOf(1, 2, 3), "broken.zip")
        advanceUntilIdle()

        assertTrue(result is SkillArchiveResult.Failure)
        val error = holder.state.value.installError
        assertNotNull(error)
        assertFalse(error!!.retryable)
        assertFalse(holder.state.value.installing)

        holder.dismissInstallError()
        assertNull(holder.state.value.installError)
    }

    @Test
    fun onResumeSyncsChangedSkillsAndReloads() = runTest {
        val fake = source()
        val holder = holder(fake)
        holder.onResume()
        advanceUntilIdle()

        assertEquals(1, fake.syncChangedCalls)
        assertEquals(1, fake.listCalls)
    }

    @Test
    fun uninstallDelegatesToDelete() = runTest {
        val fake = source()
        val holder = holder(fake)
        holder.uninstall(beta)
        advanceUntilIdle()

        assertEquals(listOf("beta"), fake.deletedIds)
        assertEquals(listOf("alpha"), holder.state.value.items.map { it.id })
    }
}
