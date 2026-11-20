package io.github.whywood.superpi.runtime

internal class RuntimeHandle(
    val engine: SessionEngine,
    val session: SessionHandle,
    val cwd: String,
    val state: SessionCommandPlane.SessionState,
) {
    val pending = PendingInputs()

    var unsubscribe: (() -> Unit)? = null

    @Volatile
    var lastActiveAt: Long = System.currentTimeMillis()

    @Volatile
    var activity: RuntimeActivity? = null

    fun touch(now: Long = System.currentTimeMillis()) {
        lastActiveAt = now
    }
}
