package io.github.whywood.deepdeep.plugintemplate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginPageState
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginSkillFile
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.api.SlotState
import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.plugin.api.ToolCallDecision
import io.github.whywood.deepdeep.plugin.api.ToolCallRequest
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class TemplatePlugin : DeepDeepPlugin {
    private var streaming: Boolean = true

    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun uninstall(host: PluginHost) {
        runCatching { host.secrets().remove(SECRET_TOKEN) }
    }

    override suspend fun onEnable(host: PluginHost) {
        host.logs().info("template plugin enabled")
    }

    override suspend fun onDisable(host: PluginHost) {
        host.logs().info("template plugin disabled")
    }

    override suspend fun onUpgrade(host: PluginHost, fromVersion: String) {
        host.logs().info("template plugin upgraded from $fromVersion")
    }

    override fun skills(host: PluginHost): List<PluginSkill> {
        val skillRoot = File(host.assetsDir, "skills/template-skill")
        val markdown = File(skillRoot, "SKILL.md").takeIf { it.isFile }?.readText().orEmpty()
        if (markdown.isBlank()) return emptyList()
        return listOf(
            PluginSkill(
                id = SKILL_ID,
                markdown = markdown,
                files = listOf(
                    PluginSkillFile(
                        path = "references/format.md",
                        assetPath = "skills/template-skill/references/format.md",
                    ),
                ),
            ),
        )
    }

    override fun promptSections(host: PluginHost): List<PromptSection> = listOf(
        PromptSection(
            id = "template-hint",
            body = "The template plugin exposes template_echo and template_session demo tools. " +
                "They are examples for plugin development, not production tools.",
            priority = 100,
        ),
    )

    override fun contributions(host: PluginHost): Contributions = Contributions(
        tools = listOf(echoTool()),
        hooks = object : PluginHooks {
            override suspend fun turnFinished(event: PluginEvent.TurnFinished) {
                host.logs().debug("template plugin observed turn ${event.turnId} failed=${event.failed}")
            }

            override suspend fun beforeToolCall(request: ToolCallRequest): ToolCallDecision {
                val text = request.args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                return if (request.toolName == "template_echo" && "blocked" in text) {
                    ToolCallDecision.Block("template plugin blocked this demo call")
                } else {
                    ToolCallDecision.Continue
                }
            }
        },
    )

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions {
        val base = contributions(host)
        if (session == null) return base
        return base.copy(tools = base.tools + sessionTool(session))
    }

    override fun ui(host: PluginHost): PluginUiContributions = object : PluginUiContributions {
        override val bindings: List<SlotBinding> = listOf(
            SlotBinding(
                slotId = SlotIds.toolCard("template_echo"),
                priority = 0,
                content = SlotContent.Compose(uiApiLevel = 1) {
                    TemplateToolCard(host)
                },
            ),
            SlotBinding(
                slotId = SlotIds.page(PAGE_ID),
                content = SlotContent.Compose(uiApiLevel = 1) { state ->
                    TemplatePage(host, state)
                },
            ),
        )

        override val pages: List<PluginPage> = listOf(
            PluginPage(
                id = PAGE_ID,
                title = literal("Template Page", "模板页面"),
            ),
        )
    }

    private fun echoTool(): PluginTool = PluginTool(
        name = "template_echo",
        label = "Template Echo",
        description = "Echo text back in two steps, streaming one partial update before the final result.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("text") {
                    put("type", "string")
                    put("description", "Text to echo back.")
                }
            }
            put("required", buildJsonArray { add(JsonPrimitive("text")) })
        },
        promptSnippet = "template_echo echoes text and reports one partial update",
        promptGuidelines = listOf("template_echo is a development demo; do not rely on it in user workflows."),
    ) { call ->
        call.signal?.throwIfCancelled()
        val text = call.args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        call.partial(PluginToolResult.Text("received ${text.length} chars"))
        if (streaming) delay(PARTIAL_DELAY_MS)
        call.signal?.throwIfCancelled()
        PluginToolResult.Rich(
            blocks = listOf(
                ToolBlock.Text("echo: $text"),
                ToolBlock.Text("chars: ${text.length}"),
            ),
            details = buildJsonObject {
                put("chars", text.length)
            },
        )
    }

    private fun sessionTool(session: PluginSessionInfo): PluginTool = PluginTool(
        name = "template_session",
        label = "Template Session Info",
        description = "Return the active session id and working directory; reports when no session is currently bound.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("format") {
                    put("type", "string")
                    put("description", "Either text or json.")
                    put("enum", buildJsonArray { add(JsonPrimitive("text")); add(JsonPrimitive("json")) })
                }
            }
        },
    ) { call ->
        val format = call.args["format"]?.jsonPrimitive?.contentOrNull ?: "text"
        val snapshot = session.snapshot()
        val body = when {
            snapshot == null -> null
            format == "json" -> buildJsonObject {
                put("sessionId", snapshot.sessionId)
                put("cwd", snapshot.cwd)
            }.toString()

            else -> "session=${snapshot.sessionId} cwd=${snapshot.cwd}"
        }
        if (body == null) {
            PluginToolResult.Text("No session is bound.", isError = true)
        } else {
            PluginToolResult.Text(body)
        }
    }

    private companion object {
        const val SECRET_TOKEN = "template-token"
        const val SKILL_ID = "template-skill"
        const val PAGE_ID = "home"
        const val PARTIAL_DELAY_MS = 120L
    }
}

private fun literal(default: String, zh: String, en: String = default): LocalizedText =
    LocalizedText.Literal(mapOf("default" to default, "zh" to zh, "en" to en))

@Composable
private fun TemplatePage(host: PluginHost, state: SlotState) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = host.text(literal("Template plugin page", "模板插件页面")),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (state is PluginPageState) "page=${state.pageId}" else "template page",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = host.text(literal("This page is drawn by the plugin itself.", "该页面由插件自绘。")),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TemplateToolCard(host: PluginHost) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = host.text(literal("Template echo", "模板回显")),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = host.text(literal("Plugin development example", "插件开发示例")),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
