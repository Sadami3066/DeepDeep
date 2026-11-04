package io.github.whywood.deepdeep.ui.common

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWaveHistoryTest {
    @Test
    fun startsWithSilentRowAtRest() {
        val history = VoiceWaveHistory(capacity = 6, intervalMs = 70L)
        assertEquals(List(6) { 0f }, history.snapshot())
        assertEquals(0f, history.fraction, 0.0001f)
    }

    @Test
    fun advanceBelowIntervalOnlyMovesFraction() {
        val history = VoiceWaveHistory(capacity = 3, intervalMs = 70L)
        assertFalse(history.advance(35L, 0.8f))
        assertEquals(List(3) { 0f }, history.snapshot())
        assertEquals(0.5f, history.fraction, 0.0001f)
    }

    @Test
    fun crossingIntervalPushesLatestVolumeToFront() {
        val history = VoiceWaveHistory(capacity = 3, intervalMs = 70L)
        assertTrue(history.advance(70L, 0.6f))
        assertEquals(listOf(0.6f, 0f, 0f), history.snapshot())
        assertEquals(0f, history.fraction, 0.0001f)
    }

    @Test
    fun oneFrameCanCrossSeveralIntervals() {
        val history = VoiceWaveHistory(capacity = 4, intervalMs = 70L)
        assertTrue(history.advance(150L, 0.4f))
        assertEquals(listOf(0.4f, 0.4f, 0f, 0f), history.snapshot())
        assertEquals(10f / 70f, history.fraction, 0.0001f)
    }

    @Test
    fun keepsOnlyNewestSamplesWithinCapacity() {
        val history = VoiceWaveHistory(capacity = 3, intervalMs = 70L)
        history.advance(70L, 0.1f)
        history.advance(70L, 0.5f)
        history.advance(70L, 0.9f)
        assertEquals(listOf(0.9f, 0.5f, 0.1f), history.snapshot())
        history.advance(70L, 0.2f)
        assertEquals(listOf(0.2f, 0.9f, 0.5f), history.snapshot())
    }

    @Test
    fun clampsVolumesToUnitRange() {
        val history = VoiceWaveHistory(capacity = 2, intervalMs = 70L)
        history.advance(70L, 2f)
        assertEquals(listOf(1f, 0f), history.snapshot())
        history.advance(70L, -1f)
        assertEquals(listOf(0f, 1f), history.snapshot())
    }

    @Test
    fun ignoresNonPositiveDeltas() {
        val history = VoiceWaveHistory(capacity = 2, intervalMs = 70L)
        assertFalse(history.advance(0L, 1f))
        assertFalse(history.advance(-5L, 1f))
        assertEquals(List(2) { 0f }, history.snapshot())
        assertEquals(0f, history.fraction, 0.0001f)
    }

    @Test
    fun resetRestoresSilentRow() {
        val history = VoiceWaveHistory(capacity = 3, intervalMs = 70L)
        history.advance(140L, 0.9f)
        history.reset()
        assertEquals(List(3) { 0f }, history.snapshot())
        assertEquals(0f, history.fraction, 0.0001f)
    }

    @Test
    fun silenceMapsToFaintDot() {
        assertEquals(0f, voiceWaveLevel(0f), 0.0001f)
        assertEquals(0f, voiceWaveLevel(VOICE_MIN_VOLUME), 0.0001f)
    }

    @Test
    fun fullVolumeMapsToFullAmplitude() {
        assertEquals(1f, voiceWaveLevel(1f), 0.0001f)
        assertEquals(1f, voiceWaveLevel(1.4f), 0.0001f)
    }

    @Test
    fun louderVolumeRaisesDotAndBoostsQuietLevels() {
        assertTrue(voiceWaveLevel(0.5f) > voiceWaveLevel(0.25f))
        assertTrue(voiceWaveLevel(0.5f) > (0.5f - VOICE_MIN_VOLUME) / (1f - VOICE_MIN_VOLUME))
    }

    @Test
    fun newestSampleStartsAtLeftAndOlderSamplesSitToTheRight() {
        assertEquals(0f, voiceWaveOffset(index = 0, fraction = 0f), 0.0001f)
        assertTrue(voiceWaveOffset(2, 0.5f) > voiceWaveOffset(1, 0.5f))
        assertTrue(voiceWaveOffset(1, 0.5f) > voiceWaveOffset(0, 0.5f))
    }

    @Test
    fun scrollingMovesSamplesToTheRight() {
        assertTrue(voiceWaveOffset(3, 0.9f) > voiceWaveOffset(3, 0.1f))
    }

    @Test
    fun scrollStaysContinuousAcrossSamplePush() {
        val beforePush = voiceWaveOffset(index = 3, fraction = 0.99f)
        val afterPush = voiceWaveOffset(index = 4, fraction = 0.01f)
        assertTrue(abs(beforePush - afterPush) < 0.05f)
    }
}
