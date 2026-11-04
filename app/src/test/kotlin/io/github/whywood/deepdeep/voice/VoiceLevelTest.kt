package io.github.whywood.deepdeep.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLevelTest {
    @Test
    fun floorDbMapsToSilence() {
        assertEquals(0f, levelForDb(PCM_LEVEL_FLOOR_DB), 0.0001f)
        assertEquals(0f, levelForDb(PCM_LEVEL_FLOOR_DB - 20), 0.0001f)
    }

    @Test
    fun ceilingDbMapsToFullLevel() {
        assertEquals(1f, levelForDb(PCM_LEVEL_CEILING_DB), 0.0001f)
        assertEquals(1f, levelForDb(PCM_LEVEL_CEILING_DB + 10), 0.0001f)
    }

    @Test
    fun louderDbProducesHigherLevel() {
        val quiet = levelForDb(-45.0)
        val medium = levelForDb(-35.0)
        val loud = levelForDb(-25.0)
        assertTrue(quiet < medium)
        assertTrue(medium < loud)
    }

    @Test
    fun silentPcmBufferIsSilence() {
        assertEquals(0f, pcmBufferLevel(ByteArray(1280)), 0.0001f)
    }

    @Test
    fun tinyPcmBufferIsSilence() {
        assertEquals(0f, pcmBufferLevel(ByteArray(0)), 0.0001f)
        assertEquals(0f, pcmBufferLevel(ByteArray(3)), 0.0001f)
    }

    @Test
    fun louderPcmBufferProducesHigherLevel() {
        val quiet = pcmBufferLevel(pcmOf(amplitude = 300))
        val loud = pcmBufferLevel(pcmOf(amplitude = 6000))
        assertTrue(quiet > 0f)
        assertTrue(loud > quiet)
        assertEquals(1f, pcmBufferLevel(pcmOf(amplitude = 32767)), 0.05f)
    }

    private fun pcmOf(amplitude: Int): ByteArray {
        val samples = 640
        val bytes = ByteArray(samples * 2)
        for (index in 0 until samples) {
            val value = if (index % 2 == 0) amplitude else -amplitude
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
