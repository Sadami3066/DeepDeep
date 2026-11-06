package io.github.whywood.deepdeep.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.OnboardingPreferences
import io.github.whywood.deepdeep.ui.onboarding.OnboardingGate
import io.github.whywood.deepdeep.ui.onboarding.OnboardingScreen
import io.github.whywood.deepdeep.ui.onboarding.OnboardingSplash
import io.github.whywood.deepdeep.ui.onboarding.resolveOnboardingGate

@Composable
internal fun AppOnboardingGate(
    graph: AppDependencies,
    runtimeReady: Boolean,
    hasCredentials: Boolean,
    hasSessions: Boolean,
    onFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { OnboardingPreferences(context) }
    var decision by rememberSaveable {
        mutableIntStateOf(
            when {
                store.isCompleted() -> ONBOARDING_DECISION_CHAT
                store.isStarted() -> ONBOARDING_DECISION_ONBOARDING
                else -> ONBOARDING_DECISION_UNDECIDED
            },
        )
    }
    LaunchedEffect(decision, runtimeReady, hasSessions, hasCredentials) {
        if (decision != ONBOARDING_DECISION_UNDECIDED) return@LaunchedEffect
        when (
            resolveOnboardingGate(
                completed = store.isCompleted(),
                started = store.isStarted(),
                runtimeReady = runtimeReady,
                hasCredentials = hasCredentials,
                hasSessions = hasSessions,
            )
        ) {
            OnboardingGate.WAIT -> Unit
            OnboardingGate.SHOW -> {
                store.markStarted()
                decision = ONBOARDING_DECISION_ONBOARDING
            }
            OnboardingGate.SKIP -> {
                store.markCompleted()
                decision = ONBOARDING_DECISION_CHAT
            }
        }
    }
    when (decision) {
        ONBOARDING_DECISION_CHAT -> content()
        ONBOARDING_DECISION_ONBOARDING -> OnboardingScreen(
            graph = graph,
            onFinished = {
                decision = ONBOARDING_DECISION_CHAT
                onFinished()
            },
        )
        else -> OnboardingSplash()
    }
}

private const val ONBOARDING_DECISION_UNDECIDED = 0
private const val ONBOARDING_DECISION_CHAT = 1
private const val ONBOARDING_DECISION_ONBOARDING = 2
