package io.github.whywood.deepdeep.core

import io.github.whywood.deepdeep.data.AppPreferences
import io.github.whywood.deepdeep.data.SessionNamingMode
import io.github.whywood.deepdeep.runtime.ModelOption
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.runtime.SessionTitleNamer

class SessionTitleService(
    private val catalog: ProviderCatalog,
    private val credentials: SdkCredentialStore,
    private val preferences: AppPreferences,
) : SessionTitleNamer {
    override suspend fun title(firstUserText: String): String? {
        val values = preferences.read()
        val configured = credentials.authKinds.value.keys
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
        val prompt = prompt(firstUserText)
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

    private fun prompt(userText: String): String = buildString {
        append("为下面的用户消息生成一个简短的中文会话标题，最多 12 个字，")
        append("不要引号、标点或解释，只输出标题。\n\n用户消息：\n")
        append(userText.take(2000))
    }

    private fun sanitize(raw: String): String? = sanitizeSessionTitle(raw)
}

internal fun sanitizeSessionTitle(raw: String): String? {

        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
        val stripped = line
            .removePrefix("标题：")
            .removePrefix("标题:")
            .trim()
            .trim('"', '\'', '“', '”', '「', '」', '《', '》', '*', '#', '-', ' ')
            .trimEnd('。', '，', ',', '！', '!', '？', '?', '；', ';', ':')
            .trim('"', '\'', '“', '”', '「', '」', '《', '》', '*', '#', '-', ' ')
            .trimEnd('。', '，', ',', '！', '!', '？', '?', '；', ';', ':')
            .replace(Regex("\\s+"), " ")
        if (stripped.isBlank()) return null
        return if (stripped.length <= 20) stripped else stripped.take(20)
    }
