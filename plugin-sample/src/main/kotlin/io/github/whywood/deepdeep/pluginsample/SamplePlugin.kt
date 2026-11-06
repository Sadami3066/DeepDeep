package io.github.whywood.deepdeep.pluginsample

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
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginPage
import io.github.whywood.deepdeep.plugin.api.PluginPageState
import io.github.whywood.deepdeep.plugin.api.PluginTool
import io.github.whywood.deepdeep.plugin.api.PluginToolResult
import io.github.whywood.deepdeep.plugin.api.PluginUiContributions
import io.github.whywood.deepdeep.plugin.api.SlotBinding
import io.github.whywood.deepdeep.plugin.api.SlotContent
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.api.SlotState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SamplePlugin : DeepDeepPlugin {
    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override fun contributions(host: PluginHost): Contributions = Contributions(
        tools = listOf(
            PluginTool(
                name = "sample_echo",
                label = "Sample Echo",
                description = "Echo the given text back.",
                parameters = buildJsonObject {
                    put(
                        "text",
                        buildJsonObject {
                            put("type", "string")
                            put("description", "Text to echo")
                        },
                    )
                },
                promptSnippet = "sample_echo echoes text",
            ) { call ->
                val text = call.args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                PluginToolResult.Text("echo: $text")
            },
            PluginTool(
                name = "sample_card",
                label = "Sample Card",
                description = "Post a sample plugin card into the current chat.",
                parameters = buildJsonObject {
                    put("title", buildJsonObject { put("type", "string") })
                    put("body", buildJsonObject { put("type", "string") })
                },
                promptSnippet = "sample_card posts a card into the chat",
            ) { call ->
                val payload = buildJsonObject {
                    put("title", call.args["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    put("body", call.args["body"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
                val posted = host.chat().postCustomMessage(
                    sessionId = call.sessionId,
                    customType = "sample.card",
                    payload = payload,
                    text = payload.toString(),
                )
                if (posted) {
                    PluginToolResult.Text("card posted")
                } else {
                    PluginToolResult.Text("no active session to post the card", isError = true)
                }
            },
        ),
    )

    override fun ui(host: PluginHost): PluginUiContributions = object : PluginUiContributions {
        override val bindings: List<SlotBinding> = listOf(
            SlotBinding(
                slotId = SlotIds.chatMessage("sample.card"),
                content = SlotContent.Compose(uiApiLevel = 1) {
                    Text("sample plugin card")
                },
            ),
            SlotBinding(
                slotId = SlotIds.page(PAGE_ID),
                content = SlotContent.Compose(uiApiLevel = 1) { state ->
                    SamplePage(host = host, state = state)
                },
            ),
        )

        override val pages: List<PluginPage> = listOf(
            PluginPage(
                id = PAGE_ID,
                title = LocalizedText.Literal(
                    mapOf("default" to "Sample Page", "zh" to "示例页面", "en" to "Sample Page"),
                ),
            ),
        )
    }

    private companion object {
        const val PAGE_ID = "sample.main"
    }
}

@Composable
private fun SamplePage(host: PluginHost, state: SlotState) {
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = host.text(
                LocalizedText.Literal(mapOf("default" to "Sample plugin page", "zh" to "示例插件页面")),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (state is PluginPageState) "page=${state.pageId}" else "sample page",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
