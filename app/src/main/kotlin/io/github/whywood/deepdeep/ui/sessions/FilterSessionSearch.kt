package io.github.whywood.deepdeep.ui.sessions

import io.github.whywood.deepdeep.ui.chat.SessionSearchHit

internal fun filterSessionSearch(
    hits: List<SessionSearchHit>,
    query: String,
): List<SessionSearchHit> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return hits
    return hits.filter { hit -> hit.conversation.title.contains(trimmed, ignoreCase = true) }
}
