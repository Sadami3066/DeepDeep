package io.github.whywood.deepdeep.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppScreenTransitionMotionTest {
    @Test
    fun popTargetReportsPreviousStackEntry() {
        assertNull(AppScreenTransition.popTarget(emptyList()))
        assertNull(AppScreenTransition.popTarget(listOf(AppDestination.Chat)))
        assertEquals(
            AppDestination.Chat,
            AppScreenTransition.popTarget(listOf(AppDestination.Chat, AppDestination.Settings)),
        )
        assertEquals(
            AppDestination.Settings,
            AppScreenTransition.popTarget(
                listOf(AppDestination.Chat, AppDestination.Settings, AppDestination.Providers),
            ),
        )
    }

    @Test
    fun forwardMovesNewPageInFromEndAndOldPageOutToStart() {
        val direction = 1
        assertEquals(1f, AppScreenTransition.translationFraction(0f, incoming = true, direction = direction), DELTA)
        assertEquals(0f, AppScreenTransition.translationFraction(1f, incoming = true, direction = direction), DELTA)
        assertEquals(0f, AppScreenTransition.translationFraction(0f, incoming = false, direction = direction), DELTA)
        assertEquals(-1f, AppScreenTransition.translationFraction(1f, incoming = false, direction = direction), DELTA)
    }

    @Test
    fun backReversesSharedAxisOffsets() {
        val direction = -1
        assertEquals(-1f, AppScreenTransition.translationFraction(0f, incoming = true, direction = direction), DELTA)
        assertEquals(0f, AppScreenTransition.translationFraction(1f, incoming = true, direction = direction), DELTA)
        assertEquals(0f, AppScreenTransition.translationFraction(0f, incoming = false, direction = direction), DELTA)
        assertEquals(1f, AppScreenTransition.translationFraction(1f, incoming = false, direction = direction), DELTA)
    }

    @Test
    fun sameLevelTransitionCrossfadesWithoutTranslation() {
        listOf(0f, 0.5f, 1f).forEach { settled ->
            assertEquals(0f, AppScreenTransition.translationFraction(settled, incoming = true, direction = 0), DELTA)
            assertEquals(0f, AppScreenTransition.translationFraction(settled, incoming = false, direction = 0), DELTA)
        }
    }

    @Test
    fun alphaFadesIncomingInAndOutgoingOut() {
        assertEquals(0f, AppScreenTransition.alpha(0f, incoming = true), DELTA)
        assertEquals(1f, AppScreenTransition.alpha(1f, incoming = true), DELTA)
        assertEquals(1f, AppScreenTransition.alpha(0f, incoming = false), DELTA)
        assertEquals(0f, AppScreenTransition.alpha(1f, incoming = false), DELTA)
    }

    @Test
    fun sharedAxisOffsetIsAQuarterOfThePage() {
        assertEquals(0.25f, AppScreenTransition.SHARED_AXIS_FRACTION, 0f)
        val ltr = AppScreenTransition.translationFraction(0f, incoming = true, direction = 1) *
            AppScreenTransition.SHARED_AXIS_FRACTION
        assertEquals(0.25f, ltr, DELTA)
        assertEquals(-0.25f, ltr * -1f, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
