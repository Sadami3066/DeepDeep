package io.github.whywood.deepdeep.plugin.api

import kotlinx.serialization.json.JsonObject

public enum class PluginToolExposure {
    DIRECT,
    DEFERRED,
}

public interface Contributions {
    public val tools: List<PluginTool>
    public val hooks: PluginHooks
}

public fun contributions(
    tools: List<PluginTool> = emptyList(),
    hooks: PluginHooks = PluginHooks.None,
): Contributions = ContributionsImpl(tools, hooks)

internal data class ContributionsImpl(
    override val tools: List<PluginTool>,
    override val hooks: PluginHooks,
) : Contributions

public interface PluginTool {
    public val name: String
    public val label: String
    public val description: String
    public val parameters: JsonObject
    public val promptSnippet: String?
    public val promptGuidelines: List<String>
    public val exposure: PluginToolExposure

    public suspend fun execute(call: PluginToolCall): PluginToolResult
}

public fun pluginTool(
    name: String,
    description: String,
    parameters: JsonObject,
    label: String? = null,
    promptSnippet: String? = null,
    promptGuidelines: List<String> = emptyList(),
    exposure: PluginToolExposure = PluginToolExposure.DIRECT,
    execute: suspend (PluginToolCall) -> PluginToolResult,
): PluginTool = PluginToolImpl(
    name = name,
    label = label ?: name,
    description = description,
    parameters = parameters,
    promptSnippet = promptSnippet,
    promptGuidelines = promptGuidelines,
    exposure = exposure,
    execute = execute,
)

internal class PluginToolImpl(
    override val name: String,
    override val label: String,
    override val description: String,
    override val parameters: JsonObject,
    override val promptSnippet: String?,
    override val promptGuidelines: List<String>,
    override val exposure: PluginToolExposure,
    private val execute: suspend (PluginToolCall) -> PluginToolResult,
) : PluginTool {
    override suspend fun execute(call: PluginToolCall): PluginToolResult = execute(call)
}

public interface PluginToolCall {
    public val callId: String
    public val args: JsonObject
    public val signal: PluginRunSignal?
    public val sessionId: String

    public fun partial(result: PluginToolResult)
}

public interface PluginRunSignal {
    public val cancelled: Boolean

    public fun throwIfCancelled()

    public fun onCancel(listener: () -> Unit): () -> Unit

    public companion object {
        public val None: PluginRunSignal = object : PluginRunSignal {
            override val cancelled: Boolean = false

            override fun throwIfCancelled(): Unit = Unit

            override fun onCancel(listener: () -> Unit): () -> Unit = {}
        }
    }
}

public interface PluginToolResult {
    public val text: String?
    public val blocks: List<ToolBlock>
    public val details: JsonObject?
    public val isError: Boolean
}

public fun textResult(
    text: String,
    details: JsonObject? = null,
    isError: Boolean = false,
): PluginToolResult = PluginToolResultImpl(text = text, blocks = emptyList(), details = details, isError = isError)

public fun richResult(
    blocks: List<ToolBlock>,
    details: JsonObject? = null,
    isError: Boolean = false,
): PluginToolResult = PluginToolResultImpl(text = null, blocks = blocks, details = details, isError = isError)

internal data class PluginToolResultImpl(
    override val text: String?,
    override val blocks: List<ToolBlock>,
    override val details: JsonObject?,
    override val isError: Boolean,
) : PluginToolResult

public interface ToolBlock

public interface TextBlock : ToolBlock {
    public val text: String
}

public interface ImageBlock : ToolBlock {
    public val data: ByteArray
    public val mimeType: String
}

public interface FileRefBlock : ToolBlock {
    public val path: String
    public val mimeType: String?
}

public fun textBlock(text: String): ToolBlock = TextBlockImpl(text)

public fun imageBlock(data: ByteArray, mimeType: String): ToolBlock = ImageBlockImpl(data, mimeType)

public fun fileRefBlock(path: String, mimeType: String? = null): ToolBlock = FileRefBlockImpl(path, mimeType)

internal data class TextBlockImpl(override val text: String) : TextBlock

internal data class ImageBlockImpl(
    override val data: ByteArray,
    override val mimeType: String,
) : ImageBlock {
    override fun equals(other: Any?): Boolean =
        other is ImageBlock && data.contentEquals(other.data) && mimeType == other.mimeType

    override fun hashCode(): Int = 31 * data.contentHashCode() + mimeType.hashCode()
}

internal data class FileRefBlockImpl(
    override val path: String,
    override val mimeType: String?,
) : FileRefBlock
