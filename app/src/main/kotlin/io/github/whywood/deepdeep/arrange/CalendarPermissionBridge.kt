package io.github.whywood.deepdeep.arrange

import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

object CalendarPermissionBridge {
    private const val EXTRA_REQUEST_ID = "io.github.whywood.deepdeep.arrange.CalendarPermissionRequest.REQUEST_ID"
    private const val EXTRA_PERMISSION = "io.github.whywood.deepdeep.arrange.CalendarPermissionRequest.PERMISSION"
    private const val TIMEOUT_MILLIS = 60_000L

    private val requestIds = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()

    suspend fun request(context: Context, permission: String): Boolean {
        val requestId = requestIds.incrementAndGet()
        val result = CompletableDeferred<Boolean>()
        pending[requestId] = result
        val intent = Intent(context, CalendarPermissionActivity::class.java)
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_PERMISSION, permission)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
        if (!started) {
            pending.remove(requestId)
            result.complete(false)
            return false
        }
        return try {
            withTimeoutOrNull(TIMEOUT_MILLIS) { result.await() } ?: false
        } finally {
            pending.remove(requestId)?.complete(false)
        }
    }

    internal fun requestId(intent: Intent): Long = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)

    internal fun permission(intent: Intent): String? = intent.getStringExtra(EXTRA_PERMISSION)

    internal fun complete(requestId: Long, granted: Boolean) {
        pending.remove(requestId)?.complete(granted)
    }
}
