package io.github.whywood.deepdeep.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayDraftStoreTest {
    private class FakePersistence : OverlayDraftPersistence {
        var value: String? = null

        override fun read(): String? = value

        override fun write(value: String?) {
            this.value = value
        }
    }

    @Test
    fun restoresSavedDraft() {
        val persistence = FakePersistence()
        OverlayDraftStore(persistence).save("half typed message")
        assertEquals("half typed message", OverlayDraftStore(persistence).read())
    }

    @Test
    fun readsEmptyWhenNothingSaved() {
        assertEquals("", OverlayDraftStore(FakePersistence()).read())
    }

    @Test
    fun savingBlankClearsDraft() {
        val persistence = FakePersistence()
        val store = OverlayDraftStore(persistence)
        store.save("text")
        store.save("")
        assertEquals(null, persistence.value)
        assertEquals("", store.read())
    }

    @Test
    fun clearDropsDraft() {
        val persistence = FakePersistence()
        val store = OverlayDraftStore(persistence)
        store.save("text")
        store.clear()
        assertEquals("", store.read())
    }
}
