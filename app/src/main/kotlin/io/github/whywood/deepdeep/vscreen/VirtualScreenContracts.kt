package io.github.whywood.deepdeep.vscreen

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

sealed interface VirtualScreenState {
    data object Offline : VirtualScreenState

    data object Starting : VirtualScreenState

    data class Online(
        val displayId: Int,
        val width: Int,
        val height: Int,
        val backend: String,
        val frame: Bitmap? = null,
        val instanceId: String = java.util.UUID.randomUUID().toString(),
    ) : VirtualScreenState

    data class Failed(val message: String) : VirtualScreenState
}

data class VirtualScreenResult(
    val ok: Boolean,
    val message: String = "",
)

interface VirtualScreenController {
    val state: StateFlow<VirtualScreenState>

    suspend fun start(): VirtualScreenResult

    suspend fun stop()

    suspend fun launch(packageName: String): VirtualScreenResult

    suspend fun tap(x: Int, y: Int): VirtualScreenResult

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): VirtualScreenResult

    suspend fun key(name: String): VirtualScreenResult

    suspend fun text(value: String): VirtualScreenResult

    suspend fun screenshot(): ByteArray?
}
