package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

data class Contributions(
    val tools: List<PluginTool> = emptyList(),
    val hooks: PluginHooks = PluginHooks.None,
)

class PluginTool(
    val name: String,
    val label: String,
    val description: String,
    val parameters: JsonObject,
    val promptSnippet: String? = null,
    val promptGuidelines: List<String> = emptyList(),
    val execute: suspend (PluginToolCall) -> PluginToolResult,
)

class PluginToolCall(
    val callId: String,
    val args: JsonObject,
    val signal: PluginRunSignal? = null,
    private val update: ((PluginToolResult) -> Unit)? = null,
) {
    fun partial(result: PluginToolResult) {
        update?.invoke(result)
    }
}

interface PluginRunSignal {
    val cancelled: Boolean

    fun throwIfCancelled()

    fun onCancel(listener: () -> Unit): () -> Unit

    companion object {
        val None: PluginRunSignal = object : PluginRunSignal {
            override val cancelled: Boolean = false

            override fun throwIfCancelled() = Unit

            override fun onCancel(listener: () -> Unit): () -> Unit = {}
        }
    }
}

sealed interface PluginToolResult {
    val isError: Boolean

    data class Text(
        val text: String,
        val details: JsonObject? = null,
        override val isError: Boolean = false,
    ) : PluginToolResult

    data class Rich(
        val blocks: List<ToolBlock>,
        val details: JsonObject? = null,
        override val isError: Boolean = false,
    ) : PluginToolResult

    companion object {
        fun text(text: String, details: JsonObject? = null, isError: Boolean = false): PluginToolResult =
            Text(text, details, isError)

        fun rich(blocks: List<ToolBlock>, details: JsonObject? = null, isError: Boolean = false): PluginToolResult =
            Rich(blocks, details, isError)
    }
}

sealed interface ToolBlock {
    data class Text(val text: String) : ToolBlock

    data class Image(val data: ByteArray, val mimeType: String) : ToolBlock

    data class FileRef(val path: String, val mimeType: String? = null) : ToolBlock
}
