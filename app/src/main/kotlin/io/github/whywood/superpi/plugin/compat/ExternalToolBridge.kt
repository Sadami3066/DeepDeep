package io.github.whywood.superpi.plugin.compat

import io.github.whywood.superpi.plugin.api.Cards
import io.github.whywood.superpi.plugin.api.PluginTool
import io.github.whywood.superpi.plugin.api.PluginToolCall
import io.github.whywood.superpi.plugin.api.PluginToolExposure
import io.github.whywood.superpi.plugin.api.pluginTool
import io.github.whywood.superpi.plugin.api.textResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class ExternalToolExecutionOutput(
    val stdout: String,
    val stderr: String = "",
    val exitCode: Int = 0,
    val guidance: String? = null,
)

object ExternalToolBridge {
    fun createPluginTool(
        toolJson: JsonObject,
        executeBlock: suspend (PluginToolCall) -> ExternalToolExecutionOutput,
    ): PluginTool {
        val fixedJson = SuperPICompatibilityFixer.applyAllRecommendedTool(toolJson)
        val name = (fixedJson["name"] as? JsonPrimitive)?.contentOrNull ?: "custom_tool"
        val label = (fixedJson["label"] as? JsonPrimitive)?.contentOrNull ?: name
        val description = (fixedJson["description"] as? JsonPrimitive)?.contentOrNull ?: name
        val parameters = (fixedJson["parameters"] as? JsonObject) ?: buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {})
        }
        val exposureStr = (fixedJson["exposure"] as? JsonPrimitive)?.contentOrNull
        val exposure = if (exposureStr.equals("DEFERRED", ignoreCase = true)) {
            PluginToolExposure.DEFERRED
        } else {
            PluginToolExposure.DIRECT
        }

        return pluginTool(
            name = name,
            label = label,
            description = description,
            parameters = parameters,
            exposure = exposure,
        ) { call ->
            call.signal?.throwIfCancelled()
            val output = executeBlock(call)
            val cleanStdout = cleanAnsi(output.stdout)
            val isError = output.exitCode != 0

            val details = buildJsonObject {
                if (cleanStdout.isNotBlank()) {
                    put(Cards.RAW_KEY, cleanStdout)
                }
                output.guidance?.takeIf { it.isNotBlank() }?.let {
                    put(Cards.GUIDANCE_KEY, it)
                }
            }

            val bodyText = when {
                isError && output.stderr.isNotBlank() -> cleanAnsi(output.stderr)
                cleanStdout.isNotBlank() -> cleanStdout.lines().take(10).joinToString("\n")
                else -> if (isError) "Command failed with code ${output.exitCode}" else "Command completed successfully"
            }

            textResult(bodyText, details = details, isError = isError)
        }
    }

    private fun cleanAnsi(input: String): String =
        input.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "").trim()
}
