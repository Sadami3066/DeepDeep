package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val SETUP_WAIT_TIMEOUT_MS = 15 * 60_000L

private fun pluginSetupParameters(ids: List<String>): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("plugin") {
            put("type", "string")
            if (ids.isNotEmpty()) {
                putJsonArray("enum") { ids.forEach { add(JsonPrimitive(it)) } }
            }
            put(
                "description",
                if (ids.isEmpty()) {
                    "Official plugin id"
                } else {
                    "Official plugin id: ${ids.joinToString(", ")}"
                },
            )
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("plugin")) })
}

fun pluginSetupTool(controller: PluginController?): CodingTool? {
    if (controller == null) return null
    val ids = controller.setupPluginIds
    return CodingTool(
        name = "plugin_setup",
        description = "Install and run the setup flow of an official DeepDeep plugin such as Feishu, WeCom or Xiaomi Cloud. Opens an in-app sheet and waits for the user; only works while DeepDeep is in the foreground.",
        parameters = pluginSetupParameters(ids),
        label = "plugin_setup",
        execute = { _, args, signal, _ ->
            signal?.throwIfAborted()
            val pluginId = (args["plugin"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            when {
                pluginId.isEmpty() -> textResult("plugin must not be empty", isError = true)
                ids.isNotEmpty() && pluginId !in ids -> textResult(
                    "unknown plugin: $pluginId. Valid ids: ${ids.joinToString(", ")}",
                    isError = true,
                )
                else -> try {
                    val result = withTimeoutOrNull(SETUP_WAIT_TIMEOUT_MS) { controller.setup(pluginId) }
                    if (result == null) {
                        textResult("Timed out waiting for the user to complete setup.")
                    } else {
                        textResult(setupResultJson(result).toString())
                    }
                } catch (error: RequestAbortedException) {
                    throw error
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    textResult(error.message ?: "plugin setup failed", isError = true)
                }
            }
        },
    )
}

private fun setupResultJson(result: PluginSetupResult): JsonObject = buildJsonObject {
    put("ok", result.ok)
    put("plugin", result.pluginId)
    put("installed", result.installed)
    put("ready", result.ready)
    result.status?.let { put("status", it) }
    result.account?.let { put("account", it) }
    result.error?.let { put("error", it) }
}
