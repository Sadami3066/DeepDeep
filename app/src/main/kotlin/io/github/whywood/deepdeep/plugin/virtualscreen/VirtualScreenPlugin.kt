package io.github.whywood.deepdeep.plugin.virtualscreen

import android.content.Context
import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.adb.AdbBootstrap
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.plugin.OfficialPlugin
import io.github.whywood.deepdeep.plugin.PluginAction
import io.github.whywood.deepdeep.plugin.PluginActionStyle
import io.github.whywood.deepdeep.plugin.PluginAvailability
import io.github.whywood.deepdeep.plugin.PluginDetailRow
import io.github.whywood.deepdeep.plugin.PluginKind
import io.github.whywood.deepdeep.plugin.PluginProgress
import io.github.whywood.deepdeep.plugin.PluginStatus
import io.github.whywood.deepdeep.plugin.PluginUi
import io.github.whywood.deepdeep.privilege.PrivilegeController
import io.github.whywood.deepdeep.vscreen.PrivilegedLauncher
import io.github.whywood.deepdeep.vscreen.VirtualScreenEngine
import io.github.whywood.deepdeep.vscreen.VirtualScreenFailureCategory
import io.github.whywood.deepdeep.vscreen.VirtualScreenFailureInfo
import io.github.whywood.deepdeep.vscreen.VirtualScreenResult
import io.github.whywood.deepdeep.vscreen.VirtualScreenRuntime
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
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

class VirtualScreenPlugin(
    private val context: Context,
    private val privilege: PrivilegeController,
    private val adb: AdbBootstrap,
) : OfficialPlugin {
    private val engine: VirtualScreenEngine = VirtualScreenRuntime.install(
        VirtualScreenEngine(
            context.applicationContext,
            PrivilegedLauncher(context.applicationContext, adb, privilege),
        ),
    )

    override val id: String = "virtual_screen"
    override val kind: PluginKind = PluginKind.INTERNAL
    override val icon: DdIcon = DdIcons.Monitor
    override val nameRes: Int = R.string.plugin_virtual_screen_name
    override val summaryRes: Int = R.string.plugin_virtual_screen_summary
    override val version: String = "1.1.0"
    override val sizeLabelRes: Int = R.string.plugin_virtual_screen_size

    override suspend fun availability(): PluginAvailability = virtualScreenAvailability(
        privilege.state.value.virtualScreenAvailable,
        string(R.string.plugin_virtual_screen_privilege_required),
    )

    override suspend fun install(progress: PluginProgress) = Unit

    override suspend fun uninstall() = Unit

    override suspend fun status(): PluginStatus = when (val state = engine.state.value) {
        VirtualScreenState.Offline -> PluginStatus.NotReady(string(R.string.plugin_virtual_screen_status_offline))
        VirtualScreenState.Starting -> PluginStatus.NotReady(string(R.string.plugin_virtual_screen_status_starting))
        is VirtualScreenState.Online -> PluginStatus.Ready(string(R.string.plugin_virtual_screen_status_online))
        is VirtualScreenState.Failed -> PluginStatus.Failed(state.message)
    }

    override fun actions(status: PluginStatus): List<PluginAction> = buildList {
        if (engine.state.value is VirtualScreenState.Online) {
            add(
                PluginAction(
                    ACTION_STOP,
                    R.string.plugin_virtual_screen_action_stop,
                    PluginActionStyle.DESTRUCTIVE,
                ),
            )
        } else {
            add(PluginAction(ACTION_START, R.string.plugin_virtual_screen_action_start))
        }
    }

    override suspend fun runAction(actionId: String, ui: PluginUi) {
        when (actionId) {
            ACTION_START -> engine.start()
            ACTION_STOP -> engine.stop()
        }
    }

    override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> {
        val state = engine.state.value
        val online = state as? VirtualScreenState.Online
        val backend = online?.backend?.let { backend ->
            when (backend) {
                PrivilegedLauncher.BACKEND_ROOT -> string(R.string.plugin_virtual_screen_backend_root)
                PrivilegedLauncher.BACKEND_ADB -> string(R.string.plugin_virtual_screen_backend_adb)
                else -> backend
            }
        } ?: string(R.string.plugin_virtual_screen_value_offline)
        val uid = engine.helperUid?.let { "uid $it" }
            ?: string(R.string.plugin_virtual_screen_value_offline)
        return listOf(
            PluginDetailRow(R.string.plugin_virtual_screen_detail_state, status.label),
            PluginDetailRow(
                R.string.plugin_virtual_screen_detail_resolution,
                online?.let { "${it.width} × ${it.height}" }
                    ?: string(R.string.plugin_virtual_screen_value_offline),
            ),
            PluginDetailRow(
                R.string.plugin_virtual_screen_detail_display,
                online?.displayId?.toString() ?: string(R.string.plugin_virtual_screen_value_offline),
            ),
            PluginDetailRow(R.string.plugin_virtual_screen_detail_backend, backend),
            PluginDetailRow(R.string.plugin_virtual_screen_detail_uid, uid),
            PluginDetailRow(
                R.string.plugin_virtual_screen_detail_controls,
                string(R.string.plugin_virtual_screen_detail_controls_value),
            ),
        )
    }

    override fun tools(): List<CodingTool> = virtualScreenTools(EngineScreen(engine))

    private fun string(id: Int): String = context.resources.getString(id)

    private companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
    }
}

internal fun virtualScreenAvailability(
    available: Boolean,
    unavailableReason: String,
): PluginAvailability = if (available) {
    PluginAvailability.Available
} else {
    PluginAvailability.Unavailable(unavailableReason)
}

internal interface VirtualScreenAccess {
    val state: StateFlow<VirtualScreenState>
    val reconnecting: Boolean get() = false
    val lastFailure: VirtualScreenFailureInfo?
    val lastTransportNotice: String?

    suspend fun start(): VirtualScreenResult

    suspend fun stop()
}

private class EngineScreen(private val engine: VirtualScreenEngine) : VirtualScreenAccess {
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
                    screen.start()
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
