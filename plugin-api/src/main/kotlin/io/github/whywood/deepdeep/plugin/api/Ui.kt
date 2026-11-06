package io.github.whywood.deepdeep.plugin.api

import androidx.compose.runtime.Composable

object SlotIds {
    const val TOOL_CARD_PREFIX: String = "tool.card.content:"

    const val CHAT_MESSAGE_PREFIX: String = "chat.message:"

    const val PAGE_PREFIX: String = "plugin.page:"

    fun toolCard(toolName: String): String = TOOL_CARD_PREFIX + toolName

    fun chatMessage(customType: String): String = CHAT_MESSAGE_PREFIX + customType

    fun page(pageId: String): String = PAGE_PREFIX + pageId

    fun matches(slotId: String): Boolean = when {
        slotId.startsWith(TOOL_CARD_PREFIX) -> TOOL_SUFFIX.matches(slotId.removePrefix(TOOL_CARD_PREFIX))
        slotId.startsWith(CHAT_MESSAGE_PREFIX) -> MESSAGE_SUFFIX.matches(slotId.removePrefix(CHAT_MESSAGE_PREFIX))
        slotId.startsWith(PAGE_PREFIX) -> MESSAGE_SUFFIX.matches(slotId.removePrefix(PAGE_PREFIX))
        else -> false
    }

    private val TOOL_SUFFIX = Regex("^[a-z][a-z0-9_]*$")

    private val MESSAGE_SUFFIX = Regex("^[a-z][a-z0-9_.-]*$")
}

data class SlotBinding(
    val slotId: String,
    val priority: Int = 0,
    val content: SlotContent,
)

sealed interface SlotContent {
    data class Compose(
        val uiApiLevel: Int,
        val render: @Composable (SlotState) -> Unit,
    ) : SlotContent
}

data class PluginPage(
    val id: String,
    val title: LocalizedText,
    val icon: IconRef? = null,
)

data class ComposerAction(
    val id: String,
    val label: LocalizedText,
    val icon: IconRef? = null,
)

interface PluginUiContributions {
    val bindings: List<SlotBinding>

    val pages: List<PluginPage> get() = emptyList()

    val composerActions: List<ComposerAction> get() = emptyList()

    companion object {
        val None: PluginUiContributions = object : PluginUiContributions {
            override val bindings: List<SlotBinding> = emptyList()
        }
    }
}
