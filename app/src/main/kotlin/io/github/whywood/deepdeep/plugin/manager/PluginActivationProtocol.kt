package io.github.whywood.deepdeep.plugin.manager

import kotlinx.coroutines.CancellationException

internal data class ActivationCandidate(
    val pluginId: String,
    val version: String,
    val fromVersion: String?,
    val payload: Any? = null,
)

internal sealed interface ActivationOutcome {
    data class Activated(val version: String) : ActivationOutcome

    data object Skipped : ActivationOutcome

    data class Failed(val reason: String?) : ActivationOutcome
}

internal class PluginActivationProtocol(
    private val prepare: suspend (String) -> ActivationCandidate?,
    private val probe: suspend (ActivationCandidate) -> String?,
    private val commit: suspend (ActivationCandidate) -> Boolean,
    private val swap: suspend (ActivationCandidate) -> String?,
    private val discard: suspend (ActivationCandidate, String?) -> Unit,
    private val abort: suspend (ActivationCandidate, String?) -> Unit,
) {
    suspend fun activate(pluginId: String): ActivationOutcome {
        val candidate = try {
            prepare(pluginId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return ActivationOutcome.Failed(error.message ?: error.javaClass.simpleName)
        } ?: return ActivationOutcome.Skipped
        val probeFailure = try {
            probe(candidate)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error.message ?: error.javaClass.simpleName
        }
        if (probeFailure != null) {
            runCatching { discard(candidate, probeFailure) }
            return ActivationOutcome.Failed(probeFailure)
        }
        val committed = try {
            commit(candidate)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            runCatching { abort(candidate, error.message) }
            return ActivationOutcome.Failed(error.message ?: error.javaClass.simpleName)
        }
        if (!committed) {
            runCatching { abort(candidate, null) }
            return ActivationOutcome.Failed(null)
        }
        val swapFailure = try {
            swap(candidate)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            error.message ?: error.javaClass.simpleName
        }
        return if (swapFailure == null) {
            ActivationOutcome.Activated(candidate.version)
        } else {
            ActivationOutcome.Failed(swapFailure)
        }
    }
}
