package io.github.whywood.deepdeep.plugin.api

import androidx.compose.runtime.Composable

public object SlotIds {
    public const val TOOL_CARD_PREFIX: String = "tool.card.content:"

    public const val CHAT_MESSAGE_PREFIX: String = "chat.message:"

    public fun toolCard(toolName: String): String = TOOL_CARD_PREFIX + toolName

    public fun chatMessage(customType: String): String = CHAT_MESSAGE_PREFIX + customType

    public fun matches(slotId: String): Boolean = when {
        slotId.startsWith(TOOL_CARD_PREFIX) -> TOOL_SUFFIX.matches(slotId.removePrefix(TOOL_CARD_PREFIX))
        slotId.startsWith(CHAT_MESSAGE_PREFIX) -> MESSAGE_SUFFIX.matches(slotId.removePrefix(CHAT_MESSAGE_PREFIX))
        else -> false
    }

    private val TOOL_SUFFIX = Regex("^[a-z][a-z0-9_]*$")

    private val MESSAGE_SUFFIX = Regex("^[a-z][a-z0-9_.-]*$")
}

public interface SlotBinding {
    public val slotId: String
    public val priority: Int
    public val content: SlotContent
}

public fun slotBinding(
    slotId: String,
    priority: Int = 0,
    content: SlotContent,
): SlotBinding = SlotBindingImpl(slotId, priority, content)

internal data class SlotBindingImpl(
    override val slotId: String,
    override val priority: Int,
    override val content: SlotContent,
) : SlotBinding

public interface SlotContent {
    @Composable
    public fun render(state: SlotState)
}

public fun composeSlot(render: @Composable (SlotState) -> Unit): SlotContent =
    object : SlotContent {
        @Composable
        override fun render(state: SlotState) {
            render(state)
        }
    }

public interface PluginPage {
    public val id: String
    public val title: LocalizedText
    public val icon: IconRef?

    @Composable
    public fun content()
}

public fun pluginPage(
    id: String,
    title: LocalizedText,
    icon: IconRef? = null,
    content: @Composable () -> Unit,
): PluginPage = object : PluginPage {
    override val id: String = id

    override val title: LocalizedText = title

    override val icon: IconRef? = icon

    @Composable
    override fun content() {
        content()
    }
}

public interface ComposerAction {
    public val id: String
    public val label: LocalizedText
    public val icon: IconRef?
}

public fun composerAction(
    id: String,
    label: LocalizedText,
    icon: IconRef? = null,
): ComposerAction = ComposerActionImpl(id, label, icon)

internal data class ComposerActionImpl(
    override val id: String,
    override val label: LocalizedText,
    override val icon: IconRef?,
) : ComposerAction

public interface PluginUiContributions {
    public val bindings: List<SlotBinding>

    public val pages: List<PluginPage>
        get() = emptyList()

    public val composerActions: List<ComposerAction>
        get() = emptyList()

    public companion object {
        public val None: PluginUiContributions = object : PluginUiContributions {
            override val bindings: List<SlotBinding> = emptyList()
        }
    }
}
