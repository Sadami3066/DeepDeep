package io.github.whywood.deepdeep.ui.common.market

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarketIconCachePruneTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `keeps cache under the limit`() {
        val dir = temporary.newFolder()
        val files = listOf("a", "b").mapIndexed { index, name -> icon(dir, name, 2, index) }

        pruneIconCache(dir, maxBytes = 8, keepBytes = 6)

        assertTrue(files.all { it.isFile })
    }

    @Test
    fun `prunes oldest files when the cache exceeds the limit`() {
        val dir = temporary.newFolder()
        val files = listOf("a", "b", "c").mapIndexed { index, name -> icon(dir, name, 4, index) }

        pruneIconCache(dir, maxBytes = 8, keepBytes = 6)

        assertFalse(files[0].isFile)
        assertFalse(files[1].isFile)
        assertTrue(files[2].isFile)
        assertEquals(4L, files.filter { it.isFile }.sumOf { it.length() })
    }

    @Test
    fun `prunes down to the keep budget when far over the limit`() {
        val dir = temporary.newFolder()
        val files = listOf("a", "b", "c", "d", "e").mapIndexed { index, name -> icon(dir, name, 4, index) }

        pruneIconCache(dir, maxBytes = 8, keepBytes = 6)

        assertTrue(files.take(4).none { it.isFile })
        assertTrue(files[4].isFile)
    }

    private fun icon(dir: File, name: String, size: Int, index: Int): File =
        File(dir, name).apply {
            writeBytes(ByteArray(size))
            setLastModified(1_000L + index)
        }
}
