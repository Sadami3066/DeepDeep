package io.github.whywood.deepdeep.ui.onboarding

enum class OnboardingGate {
    WAIT,
    SHOW,
    SKIP,
}

fun resolveOnboardingGate(
    completed: Boolean,
    started: Boolean,
    runtimeReady: Boolean,
    hasCredentials: Boolean,
    hasSessions: Boolean,
): OnboardingGate = when {
    completed -> OnboardingGate.SKIP
    started -> OnboardingGate.SHOW
    hasCredentials || hasSessions -> OnboardingGate.SKIP
    runtimeReady -> OnboardingGate.SHOW
    else -> OnboardingGate.WAIT
}
