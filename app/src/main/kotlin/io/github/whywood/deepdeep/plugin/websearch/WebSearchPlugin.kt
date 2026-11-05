package io.github.whywood.deepdeep.plugin.websearch

import android.content.Context
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.api.Contributions
import io.github.whywood.deepdeep.plugin.api.DeepDeepPlugin
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginAvailability
import io.github.whywood.deepdeep.plugin.api.PluginDetailRow
import io.github.whywood.deepdeep.plugin.api.PluginHost
import io.github.whywood.deepdeep.plugin.api.PluginSessionInfo
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.api.PromptSection
import io.github.whywood.deepdeep.plugin.host.CodingToolPluginAdapter

class WebSearchPlugin(
    context: Context,
    private val apiKeyProvider: suspend () -> String?,
    private val provider: WebProvider = DeepSeekWebProvider(apiKeyProvider),
) : DeepDeepPlugin {
    @Volatile
    private var configured: Boolean? = null

    override suspend fun availability(host: PluginHost): PluginAvailability = PluginAvailability.Available

    override suspend fun status(host: PluginHost): PluginStatus {
        val ready = apiKeyProvider()?.isNotBlank() == true
        configured = ready
        return if (ready) {
            PluginStatus.Ready(host.text(LocalizedText.Resource(R.string.plugin_web_search_status_ready)))
        } else {
            PluginStatus.NotReady(host.text(LocalizedText.Resource(R.string.plugin_web_search_status_not_ready)))
        }
    }

    override fun detailRows(host: PluginHost, status: PluginStatus): List<PluginDetailRow> = listOf(
        PluginDetailRow(
            label = LocalizedText.Resource(R.string.plugin_web_search_detail_source),
            value = host.text(LocalizedText.Resource(R.string.plugin_web_search_source_deepseek)),
        ),
        PluginDetailRow(
            label = LocalizedText.Resource(R.string.plugin_web_search_detail_api_key),
            value = host.text(
                LocalizedText.Resource(
                    if (status is PluginStatus.Ready) {
                        R.string.plugin_web_search_api_key_configured
                    } else {
                        R.string.plugin_web_search_api_key_missing
                    },
                ),
            ),
        ),
    )

    override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?): Contributions =
        Contributions(tools = CodingToolPluginAdapter.toPluginTools(webTools(provider)))

    override fun promptSections(host: PluginHost): List<PromptSection> =
        if (configured == true) {
            emptyList()
        } else {
            listOf(PromptSection(id = PROMPT_SECTION_ID, body = MISSING_KEY_PROMPT))
        }

    private companion object {
        const val PROMPT_SECTION_ID = "web-search-unconfigured"

        val MISSING_KEY_PROMPT =
            "网络搜索插件已安装，但尚未配置 DeepSeek API Key：`web_search` 与 `web_fetch` 当前不可用。\n" +
                "需要联网信息时，先向用户说明要在设置 → 提供商中为 DeepSeek 配置 API Key；不要猜测、读取或索要密钥。\n" +
                "无法联网时基于已有知识回答，并说明这一限制。"
    }
}
