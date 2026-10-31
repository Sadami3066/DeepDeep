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
import io.github.whywood.deepdeep.vscreen.VirtualScreenRuntime
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

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
        add(PluginAction(ACTION_VIEW, R.string.plugin_virtual_screen_action_view))
    }

    override suspend fun runAction(actionId: String, ui: PluginUi) {
        when (actionId) {
            ACTION_START -> engine.start()
            ACTION_STOP -> engine.stop()
            ACTION_VIEW -> engine.openViewer(context)
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

    override fun tools(): List<CodingTool> = listOf(
        startTool(engine),
        stopTool(engine),
        statusTool(engine),
    )

    private fun string(id: Int): String = context.resources.getString(id)

    private companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_VIEW = "view"
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

private val noParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private fun startTool(engine: VirtualScreenEngine): CodingTool = CodingTool(
    name = "virtual_screen_start",
    description = "Start the privileged virtual screen at the real phone screen size in portrait. Requires ADB (wireless debugging) or Root privilege to be selected and available.",
    parameters = noParameters,
    label = "virtual_screen_start",
    promptSnippet = "Start the background virtual screen",
    promptGuidelines = listOf(
        "Call virtual_screen_start before any screen_* tool with target=virtual; it fails with a readable message when no privilege backend is available.",
        "After starting, open apps on it with screen_launch target=virtual and operate it with screen_* tools.",
    ),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        virtualScreenTool {
            val result = engine.start()
            if (!result.ok) throw IllegalStateException(result.message)
            stateJson(engine).toString()
        }
    },
)

private fun stopTool(engine: VirtualScreenEngine): CodingTool = CodingTool(
    name = "virtual_screen_stop",
    description = "Stop the virtual screen and release its display and the helper process.",
    parameters = noParameters,
    label = "virtual_screen_stop",
    promptSnippet = "Stop the background virtual screen",
    promptGuidelines = listOf("Call virtual_screen_stop when the tasks on the virtual screen are finished; the display and the helper process are released."),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        virtualScreenTool {
            engine.stop()
            buildJsonObject {
                put("ok", true)
                put("state", "offline")
            }.toString()
        }
    },
)

private fun statusTool(engine: VirtualScreenEngine): CodingTool = CodingTool(
    name = "virtual_screen_status",
    description = "Report the virtual screen state: online or offline, display id, resolution, privilege backend and helper uid. Operation tools live under screen_* with target=virtual.",
    parameters = noParameters,
    label = "virtual_screen_status",
    promptSnippet = "Check the virtual screen status",
    promptGuidelines = listOf("Call virtual_screen_status when unsure whether the virtual screen is running or how it is running."),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        virtualScreenTool { stateJson(engine).toString() }
    },
)

private fun stateJson(engine: VirtualScreenEngine): JsonObject = buildJsonObject {
    when (val state = engine.state.value) {
        VirtualScreenState.Offline -> put("state", "offline")
        VirtualScreenState.Starting -> put("state", "starting")
        is VirtualScreenState.Failed -> {
            put("state", "failed")
            put("error", state.message)
        }
        is VirtualScreenState.Online -> {
            put("state", "online")
            put("displayId", state.displayId)
            put("width", state.width)
            put("height", state.height)
            put("backend", state.backend)
            put("uid", engine.helperUid ?: -1)
        }
    }
}

private suspend fun virtualScreenTool(block: suspend () -> String): AgentToolResult = try {
    textResult(block())
} catch (error: RequestAbortedException) {
    throw error
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    textResult(error.message ?: "虚拟屏操作失败", isError = true)
}
