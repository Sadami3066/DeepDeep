package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.AssistantMessage

private const val RAW_ERROR_MAX_CHARS = 300

internal fun AssistantMessage.localizedRunError(localize: (String?) -> String): AssistantMessage =
    rawError?.let { raw -> copy(error = localize(raw)) } ?: this

internal fun rawErrorDetails(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    return text.take(RAW_ERROR_MAX_CHARS).takeIf { it.isNotBlank() }
}
