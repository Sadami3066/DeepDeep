package io.github.whywood.superpi.runtime.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRecapPromptTest {
    @Test
    fun promptAsksForTheUserLanguageAndKeepsSkipRule() {
        val prompt = buildRecapPrompt("User: 帮我改一下登录页面\n\nAssistant: 好的")

        assertTrue(prompt.contains("language the user used in the conversation"))
        assertTrue(prompt.contains("do not translate the memory into English"))
        assertTrue(prompt.contains("output only SKIP"))
        assertTrue(prompt.contains("<conversation>\nUser: 帮我改一下登录页面"))
    }
}
