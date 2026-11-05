package io.github.whywood.deepdeep.core

import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ModelSettings
import io.github.whywood.deepdeep.runtime.ProviderDirectory
import io.github.whywood.deepdeep.runtime.ThinkingLevel

internal fun resolve(
    current: ModelConfig,
    configured: Set<String>,
    catalog: ProviderDirectory,
): ModelConfig? {
    if (configured.isEmpty() || current.providerId in configured) return null
    val target = catalog.summaries.firstOrNull { it.id in configured } ?: return null
    return catalog.resolveConfig(
        ModelConfig(providerId = target.id, modelId = "", thinkingLevel = ThinkingLevel.OFF),
    )
}

class ModelSelection(
    private val catalog: ProviderDirectory,
    private val settings: ModelSettings,
) {
    suspend fun ensure(configured: Set<String>): ModelConfig? {
        val current = settings.read()
        val next = resolve(current, configured, catalog) ?: return null
        if (next == current) return null
        settings.save(next)
        return next
    }
}
