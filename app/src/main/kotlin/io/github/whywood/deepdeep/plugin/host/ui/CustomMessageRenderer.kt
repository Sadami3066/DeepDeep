package io.github.whywood.deepdeep.plugin.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.CustomMessageSlotState
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.host.PluginFailureGuard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal data class PluginCustomMessage(
    val messageId: String,
    val customType: String,
    val payload: JsonObject = JsonObject(emptyMap()),
)

internal object PluginCustomMessageTypes {
    const val SLOT_PREFIX: String = SlotIds.CHAT_MESSAGE_PREFIX

    const val MAX_PAYLOAD_CHARS: Int = 4_000

    private val PATTERN = Regex("^[a-z][a-z0-9_.-]*$")

    private val PRETTY = Json { prettyPrint = true }

    fun slotId(customType: String): String = SlotIds.chatMessage(customType)

    fun customTypeOf(slotId: String): String? = slotId
        .takeIf { it.startsWith(SlotIds.CHAT_MESSAGE_PREFIX) }
        ?.removePrefix(SlotIds.CHAT_MESSAGE_PREFIX)
        ?.takeIf(::isValid)

    fun isValid(customType: String): Boolean = PATTERN.matches(customType)

    fun prettyPayload(payload: JsonObject, maxChars: Int = MAX_PAYLOAD_CHARS): String {
        val text = runCatching { PRETTY.encodeToString(JsonObject.serializer(), payload) }
            .getOrElse { payload.toString() }
        return if (text.length <= maxChars) text else text.take(maxChars) + "\n…"
    }
}

@Composable
internal fun CustomMessageRenderer(
    message: PluginCustomMessage,
    registry: SlotRegistry,
    modifier: Modifier = Modifier,
    failureGuard: PluginFailureGuard? = null,
    onAction: (String) -> Unit = {},
    fallback: @Composable (PluginCustomMessage) -> Unit = { DefaultCustomMessageFallback(it) },
) {
    val state = CustomMessageSlotState(
        messageId = message.messageId,
        customType = message.customType,
        payload = message.payload,
    )
    PluginSlotHost(
        slotId = PluginCustomMessageTypes.slotId(message.customType),
        registry = registry,
        state = state,
        modifier = modifier,
        failureGuard = failureGuard,
        onAction = onAction,
        fallback = { fallback(message) },
    )
}

@Composable
internal fun DefaultCustomMessageFallback(message: PluginCustomMessage) {
    val colors = deepDeepColors()
    val body = PluginCustomMessageTypes.prettyPayload(message.payload)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.wash)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = body.ifBlank { message.customType },
            style = DdType.bodySmall,
            color = colors.inkSecondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}
