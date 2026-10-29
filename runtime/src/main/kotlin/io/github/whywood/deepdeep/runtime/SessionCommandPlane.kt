package io.github.whywood.deepdeep.runtime

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SessionCommandPlane {
    class SessionState internal constructor() {
        internal val lock = Any()

        @Volatile
        var epoch: Long = 0
            internal set

        @Volatile
        var runId: String? = null
            internal set

        @Volatile
        var runStartedAt: Long = 0
            internal set

        @Volatile
        var stopRequested: Boolean = false
            internal set

        @Volatile
        var terminalEmitted: Boolean = false
            internal set

        @Volatile
        var busy: Boolean = false
            internal set

        @Volatile
        var turnSource: TurnSource = TurnSource.USER
            internal set

        internal var reconfigureOwner: Long = 0
        internal var pendingReconfigure: Boolean = false
    }

    data class Start(val runId: String, val epoch: Long)

    data class Stop(val runId: String, val epoch: Long)

    data class Finish(val released: Boolean, val reconfigure: Boolean)

    data class Snapshot(
        val runId: String?,
        val runStartedAt: Long,
        val epoch: Long,
        val busy: Boolean,
        val stopRequested: Boolean,
        val terminalEmitted: Boolean,
        val turnSource: TurnSource,
        val reconfiguring: Boolean,
    )

    sealed interface Delivery {
        data class Started(val start: Start) : Delivery

        data class Injected(val runId: String) : Delivery
    }

    private val states = ConcurrentHashMap<String, SessionState>()
    private val reconfigureTokens = AtomicLong()

    fun attach(sessionId: String): SessionState = states.computeIfAbsent(sessionId) { SessionState() }

    fun detach(sessionId: String, keepReconfigure: Boolean = false) {
        val state = states[sessionId] ?: return
        synchronized(state.lock) {
            state.epoch += 1
            state.runId = null
            state.runStartedAt = 0
            state.busy = false
            state.stopRequested = false
            state.terminalEmitted = false
            if (!keepReconfigure) {
                state.reconfigureOwner = 0
                state.pendingReconfigure = false
            }
        }
    }

    fun forget(sessionId: String) {
        states.remove(sessionId)
    }

    fun snapshot(sessionId: String): Snapshot? {
        val state = states[sessionId] ?: return null
        synchronized(state.lock) {
            return state.snapshot()
        }
    }

    fun busySessionIds(): Set<String> =
        states.entries.filter { it.value.busy }.mapTo(mutableSetOf()) { it.key }

    fun begin(sessionId: String, source: TurnSource): Start? = start(sessionId, source, null)

    fun beginIfEpoch(sessionId: String, source: TurnSource, epoch: Long): Start? =
        start(sessionId, source, epoch)

    fun deliver(sessionId: String, source: TurnSource, streaming: () -> Boolean): Delivery? {
        val state = states[sessionId] ?: return null
        synchronized(state.lock) {
            if (state.reconfigureOwner != 0L) return null
            if (state.busy || streaming()) return Delivery.Injected(state.runId.orEmpty())
            return Delivery.Started(state.startLocked(source))
        }
    }

    fun finish(sessionId: String, runId: String): Finish {
        val state = states[sessionId] ?: return Finish(false, false)
        synchronized(state.lock) {
            if (state.runId != runId) return Finish(false, false)
            state.busy = false
            state.runId = null
            state.runStartedAt = 0
            state.stopRequested = false
            state.terminalEmitted = false
            val reconfigure = state.pendingReconfigure
            state.pendingReconfigure = false
            return Finish(true, reconfigure)
        }
    }

    fun claimTerminal(sessionId: String, runId: String): Boolean {
        val state = states[sessionId] ?: return false
        synchronized(state.lock) {
            if (state.runId != runId || state.terminalEmitted) return false
            state.terminalEmitted = true
            return true
        }
    }

    fun stop(sessionId: String, emitStopped: (Stop) -> Unit): Stop? {
        val state = states[sessionId] ?: return null
        synchronized(state.lock) {
            state.epoch += 1
            val runId = state.runId ?: return null
            if (state.stopRequested) return null
            state.stopRequested = true
            val stop = Stop(runId, state.epoch)
            if (!state.terminalEmitted) {
                state.terminalEmitted = true
                emitStopped(stop)
            }
            return stop
        }
    }

    fun claimReconfigure(sessionId: String): Long {
        val state = states[sessionId] ?: return 0
        synchronized(state.lock) {
            if (state.reconfigureOwner != 0L || state.busy) {
                state.pendingReconfigure = true
                return 0
            }
            val token = reconfigureTokens.incrementAndGet()
            state.reconfigureOwner = token
            state.pendingReconfigure = false
            return token
        }
    }

    fun ownsReconfigure(sessionId: String, token: Long): Boolean {
        val state = states[sessionId] ?: return false
        synchronized(state.lock) {
            return state.reconfigureOwner == token
        }
    }

    fun releaseReconfigure(sessionId: String, token: Long): Boolean {
        val state = states[sessionId] ?: return false
        synchronized(state.lock) {
            if (state.reconfigureOwner != token) return false
            state.reconfigureOwner = 0
            val pending = state.pendingReconfigure
            state.pendingReconfigure = false
            return pending
        }
    }

    private fun start(sessionId: String, source: TurnSource, requiredEpoch: Long?): Start? {
        val state = states[sessionId] ?: return null
        synchronized(state.lock) {
            if (requiredEpoch != null && state.epoch != requiredEpoch) return null
            if (state.busy || state.reconfigureOwner != 0L) return null
            return state.startLocked(source)
        }
    }

    private fun SessionState.startLocked(source: TurnSource): Start {
        val runId = UUID.randomUUID().toString()
        this.runId = runId
        runStartedAt = System.currentTimeMillis()
        stopRequested = false
        terminalEmitted = false
        busy = true
        turnSource = source
        return Start(runId, epoch)
    }

    private fun SessionState.snapshot(): Snapshot = Snapshot(
        runId = runId,
        runStartedAt = runStartedAt,
        epoch = epoch,
        busy = busy,
        stopRequested = stopRequested,
        terminalEmitted = terminalEmitted,
        turnSource = turnSource,
        reconfiguring = reconfigureOwner != 0L,
    )
}
