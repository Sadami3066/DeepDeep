package io.github.whywood.deepdeep.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingGateTest {
    @Test
    fun completedOnboardingIsSkippedEvenBeforeRuntimeIsReady() {
        assertEquals(
            OnboardingGate.SKIP,
            resolveOnboardingGate(
                completed = true,
                started = true,
                runtimeReady = false,
                hasCredentials = false,
                hasSessions = false,
            ),
        )
    }

    @Test
    fun existingCredentialsSkipOnboardingBeforeRuntimeIsReady() {
        assertEquals(
            OnboardingGate.SKIP,
            resolveOnboardingGate(
                completed = false,
                started = false,
                runtimeReady = false,
                hasCredentials = true,
                hasSessions = false,
            ),
        )
    }

    @Test
    fun existingSessionsSkipOnboarding() {
        assertEquals(
            OnboardingGate.SKIP,
            resolveOnboardingGate(
                completed = false,
                started = false,
                runtimeReady = true,
                hasCredentials = false,
                hasSessions = true,
            ),
        )
    }

    @Test
    fun startedOnboardingResumesEvenWhenRuntimeIsNotReady() {
        assertEquals(
            OnboardingGate.SHOW,
            resolveOnboardingGate(
                completed = false,
                started = true,
                runtimeReady = false,
                hasCredentials = false,
                hasSessions = true,
            ),
        )
    }

    @Test
    fun firstInstallWaitsForRuntimeThenShowsOnboarding() {
        assertEquals(
            OnboardingGate.WAIT,
            resolveOnboardingGate(
                completed = false,
                started = false,
                runtimeReady = false,
                hasCredentials = false,
                hasSessions = false,
            ),
        )
        assertEquals(
            OnboardingGate.SHOW,
            resolveOnboardingGate(
                completed = false,
                started = false,
                runtimeReady = true,
                hasCredentials = false,
                hasSessions = false,
            ),
        )
    }
}
