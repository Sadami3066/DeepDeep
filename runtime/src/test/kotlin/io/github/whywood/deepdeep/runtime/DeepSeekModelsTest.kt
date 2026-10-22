package io.github.whywood.deepdeep.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekModelsTest {
    @Test
    fun catalogContainsDefaultModel() {
        val options = DeepSeekModels.options()
        assertTrue(options.isNotEmpty())
        assertTrue(options.any { it.id == DeepSeekModels.defaultModelId() })
    }

    @Test
    fun modelReferenceUsesProviderPrefix() {
        val reference = DeepSeekModels.modelReference(DeepSeekModels.defaultModelId())
        assertTrue(reference.startsWith("deepseek/"))
    }
}
