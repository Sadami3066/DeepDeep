package io.github.whywood.deepdeep.runtime.memory

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryRollupTest {
    private lateinit var directory: File
    private lateinit var store: MemoryStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("deepdeep-rollup").toFile()
        store = MemoryStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun rollsPastDaysIntoMonthOnce() = runTest {
        store.ensure()
        store.appendDayBlock("2026-11-06", "- 事件 A")
        var calls = 0
        val rollup = MemoryRollup(store, this, { calls++; "- 摘要" }, today = { "2026-11-07" })

        rollup.runIfDue()

        assertTrue(store.monthHasDay("2026-11-06"))
        assertEquals(1, calls)

        rollup.runIfDue()

        assertEquals(1, calls)
    }

    @Test
    fun modelFailureLeavesDayForNextRun() = runTest {
        store.ensure()
        store.appendDayBlock("2026-11-06", "- 事件 A")
        var available = false
        val rollup = MemoryRollup(
            store,
            this,
            { if (available) "- 摘要" else null },
            today = { "2026-11-07" },
        )

        rollup.runIfDue()
        assertFalse(store.monthHasDay("2026-11-06"))

        available = true
        rollup.runIfDue()
        assertTrue(store.monthHasDay("2026-11-06"))
    }

    @Test
    fun skipOutputIsNotWritten() = runTest {
        store.ensure()
        store.appendDayBlock("2026-11-06", "- 闲聊")
        val rollup = MemoryRollup(store, this, { "SKIP" }, today = { "2026-11-07" })

        rollup.runIfDue()

        assertTrue(store.monthHasDay("2026-11-06"))
        val month = store.readMonth("2026-11")!!
        assertFalse(month.contains("SKIP"))
    }

    @Test
    fun rollsCompletedMonthIntoYear() = runTest {
        store.ensure()
        store.appendDayBlock("2026-10-31", "- 十月的事")
        var calls = 0
        val rollup = MemoryRollup(store, this, { calls++; "- 摘要" }, today = { "2026-12-01" })

        rollup.runIfDue()

        assertTrue(store.yearHasMonth("2026-10"))
        assertEquals(2, calls)
        assertTrue(store.readYear("2026")!!.contains("## 2026-10"))
    }

    @Test
    fun keepsEmptyDayMarkerToAvoidRetryLoop() = runTest {
        store.ensure()
        store.appendDayBlock("2026-11-06", "- 只有标记")
        File(directory, "daily/2026/11/2026-11/2026-11-05.md").apply {
            parentFile?.mkdirs()
            writeText("")
        }
        val rollup = MemoryRollup(store, this, { null }, today = { "2026-11-07" })

        rollup.runIfDue()

        assertTrue(store.monthHasDay("2026-11-05"))
    }
}
