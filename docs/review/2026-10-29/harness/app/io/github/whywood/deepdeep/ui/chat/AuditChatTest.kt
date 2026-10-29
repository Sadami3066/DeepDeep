package io.github.whywood.deepdeep.ui.chat

import io.github.whywood.deepdeep.runtime.*
import org.junit.Assert.*
import org.junit.Test

class AuditChatTest {
    @Test fun markdownAndReducerScaling() {
        val part = "# Title\n\nParagraph **bold** and [link](https://example.test).\n\n- one\n- two\n\n|A|B|\n|---|---|\n|1|2|\n\n```kotlin\nval x = 1\n```\n\n"
        for (copies in listOf(10,100,1000)) {
            val text = part.repeat(copies)
            repeat(10) { parseMarkdownDocument(text) }
            val times = List(21) {
                val start = System.nanoTime()
                assertTrue(parseMarkdownDocument(text).blocks.isNotEmpty())
                (System.nanoTime()-start)/1e6
            }.sorted()
            println("AUDIT_PERF markdown chars=${text.length} median_ms=${times[10]} p95_ms=${times[19]}")
        }
        for (count in listOf(100,1000,10000)) {
            val history = List(count) { AssistantMessage(it.toLong(),AssistantRole.USER,"history") } + AssistantMessage(count.toLong(),AssistantRole.ASSISTANT,"",streaming=true,runId="audit")
            var state = ConversationState(messages=history,sending=true)
            repeat(20) { state=reduce(state,RuntimeEvent.TextDelta("audit","x"),ChatUiStrings.fallback()) }
            val times = List(21) {
                val start = System.nanoTime()
                repeat(100) { state=reduce(state,RuntimeEvent.TextDelta("audit","x"),ChatUiStrings.fallback()) }
                (System.nanoTime()-start)/1e6
            }.sorted()
            println("AUDIT_PERF reducer history=$count deltas=100 median_ms=${times[10]} p95_ms=${times[19]}")
        }
    }
}
