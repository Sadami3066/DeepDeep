package io.github.whywood.deepdeep.data

const val MAX_SHARE_STREAMS = 20

private const val ACTION_SEND = "android.intent.action.SEND"

data class SharePayload(
    val streams: List<String> = emptyList(),
    val text: String = "",
    val title: String = "",
) {
    val isEmpty: Boolean get() = streams.isEmpty() && text.isBlank()
}

fun parseSharePayload(
    action: String?,
    type: String?,
    streamUris: List<String> = emptyList(),
    clipUris: List<String> = emptyList(),
    text: String? = null,
    title: String? = null,
): SharePayload {
    val uris = (streamUris.asSequence() + clipUris.asSequence())
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_SHARE_STREAMS)
        .toList()
    return SharePayload(
        streams = if (action == ACTION_SEND) uris.take(1) else uris,
        text = text?.trim().orEmpty(),
        title = title?.trim().orEmpty(),
    )
}
