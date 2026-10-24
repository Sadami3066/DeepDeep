package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val FEISHU_PLUGIN_ID = "feishu"

private val pluginSetupParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("plugin") {
            put("type", "string")
            putJsonArray("enum") { add(JsonPrimitive(FEISHU_PLUGIN_ID)) }
            put("description", "Official plugin id")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("plugin")) })
}

fun pluginSetupTool(controller: PluginController?): CodingTool? {
    if (controller == null) return null
    return CodingTool(
        name = "plugin_setup",
        description = "Install and run the setup flow of an official DeepDeep plugin, for example connecting Feishu. Opens an in-app sheet and waits until the user finishes. Only works while DeepDeep is in the foreground.",
        parameters = pluginSetupParameters,
        label = "plugin_setup",
        promptSnippet = "Set up an official plugin with the user",
        promptGuidelines = listOf(
            "Use plugin_setup when the user asks to connect, enable, or sign in to an official plugin.",
            "Do not run a plugin's interactive setup commands yourself; the plugin sheet is the only way to show the user an authorization link.",
            "When plugin_setup reports that the user cancelled, ask what went wrong instead of retrying in a loop.",
        ),
        execute = { _, args, signal, _ ->
            signal?.throwIfAborted()
            val pluginId = (args["plugin"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (pluginId.isEmpty()) {
                textResult("plugin must not be empty", isError = true)
            } else {
                try {
                    textResult(setupResultJson(controller.setup(pluginId)).toString())
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
