package io.github.whywood.deepdeep.plugin.manager

internal sealed interface RecoveryDecision {
    data object Continue : RecoveryDecision

    data object RetryRequired : RecoveryDecision

    data class AutoDisable(val interrupted: Boolean) : RecoveryDecision
}

internal object PluginRecoveryPolicy {
    const val CRASH_LIMIT: Int = 2

    fun afterInterruptedActivation(attempts: Int): RecoveryDecision =
        if (attempts >= CRASH_LIMIT) {
            RecoveryDecision.AutoDisable(interrupted = true)
        } else {
            RecoveryDecision.RetryRequired
        }

    fun afterStartup(attempts: Int): RecoveryDecision =
        if (attempts >= CRASH_LIMIT) {
            RecoveryDecision.AutoDisable(interrupted = false)
        } else {
            RecoveryDecision.Continue
        }
}
