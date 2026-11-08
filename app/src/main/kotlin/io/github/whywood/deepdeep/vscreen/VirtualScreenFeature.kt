package io.github.whywood.deepdeep.vscreen

import android.content.Context
import io.github.whywood.deepdeep.sdk.agent.AgentToolResult
import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.data.LocalizedContext
import io.github.whywood.deepdeep.privilege.PrivilegeController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal const val VIRTUAL_SCREEN_TOOL = "virtual_screen"

internal const val VIRTUAL_SCREEN_DESCRIPTION =
    "Manage a virtual screen that runs apps in the background without taking over the visible phone screen: start, stop or check it. Returns the screen state, its size, a rebuild notice or failure details."

internal const val VIRTUAL_SCREEN_GUIDELINE =
    "virtual_screen only manages the virtual screen lifecycle; use the screen_* tools to operate on it."

class VirtualScreenFeature internal constructor(
    private val screen: VirtualScreenAccess,
) {
    val online: Boolean get() = screen.state.value is VirtualScreenState.Online

    suspend fun start() {
        screen.start()
    }

    suspend fun stop() {
        screen.stop()
    }

    fun tools(): List<CodingTool> = virtualScreenTools(screen)

    companion object {
        fun install(
            context: Context,
            privilege: PrivilegeController,
            adb: AdbBootstrap,
        ): VirtualScreenFeature {
            val appContext = context.applicationContext
            val localizedContext = LocalizedContext(appContext)
            val engine = VirtualScreenRuntime.install(
                VirtualScreenEngine(
                    localizedContext,
                    PrivilegedLauncher(appContext, adb, privilege),
                ),
            )
            return VirtualScreenFeature(EngineScreen(engine))
        }
    }
}

internal interface VirtualScreenAccess {
    val state: StateFlow<VirtualScreenState>
    val reconnecting: Boolean get() = false
    val lastFailure: VirtualScreenFailureInfo?
    val lastTransportNotice: String?

    suspend fun start(): VirtualScreenResult

    suspend fun stop()
}

internal class EngineScreen(private val engine: VirtualScreenEngine) : VirtualScreenAccess {
    override val reconnecting: Boolean get() = engine.reconnecting
    override val state: StateFlow<VirtualScreenState>
        get() = engine.state

    override val lastFailure: VirtualScreenFailureInfo?
        get() = engine.lastFailure

    override val lastTransportNotice: String?
        get() = engine.lastTransportNotice

    override suspend fun start(): VirtualScreenResult = engine.start()

    override suspend fun stop() = engine.stop()
}

private val virtualScreenParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("action") {
            put("type", "string")
            put(
                "description",
                "start opens the background screen, stop closes it and its apps, status reports whether it is running.",
            )
            putJsonArray("enum") {
                add(JsonPrimitive("start"))
                add(JsonPrimitive("stop"))
                add(JsonPrimitive("status"))
            }
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("action")) })
}

internal fun virtualScreenTools(screen: VirtualScreenAccess): List<CodingTool> =
    listOf(virtualScreenTool(screen))

internal fun virtualScreenTool(screen: VirtualScreenAccess): CodingTool = CodingTool(
    name = VIRTUAL_SCREEN_TOOL,
    description = VIRTUAL_SCREEN_DESCRIPTION,
    parameters = virtualScreenParameters,
    label = VIRTUAL_SCREEN_TOOL,
    promptGuidelines = listOf(VIRTUAL_SCREEN_GUIDELINE),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        virtualScreenResult {
            require(args.keys.all { it == "action" }) { "virtual_screen only accepts action; choose target with the screen_* tools" }
            when (val action = args.requiredString("action").lowercase()) {
                "start" -> {
                    if (screen.start().ok) VirtualScreenRuntime.notifyToolStarted()
                    textResult(
                        stateJson(screen).toString(),
                        isError = screen.state.value is VirtualScreenState.Failed,
                    )
                }

                "stop" -> {
                    screen.stop()
                    textResult(stateJson(screen).toString())
                }

                "status" -> textResult(stateJson(screen).toString())

                else -> throw IllegalArgumentException("action must be start, stop or status")
            }
        }
    },
)

private fun stateJson(screen: VirtualScreenAccess): JsonObject = buildJsonObject {
    put("reconnecting", screen.reconnecting)
    screen.lastTransportNotice?.let { put("notice", it) }
    when (val state = screen.state.value) {
        VirtualScreenState.Offline -> put("state", "offline")
        VirtualScreenState.Starting -> put("state", "starting")
        is VirtualScreenState.Failed -> {
            put("state", "failed")
            val failure = screen.lastFailure
            val category = failureCategory(failure?.category)
            putJsonObject("failure") {
                put("category", category)
                put("message", failure?.message ?: state.message)
                put("hint", if (screen.reconnecting) "Reconnecting automatically; check status again shortly" else failureHint(category))
            }
        }

        is VirtualScreenState.Online -> {
            put("state", "online")
            put("width", state.width)
            put("height", state.height)
        }
    }
}

private fun failureCategory(category: VirtualScreenFailureCategory?): String = when (category) {
    VirtualScreenFailureCategory.BACKEND -> "privilege"
    VirtualScreenFailureCategory.START -> "start"
    else -> "connection"
}

private fun failureHint(category: String): String = when (category) {
    "privilege" -> "Ask the user to enable wireless debugging (ADB) or root in permission settings, then retry"
    "start" -> "Retry start now"
    else -> "Automatic reconnect did not recover; call virtual_screen(action=start), then screen_launch and observe again"
}

private suspend fun virtualScreenResult(block: suspend () -> AgentToolResult): AgentToolResult = try {
    block()
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "Virtual screen action failed", isError = true)
}

private fun JsonObject.requiredString(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Missing required argument: $name")
