package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AskUserQuestionToolTest {
    private fun questionsArgs(vararg builders: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            putJsonArray("questions") {
                builders.forEach { builder ->
                    add(buildJsonObject(builder))
                }
            }
        }

    private fun resultText(result: AgentToolResult): String =
        result.content.filterIsInstance<TextContent>().joinToString("") { it.text }

    private fun answerEntries(result: AgentToolResult): JsonArray =
        Json.parseToJsonElement(resultText(result)).jsonObject.getValue("answers").jsonArray

    @Test
    fun toolExposesDshQuestionSchema() {
        val tool = askUserQuestionTool(QuestionCenter { false })
        assertEquals("ask_user_question", tool.name)
        val properties = tool.parameters.getValue("properties").jsonObject
        val questions = properties.getValue("questions").jsonObject
        assertEquals("array", (questions["type"] as JsonPrimitive).content)
        assertEquals("5", (questions["maxItems"] as JsonPrimitive).content)
        val questionProperties = questions.getValue("items").jsonObject.getValue("properties").jsonObject
        assertTrue(questionProperties.containsKey("multi_select"))
        assertFalse(questionProperties.containsKey("multiSelect"))
    }

    @Test
    fun invalidArgumentsReturnErrorText() = runTest {
        val tool = askUserQuestionTool(QuestionCenter { false })
        val result = tool.execute(
            "call-invalid",
            buildJsonObject { putJsonArray("questions") {} },
            null,
            null,
        )
        assertEquals(true, result.isError)
        assertTrue(resultText(result).isNotBlank())
    }

    @Test
    fun answersAreSerializedForTheModel() = runTest {
        val center = QuestionCenter { true }
        val tool = askUserQuestionTool(center)
        var result: AgentToolResult? = null
        val args = questionsArgs(
            {
                put("id", "q1")
                put("question", "Pick a color")
                putJsonArray("options") {
                    add(buildJsonObject { put("label", "Blue") })
                    add(buildJsonObject { put("label", "Green (Recommended)") })
                }
            },
            {
                put("id", "q2")
                put("question", "Anything else?")
                put("multi_select", true)
            },
        )
        val job = launch {
            result = tool.execute("call-1", args, null, null)
        }
        runCurrent()

        val pending = center.pending.value.single()
        assertEquals(2, pending.questions.size)
        assertTrue(pending.questions[0].options[1].label.endsWith("(Recommended)"))
        assertTrue(pending.questions[1].multiSelect)

        center.answer(
            pending.requestId,
            listOf(
                QuestionAnswer(questionId = "q1", selected = listOf("Green (Recommended)")),
                QuestionAnswer(questionId = "q2", custom = "   "),
            ),
        )
        job.join()

        val toolResult = requireNotNull(result)
        assertNull(toolResult.isError)
        val entries = answerEntries(toolResult)
        assertEquals(1, entries.size)
        val entry = entries[0].jsonObject
        assertEquals("q1", (entry["id"] as JsonPrimitive).content)
        assertEquals(
            listOf("Green (Recommended)"),
            (entry["selected"] as JsonArray).map { (it as JsonPrimitive).content },
        )
        assertFalse(entry.containsKey("custom"))
        assertFalse(entry.containsKey("question"))
        assertFalse(entry.containsKey("answers"))
    }

    @Test
    fun customTextAndCamelCaseMultiSelectFallbackAreSupported() = runTest {
        val center = QuestionCenter { true }
        val tool = askUserQuestionTool(center)
        var result: AgentToolResult? = null
        val args = questionsArgs(
            {
                put("id", "q1")
                put("question", "Free input")
                put("multiSelect", true)
            },
        )
        val job = launch {
            result = tool.execute("call-2", args, null, null)
        }
        runCurrent()
        val pending = center.pending.value.single()
        assertTrue(pending.questions[0].multiSelect)
        center.answer(
            pending.requestId,
            listOf(QuestionAnswer(questionId = "q1", custom = "  custom answer  ")),
        )
        job.join()
        val entry = answerEntries(requireNotNull(result))[0].jsonObject
        assertEquals("custom answer", (entry["custom"] as JsonPrimitive).content)
        assertEquals(0, (entry["selected"] as JsonArray).size)
    }

    @Test
    fun skippedQuestionsAreOmitted() = runTest {
        val center = QuestionCenter { true }
        val tool = askUserQuestionTool(center)
        var result: AgentToolResult? = null
        val args = questionsArgs(
            {
                put("id", "single")
                put("question", "Keep going?")
            },
        )
        val job = launch {
            result = tool.execute("call-3", args, null, null)
        }
        runCurrent()
        center.answer(center.pending.value.single().requestId, listOf(QuestionAnswer("single", skipped = true)))
        job.join()
        assertTrue(answerEntries(requireNotNull(result)).isEmpty())
    }
}
