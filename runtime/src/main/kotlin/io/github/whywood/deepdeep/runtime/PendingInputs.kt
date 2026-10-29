package io.github.whywood.deepdeep.runtime

class PendingInputs {
    data class Snapshot(
        val steering: List<String>,
        val followUp: List<String>,
    )

    private val lock = Any()
    private val steering = ArrayDeque<String>()
    private val followUp = ArrayDeque<String>()

    fun enqueue(kind: InputQueueKind, text: String) {
        synchronized(lock) {
            when (kind) {
                InputQueueKind.STEER -> steering.addLast(text)
                InputQueueKind.FOLLOW_UP -> followUp.addLast(text)
            }
        }
    }

    fun discard(kind: InputQueueKind, text: String): Boolean = synchronized(lock) {
        when (kind) {
            InputQueueKind.STEER -> steering.remove(text)
            InputQueueKind.FOLLOW_UP -> followUp.remove(text)
        }
    }

    fun admit(text: String): InputQueueKind? = synchronized(lock) {
        when {
            steering.isNotEmpty() -> {
                if (steering.first() != text) return@synchronized null
                steering.removeFirst()
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
        }
    }

    fun isEmpty(): Boolean = synchronized(lock) { steering.isEmpty() && followUp.isEmpty() }
}
