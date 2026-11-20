package io.github.whywood.superpi.core

import io.github.whywood.superpi.data.AppPreferences
import io.github.whywood.superpi.data.SessionNamingMode
import io.github.whywood.superpi.runtime.ModelOption
import io.github.whywood.superpi.runtime.ProviderCatalog
import io.github.whywood.superpi.runtime.SessionTitleNamer

class SessionTitleService(
    private val catalog: ProviderCatalog,
    private val configuredProviders: () -> Set<String>,
    private val preferences: AppPreferences,
) : SessionTitleNamer {
    override suspend fun title(firstUserText: String): String? {
        val values = preferences.read()
        val configured = configuredProviders()
        val candidates = when (values.metadataMode) {
            SessionNamingMode.AUTO -> SessionTitleModels.candidates(
                configured.flatMap { providerId -> catalog.models(providerId) },
            )
            SessionNamingMode.MANUAL -> listOfNotNull(
                manualModel(values.metadataProviderId, values.metadataModelId, configured),
            )
        }
        if (candidates.isEmpty()) {
            android.util.Log.w("SessionTitle", "no title model mode=${values.metadataMode} configured=$configured")
            return null
        }
        val prompt = sessionTitlePrompt(firstUserText)
        for (model in candidates) {
            val raw = runCatching { catalog.complete(model.providerId, model.id, prompt) }
                .onFailure { android.util.Log.w("SessionTitle", "completion failed ${model.providerId}/${model.id}", it) }
                .getOrNull() ?: continue
            val title = sanitize(raw) ?: continue
            return title
        }
        return null
    }

    private fun manualModel(providerId: String, modelId: String, configured: Set<String>): ModelOption? {
        if (providerId.isBlank() || modelId.isBlank() || providerId !in configured) return null
        return catalog.models(providerId).firstOrNull { it.id == modelId }
    }

    private fun sanitize(raw: String): String? = sanitizeSessionTitle(raw)
}

internal fun sessionTitlePrompt(userText: String): String = buildString {
    append("Write a short session title for the user message below, at most 6 words. ")
    append("Use the language the user wrote in and keep names and terms as the user wrote them. ")
    append("Output only the title: no quotes, punctuation or explanation.\n\nUser message:\n")
    append(userText.take(2000))
}

internal fun sanitizeSessionTitle(raw: String): String? {
    val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
    val stripped = line
        .removePrefix("Title:")
        .removePrefix("Title：")
        .removePrefix("标题：")
        .removePrefix("标题:")
        .trim()
        .trim('"', '\'', '“', '”', '「', '」', '《', '》', '*', '#', '-', ' ')
        .trimEnd('.', '。', '，', ',', '！', '!', '？', '?', '；', ';', ':')
        .trim('"', '\'', '“', '”', '「', '」', '《', '》', '*', '#', '-', ' ')
        .trimEnd('.', '。', '，', ',', '！', '!', '？', '?', '；', ';', ':')
        .replace(Regex("\\s+"), " ")
    if (stripped.isBlank()) return null
    return if (stripped.length <= 48) stripped else stripped.take(48).trimEnd()
}
