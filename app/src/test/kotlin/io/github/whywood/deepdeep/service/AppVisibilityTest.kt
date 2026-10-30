package io.github.whywood.deepdeep.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisibilityTest {
    @Test
    fun tracksStartedActivitiesWithoutGoingNegative() {
        assertFalse(AppVisibility.foreground)
        AppVisibility.activityStopped()
        assertFalse(AppVisibility.foreground)
        AppVisibility.activityStarted()
        assertTrue(AppVisibility.foreground)
        AppVisibility.activityStarted()
        assertTrue(AppVisibility.foreground)
        AppVisibility.activityStopped()
        assertTrue(AppVisibility.foreground)
        AppVisibility.activityStopped()
        assertFalse(AppVisibility.foreground)
        AppVisibility.activityStopped()
        assertFalse(AppVisibility.foreground)
    }
}
