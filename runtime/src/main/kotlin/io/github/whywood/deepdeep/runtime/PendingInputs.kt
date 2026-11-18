package io.github.whywood.deepdeep.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PendingInputs {
    data class Snapshot(
        val steering: List<String>,
        val followUp: List<String>,
    )

    private val lock = Any()
    private val steering = ArrayDeque<String>()
    private val followUp = ArrayDeque<String>()
    private val pendingSteer = MutableStateFlow(false)
    internal val steeringPending: StateFlow<Boolean> get() = pendingSteer

    fun enqueue(kind: InputQueueKind, text: String) {
        synchronized(lock) {
            when (kind) {
                InputQueueKind.STEER -> steering.addLast(text)
                InputQueueKind.FOLLOW_UP -> followUp.addLast(text)
            }
            pendingSteer.value = steering.isNotEmpty()
        }
    }

    fun discard(kind: InputQueueKind, text: String): Boolean = synchronized(lock) {
        val removed = when (kind) {
            InputQueueKind.STEER -> steering.remove(text)
            InputQueueKind.FOLLOW_UP -> followUp.remove(text)
        }
        pendingSteer.value = steering.isNotEmpty()
        removed
    }

    fun admit(text: String): InputQueueKind? = synchronized(lock) {
        when {
            steering.isNotEmpty() -> {
                if (steering.first() != text) return@synchronized null
                steering.removeFirst()
                pendingSteer.value = steering.isNotEmpty()
                InputQueueKind.STEER
            }

            followUp.isNotEmpty() && followUp.first() == text -> {
                followUp.removeFirst()
                InputQueueKind.FOLLOW_UP
            }

            else -> null
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(steering.toList(), followUp.toList())
    }

    fun drain(): List<QueuedInput> = synchronized(lock) {
        buildList {
            while (steering.isNotEmpty()) add(QueuedInput(InputQueueKind.STEER, steering.removeFirst()))
            while (followUp.isNotEmpty()) add(QueuedInput(InputQueueKind.FOLLOW_UP, followUp.removeFirst()))
            pendingSteer.value = false
        }
    }

    fun isEmpty(): Boolean = synchronized(lock) { steering.isEmpty() && followUp.isEmpty() }
}
