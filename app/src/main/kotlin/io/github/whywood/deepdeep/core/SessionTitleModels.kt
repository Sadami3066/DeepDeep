package io.github.whywood.deepdeep.core

import io.github.whywood.deepdeep.runtime.ModelOption

object SessionTitleModels {
    fun score(model: ModelOption): Int {
        val key = "${model.id} ${model.displayName}".lowercase()
        return when {
            "deepseek" in key && "flash" in key -> 100
            "flash" in key -> 90
            "haiku" in key -> 80
            "terra" in key -> 70
            else -> 0
        }
    }

    fun rank(models: List<ModelOption>): List<ModelOption> = models.sortedWith(
        compareByDescending<ModelOption> { score(it) }
            .thenBy { it.providerId }
            .thenBy { it.displayName },
    )

    fun candidates(models: List<ModelOption>): List<ModelOption> =
        rank(models).filter { score(it) > 0 }

    fun preferred(models: List<ModelOption>): ModelOption? = candidates(models).firstOrNull()
}
