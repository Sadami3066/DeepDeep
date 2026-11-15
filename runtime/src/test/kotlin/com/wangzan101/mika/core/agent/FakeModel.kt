package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ChatModel
import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.ToolSpec

// Extracted unchanged from the Phongent AgentLoopTest fixture.
class FakeModel(vararg script: Any, private val repeatLast: Boolean = false, contextTokens: Int = 128_000, groundingVerified: Boolean = true) : ChatModel {
    override val capabilities = Capabilities(toolCalls = true, vision = false, contextTokens = contextTokens, reasoning = false, groundingVerified = groundingVerified)
    private val script = script.toMutableList()
    val requests = mutableListOf<List<Message>>()
    val offeredTools = mutableListOf<List<String>>()
    var system: String? = null

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
        this.system = system
        requests += messages
        offeredTools += tools.map { it.name }
        val next = if (repeatLast && script.size == 1) script[0] else script.removeAt(0)
        if (next is Throwable) throw next
        return next as ModelReply
    }
}
