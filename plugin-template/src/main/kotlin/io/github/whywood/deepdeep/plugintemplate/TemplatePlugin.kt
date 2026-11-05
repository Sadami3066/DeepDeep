package io.github.whywood.deepdeep.plugintemplate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAction
import io.github.whywood.deepdeep.plugin.api.PluginActionStyle
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginDetailRow
import io.github.whywood.deepdeep.plugin.api.PluginEvent
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginHooks
import io.github.whywood.deepdeep.plugin.api.PluginNotification
import io.github.whywood.deepdeep.plugin.api.PluginProgress
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginSetupUi
import io.github.whywood.deepdeep.plugin.api.PluginSkill
import io.github.whywood.deepdeep.plugin.api.PluginSkillFile
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.api.SettingOption
import io.github.whywood.deepdeep.plugin.api.SettingSpec
import io.github.whywood.deepdeep.plugin.api.SetupSlotState
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
    private var connected: Boolean = false
    private var settings: Map<String, String> = emptyMap()

    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus =
        if (connected) {
            PluginStatus.Ready(host.text(literal("Connected", "已连接")), account = accountLabel(host))
        } else {
            PluginStatus.NotReady(host.text(literal("Not connected yet", "尚未连接")))
        }

    override suspend fun install(host: PluginHost, progress: PluginProgress) {
        progress.report(20, literal("Preparing plugin files", "准备插件文件"))
        delay(120)
        progress.report(100, literal("Installed", "已安装"))
    }

    override suspend fun uninstall(host: PluginHost) {
        runCatching { host.secrets().remove(SECRET_TOKEN) }
        connected = false
    }

    override suspend fun onEnable(host: PluginHost) {
        connected = readToken(host) != null
    }

    override suspend fun onDisable(host: PluginHost) {
        host.logs().info("template plugin disabled")
    }

    override suspend fun onUpgrade(host: PluginHost, fromVersion: String) {
        host.logs().info("template plugin upgraded from $fromVersion")
    }

    override suspend fun setup(host: PluginHost, ui: PluginSetupUi) {
        ui.stage(literal("Checking environment", "检查运行环境"))
        ui.progress(25, literal("Reading assets", "读取插件资源"))
        delay(200)
        if (ui.cancelled()) return
        ui.stage(
            title = literal("Connect the template plugin", "连接模板插件"),
            message = literal(
                "This demo stores a token through host.secrets().",
                "示例会通过 host.secrets() 保存令牌。",
            ),
            url = "https://example.com/plugin-template/setup",
            progress = 60,
        )
        delay(200)
        if (ui.cancelled()) return
        runCatching { host.secrets().put(SECRET_TOKEN, "demo-token") }
        connected = true
        ui.progress(100, literal("Connected", "已连接"))
    }

    override fun actions(host: PluginHost, status: PluginStatus): List<PluginAction> = listOf(
        PluginAction(
            id = ACTION_RECONNECT,
            label = literal("Reconnect", "重新连接"),
            enabled = !connected,
        ),
        PluginAction(
            id = ACTION_RESET,
            label = literal("Reset template state", "重置模板状态"),
            style = PluginActionStyle.DESTRUCTIVE,
            enabled = connected,
        ),
    )

    override suspend fun runAction(host: PluginHost, actionId: String, ui: PluginSetupUi) {
        when (actionId) {
            ACTION_RECONNECT -> {
                connected = readToken(host) != null || runCatching {
                    host.secrets().put(SECRET_TOKEN, "demo-token")
                }.isSuccess
                runCatching {
                    host.notifications().notify(
                        PluginNotification(
                            id = "template-reconnected",
                            title = host.text(literal("Template plugin", "模板插件")),
                            body = host.text(literal("Reconnected", "已重新连接")),
                        ),
                    )
                }
            }

            ACTION_RESET -> {
                runCatching { host.secrets().remove(SECRET_TOKEN) }
                connected = false
            }
        }
    }

    override fun detailRows(host: PluginHost, status: PluginStatus): List<PluginDetailRow> = listOf(
        PluginDetailRow(
            label = literal("Connection", "连接状态"),
            value = host.text(if (connected) literal("Connected", "已连接") else literal("Not connected", "未连接")),
        ),
        PluginDetailRow(
            label = literal("Mode", "模式"),
            value = settings["mode"] ?: "fast",
        ),
        PluginDetailRow(
            label = literal("Plugin API", "插件 API"),
            value = runCatching { host.app().info.pluginApiLevel }.getOrNull()?.toString() ?: "unknown",
        ),
    )

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

    override fun settings(): List<SettingSpec> = listOf(
        SettingSpec.Group(
            key = "connection",
            title = literal("Connection", "连接"),
            items = listOf(
                SettingSpec.Text(
                    key = "api_key",
                    title = literal("API key", "API Key"),
                    default = "",
                    secret = true,
                    requiresRestart = true,
                    summary = literal(
                        "Stored in plaintext plugin settings; use host.secrets() for real secrets.",
                        "明文保存在插件设置里；真正的密钥请用 host.secrets()。",
                    ),
                ),
                SettingSpec.Toggle(
                    key = "streaming",
                    title = literal("Stream partial results", "流式返回中间结果"),
                    defaultValue = true,
                ),
            ),
        ),
        SettingSpec.Choice(
            key = "mode",
            title = literal("Echo mode", "回显模式"),
            options = listOf(
                SettingOption("fast", literal("Fast", "快速")),
                SettingOption("thorough", literal("Thorough", "完整")),
            ),
            default = "fast",
        ),
        SettingSpec.Number(
            key = "max_results",
            title = literal("Max results", "最大结果数"),
            min = 1,
            max = 20,
            defaultValue = 5,
        ),
    )

    override suspend fun onSettingsChanged(host: PluginHost, values: Map<String, String>) {
        settings = values
        host.logs().info("template settings changed: ${values.keys.sorted()}")
    }

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
                slotId = SlotIds.setupContent(),
                priority = 10,
                content = SlotContent.Compose(uiApiLevel = 1) { state ->
                    TemplateSetupSlot(host, state)
                },
            ),
            SlotBinding(
                slotId = SlotIds.toolCard("template_echo"),
                priority = 0,
                content = SlotContent.Compose(uiApiLevel = 1) {
                    TemplateToolCard(host)
                },
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
        val mode = settings["mode"] ?: "fast"
        call.partial(PluginToolResult.Text("received ${text.length} chars (mode=$mode)"))
        if (settings["streaming"] != "false") delay(PARTIAL_DELAY_MS)
        call.signal?.throwIfCancelled()
        PluginToolResult.Rich(
            blocks = listOf(
                ToolBlock.Text("echo: $text"),
                ToolBlock.Text("mode: $mode, chars: ${text.length}"),
            ),
            details = buildJsonObject {
                put("mode", mode)
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

    private suspend fun readToken(host: PluginHost): String? =
        runCatching { host.secrets().get(SECRET_TOKEN) }.getOrNull()

    private fun accountLabel(host: PluginHost): String =
        settings["api_key"]?.takeIf { it.isNotBlank() }?.let { host.text(literal("API key configured", "已配置 API Key")) }
            ?: host.text(literal("Demo account", "演示账号"))

    private companion object {
        const val SECRET_TOKEN = "template-token"
        const val ACTION_RECONNECT = "reconnect"
        const val ACTION_RESET = "reset"
        const val SKILL_ID = "template-skill"
        const val PARTIAL_DELAY_MS = 120L
    }
}

@Composable
private fun TemplateSetupSlot(host: PluginHost, state: SlotState) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = host.text(literal("Plugin template", "插件模板")),
            style = MaterialTheme.typography.titleMedium,
        )
        when (state) {
            is SetupSlotState -> {
                Text(host.text(state.title), style = MaterialTheme.typography.bodyMedium)
                state.message?.let { Text(host.text(it), style = MaterialTheme.typography.bodySmall) }
                state.url?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                state.progress?.let { Text("progress: $it%", style = MaterialTheme.typography.bodySmall) }
            }

            else -> Text(
                text = host.text(literal("waiting for setup state", "等待 setup 状态")),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun literal(default: String, zh: String, en: String = default): LocalizedText =
    LocalizedText.Literal(mapOf("default" to default, "zh" to zh, "en" to en))

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
        HorizontalDivider()
        val uriHandler = LocalUriHandler.current
        Text(
            text = host.text(literal("Plugin development guide", "插件开发文档")),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable {
                uriHandler.openUri("https://example.com/plugin-template/docs")
            },
        )
    }
}
