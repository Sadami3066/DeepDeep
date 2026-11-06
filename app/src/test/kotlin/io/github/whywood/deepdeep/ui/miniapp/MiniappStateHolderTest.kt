package io.github.whywood.deepdeep.ui.miniapp

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.MiniappCreation
import io.github.whywood.deepdeep.runtime.MiniappInvalid
import io.github.whywood.deepdeep.runtime.MiniappInvalidReason
import io.github.whywood.deepdeep.runtime.MiniappManifest
import io.github.whywood.deepdeep.runtime.MiniappScan
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MiniappStateHolderTest {
    @Test
    fun loadPublishesCreationsAndInvalidEntries() = runTest {
        val source = FakeMiniappSource()
        source.items += creation("heart", "Heart")
        source.invalidItems += invalid("broken")
        val holder = MiniappStateHolder(source, backgroundScope)

        holder.load()

        val state = holder.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(listOf("heart"), state.creations.map { it.manifest.id })
        assertEquals(listOf("broken"), state.invalid.map { it.id })
    }

    @Test
    fun loadFailureSetsRetryableError() = runTest {
        val source = FakeMiniappSource()
        source.scanFailure = IOException("boom")
        val holder = MiniappStateHolder(source, backgroundScope)

        holder.load()

        val state = holder.state.value
        assertFalse(state.loading)
        assertEquals(R.string.miniapp_error_scan_failed, state.error?.messageRes)
        assertTrue(state.error?.retryable == true)
        assertTrue(state.isEmpty)
    }

    @Test
    fun deleteFailureSetsErrorAndKeepsItems() = runTest {
        val source = FakeMiniappSource()
        source.items += creation("heart", "Heart")
        source.deleteFailure = IOException("read-only")
        val holder = MiniappStateHolder(source, backgroundScope)
        holder.load()

        holder.delete("heart")

        val state = holder.state.value
        assertEquals(R.string.miniapp_error_delete_failed, state.error?.messageRes)
        assertFalse(state.error?.retryable == true)
        assertEquals(listOf("heart"), state.creations.map { it.manifest.id })
        assertNull(state.busyId)
    }

    @Test
    fun deleteSuccessRescansAndDropsEntry() = runTest {
        val source = FakeMiniappSource()
        source.items += creation("heart", "Heart")
        val holder = MiniappStateHolder(source, backgroundScope)
        holder.load()
        assertEquals(1, source.scanCalls)

        holder.delete("heart")
        advanceUntilIdle()

        val state = holder.state.value
        assertEquals(listOf("heart"), source.deleted)
        assertEquals(2, source.scanCalls)
        assertTrue(state.creations.isEmpty())
        assertNull(state.error)
        assertNull(state.busyId)
    }

    @Test
    fun queryFiltersCreationsAndInvalidEntries() = runTest {
        val source = FakeMiniappSource()
        source.items += creation("heart", "Heart", description = "floating hearts")
        source.items += creation("notes", "Notes", description = "daily notes")
        source.invalidItems += invalid("old-thing")
        val holder = MiniappStateHolder(source, backgroundScope)
        holder.load()

        holder.setQuery("note")
        assertEquals(listOf("notes"), holder.state.value.filtered.creations.map { it.manifest.id })
        assertTrue(holder.state.value.filtered.invalid.isEmpty())

        holder.setQuery("old")
        assertTrue(holder.state.value.filtered.creations.isEmpty())
        assertEquals(listOf("old-thing"), holder.state.value.filtered.invalid.map { it.id })

        holder.setQuery("  ")
        assertEquals(2, holder.state.value.filtered.creations.size)
        assertEquals(1, holder.state.value.filtered.invalid.size)
    }

    @Test
    fun renameRescansAndClearsBusy() = runTest {
        val source = FakeMiniappSource()
        source.items += creation("heart", "Heart")
        source.renameResult = creation("heart", "Floating heart")
        val holder = MiniappStateHolder(source, backgroundScope)
        holder.load()
        assertEquals(1, source.scanCalls)

        holder.rename("heart", "Floating heart")
        advanceUntilIdle()

        assertEquals(listOf("heart" to "Floating heart"), source.renames)
        assertEquals(2, source.scanCalls)
        assertNull(holder.state.value.error)
        assertNull(holder.state.value.busyId)
    }

    @Test
    fun renameFailureSetsError() = runTest {
        val source = FakeMiniappSource()
        source.items += creation("heart", "Heart")
        source.renameFailure = IOException("disk full")
        val holder = MiniappStateHolder(source, backgroundScope)
        holder.load()

        holder.rename("heart", "Floating heart")

        assertEquals(R.string.chat_error_request_failed, holder.state.value.error?.messageRes)
        assertNull(holder.state.value.busyId)
    }

    @Test
    fun concurrentRefreshCoalescesIntoOneFollowUp() = runTest {
        val source = FakeMiniappSource()
        val holder = MiniappStateHolder(source, backgroundScope)
        holder.load()
        source.scanCalls = 0
        val gate = CompletableDeferred<Unit>()
        source.scanGate = gate

        val first = async { holder.refresh() }
        val second = async { holder.refresh() }
        val third = async { holder.refresh() }
        advanceUntilIdle()
        assertEquals(1, source.scanCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()
        third.await()

        assertEquals(2, source.scanCalls)
        assertNull(holder.state.value.error)
        source.scanGate = null
    }

    private fun creation(id: String, name: String, description: String = "") = MiniappCreation(
        manifest = MiniappManifest(id = id, name = name, description = description),
        directory = File("/miniapp/apps/$id"),
    )

    private fun invalid(id: String) = MiniappInvalid(
        id = id,
        directory = File("/miniapp/apps/$id"),
        reason = MiniappInvalidReason.MANIFEST_MISSING,
        detail = null,
    )

    private class FakeMiniappSource : MiniappDataSource {
        val items = mutableListOf<MiniappCreation>()
        val invalidItems = mutableListOf<MiniappInvalid>()
        var scanCalls = 0
        var scanFailure: Exception? = null
        var scanGate: CompletableDeferred<Unit>? = null
        var deleteFailure: Exception? = null
        var renameFailure: Exception? = null
        var renameResult: MiniappCreation? = null
        val deleted = mutableListOf<String>()
        val renames = mutableListOf<Pair<String, String>>()

        override suspend fun scan(): MiniappScan {
            scanCalls++
            scanGate?.await()
            scanFailure?.let { throw it }
            return MiniappScan(items.toList(), invalidItems.toList())
        }

        override suspend fun delete(id: String): Boolean {
            deleted += id
            deleteFailure?.let { throw it }
            items.removeAll { it.manifest.id == id }
            invalidItems.removeAll { it.id == id }
            return true
        }

        override suspend fun rename(id: String, name: String): MiniappCreation? {
            renames += id to name
            renameFailure?.let { throw it }
            return renameResult
        }
    }
}
