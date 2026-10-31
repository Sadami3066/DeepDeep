package io.github.whywood.deepdeep.vscreen

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

internal enum class ResponseMatch {
    MATCHED,
    LATE,
    UNKNOWN,
}

internal class ResponseCorrelator(private val maxAbandonedIds: Int = MAX_ABANDONED_IDS) {
    private val abandonedIds = LinkedHashSet<Int>()

    @Volatile
    var pendingId: Int = NO_PENDING
        private set

    @Synchronized
    fun expect(id: Int) {
        pendingId = id
    }

    @Synchronized
    fun isPending(id: Int): Boolean = pendingId == id

    @Synchronized
    fun resolve(id: Int): ResponseMatch = when {
        pendingId == id -> {
            pendingId = NO_PENDING
            ResponseMatch.MATCHED
        }
        abandonedIds.remove(id) -> ResponseMatch.LATE
        else -> ResponseMatch.UNKNOWN
    }

    @Synchronized
    fun abandon(id: Int) {
        if (pendingId == id) pendingId = NO_PENDING
        if (id <= 0) return
        if (abandonedIds.size >= maxAbandonedIds) abandonedIds.remove(abandonedIds.first())
        abandonedIds.add(id)
    }

    @Synchronized
    fun reset() {
        pendingId = NO_PENDING
        abandonedIds.clear()
    }

    companion object {
        const val NO_PENDING = 0
        private const val MAX_ABANDONED_IDS = 64
    }
}

internal fun isPongResponse(json: JsonObject): Boolean =
    json.booleanField("pong") == true || (json.intField("id") ?: 0) < 0

internal class ScreenshotGate {
    private var epoch = NO_EPOCH
    private var armedEpoch = NO_EPOCH
    private var waiter: CompletableDeferred<ByteArray>? = null

    @Synchronized
    fun open(): Long {
        epoch += 1
        armedEpoch = NO_EPOCH
        waiter = CompletableDeferred()
        return epoch
    }

    @Synchronized
    fun arm(epoch: Long): Boolean {
        if (this.epoch != epoch || armedEpoch != NO_EPOCH) return false
        armedEpoch = epoch
        return true
    }

    @Synchronized
    fun onFrame(bytes: ByteArray): Boolean {
        if (armedEpoch == NO_EPOCH || armedEpoch != epoch) return false
        val current = waiter ?: return false
        armedEpoch = NO_EPOCH
        return current.complete(bytes)
    }

    @Synchronized
    fun close(epoch: Long) {
        if (this.epoch != epoch) return
        armedEpoch = NO_EPOCH
        waiter = null
    }

    @Synchronized
    fun reset() {
        armedEpoch = NO_EPOCH
        waiter = null
    }

    suspend fun await(epoch: Long, timeoutMs: Long): ByteArray? {
        val current = synchronized(this) {
            if (this.epoch != epoch) null else waiter
        } ?: return null
        return withTimeoutOrNull(timeoutMs) { current.await() }
    }

    private companion object {
        const val NO_EPOCH = 0L
    }
}
