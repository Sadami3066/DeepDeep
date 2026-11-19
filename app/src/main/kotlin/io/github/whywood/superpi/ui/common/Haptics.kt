package io.github.whywood.superpi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.whywood.superpi.design.LocalDdHapticsEnabled

val LocalHapticsEnabled = LocalDdHapticsEnabled

@Composable
fun rememberHapticFeedback(): (HapticFeedbackType) -> Unit {
    val enabled = LocalHapticsEnabled.current
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic) {
        { type -> if (enabled) haptic.performHapticFeedback(type) }
    }
}
