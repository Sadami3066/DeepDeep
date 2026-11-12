package io.github.whywood.deepdeep.plugin.host.events

import io.github.whywood.deepdeep.plugin.api.InputRequest as PluginInputRequest
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginContextRequest
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PromptRequest
import io.github.whywood.deepdeep.plugin.api.ToolCallAction
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import io.github.whywood.deepdeep.plugin.api.ToolExecutionResult
import io.github.whywood.deepdeep.plugin.api.ToolResultAction
import io.github.whywood.deepdeep.runtime.ImagePart
import io.github.whywood.deepdeep.runtime.InputDecision as RuntimeInputDecision
import io.github.whywood.deepdeep.runtime.InputHandler
import io.github.whywood.deepdeep.runtime.PromptTransform
import io.github.whywood.deepdeep.runtime.ToolCallContext
import io.github.whywood.deepdeep.runtime.ToolCallDecision as RuntimeToolCallDecision
import io.github.whywood.deepdeep.runtime.ToolExecutionHooks
import io.github.whywood.deepdeep.runtime.ToolResultContext
import io.github.whywood.deepdeep.runtime.ToolResultDecision as RuntimeToolResultDecision
import java.io.File
import java.util.Base64

internal class PluginRuntimeHooksAdapter(
    val session: PluginHookSession,
    source: PluginHookSource,
    reporter: PluginHookReporter = PluginHookReporter.Logging,
    observeTimeoutMs: Long = PluginHooks.OBSERVE_TIMEOUT_MS,
    transformTimeoutMs: Long = PluginHooks.TRANSFORM_TIMEOUT_MS,
) {
    private val dispatchers = PluginHookDispatchers(source, reporter, transformTimeoutMs)

    val events: PluginEventBus = PluginEventBus(source, reporter, observeTimeoutMs)

    internal val eventIds: PluginEventIds = PluginEventIds()

    val promptTransform: PromptTransform = { prompt ->
        dispatchers.transformPrompt(PromptRequest(session.sessionId, prompt))
    }

    suspend fun systemContext(): String? {
        val contributions = dispatchers.collectContext(
            PluginContextRequest(
                sessionId = session.sessionId,
                cwd = session.cwd,
            ),
        )
        if (contributions.isEmpty()) return null
        return contributions.joinToString("\n") { (pluginId, text) ->
            "<plugin_context plugin=\"${escapeXml(pluginId)}\">\n${escapeXml(text)}\n</plugin_context>"
        }
    }

    val inputHandler: InputHandler = { request ->
        val decision = dispatchers.transformInput(
            PluginInputRequest(
                sessionId = session.sessionId,
                text = request.text,
                imagePaths = request.images.mapNotNull { image -> image.path },
            ),
        )
        if (decision.handled) {
            RuntimeInputDecision.Handled
        } else {
            RuntimeInputDecision.Continue(
                text = decision.text ?: request.text,
                images = resolveImages(decision.imagePaths, request.images),
            )
        }
    }

    val toolHooks: ToolExecutionHooks = object : ToolExecutionHooks {
        override suspend fun beforeToolCall(context: ToolCallContext): RuntimeToolCallDecision {
            val decision = dispatchers.beforeToolCall(
                ToolCallRequest(
                    sessionId = session.sessionId,
                    callId = context.callId,
                    toolName = context.toolName,
                    args = context.args,
                ),
            )
            return when (decision.action) {
                ToolCallAction.BLOCK -> RuntimeToolCallDecision.Block(decision.reason.orEmpty())
                ToolCallAction.REWRITE -> RuntimeToolCallDecision.Rewrite(decision.args ?: context.args)
                else -> RuntimeToolCallDecision.Continue
            }
        }

        override suspend fun afterToolResult(context: ToolResultContext): RuntimeToolResultDecision {
            val decision = dispatchers.afterToolExecution(
                ToolExecutionResult(
                    sessionId = session.sessionId,
                    callId = context.callId,
                    toolName = context.toolName,
                    result = context.result.toPluginToolResult(),
                ),
            )
            val replacement = decision.result
            return if (decision.action == ToolResultAction.REPLACE && replacement != null) {
                RuntimeToolResultDecision.Replace(replacement.toAgentToolResult())
            } else {
                RuntimeToolResultDecision.Keep
            }
        }
    }

    suspend fun emit(event: PluginEvent): List<PluginHookFailure> = events.emit(event)

    private fun resolveImages(paths: List<String>, current: List<ImagePart>): List<ImagePart> {
        if (paths.isEmpty()) return emptyList()
        val byPath = current.mapNotNull { image -> image.path?.let { path -> path to image } }.toMap()
        return paths.mapNotNull { path -> byPath[path] ?: readImagePart(path) }
    }

    private fun readImagePart(path: String): ImagePart? = runCatching {
        val file = File(path)
        if (!file.isFile) return@runCatching null
        val mime = imageMimeOf(file.extension) ?: return@runCatching null
        ImagePart(Base64.getEncoder().encodeToString(file.readBytes()), mime, path)
    }.getOrNull()

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(character)
            }
        }
    }

    private fun imageMimeOf(extension: String): String? = when (extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        else -> null
    }
}
