package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.UserQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionRecordTest {
    private val questions = """
        [
          {
            "id": "modules",
            "header": "范围",
            "question": "要改哪些模块？",
            "multi_select": true,
            "options": [
              {"label": "app", "description": "界面层"},
              {"label": "runtime (Recommended)"}
            ]
          },
          {"id": "style", "question": "视觉风格？", "options": [{"label": "极简"}]},
          {"id": "notes", "header": "补充", "question": "还有什么要说明的？"}
        ]
    """.trimIndent()

    @Test
    fun parsesQuestionsAndAnswers() {
        val record = requireNotNull(
            parseQuestionRecord(
                trace(
                    arguments = mapOf("questions" to questions),
                    output = """
                        {"answers":[
                          {"id":"notes","selected":[],"custom":"注意暗色模式"},
                          {"id":"modules","selected":["app","runtime (Recommended)"],"custom":"再加一句"}
                        ]}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(3, record.questions.size)
        assertEquals(
            UserQuestion(
                id = "modules",
                header = "范围",
                question = "要改哪些模块？",
                options = listOf(
                    QuestionOption(label = "app", description = "界面层"),
                    QuestionOption(label = "runtime (Recommended)"),
                ),
                multiSelect = true,
            ),
            record.questions[0],
        )
        assertNull(record.questions[1].header)
        assertFalse(record.questions[1].multiSelect)
        assertTrue(record.questions[2].options.isEmpty())

        assertEquals(listOf("modules", "style", "notes"), record.answers.map { it.questionId })
        assertEquals(listOf("app", "runtime (Recommended)"), record.answers[0].selected)
        assertEquals("再加一句", record.answers[0].custom)
        assertFalse(record.answers[0].skipped)
        assertTrue(record.answers[2].selected.isEmpty())
        assertEquals("注意暗色模式", record.answers[2].custom)
    }

    @Test
    fun missingAnswerIsSkipped() {
        val record = requireNotNull(
            parseQuestionRecord(
                trace(
                    arguments = mapOf("questions" to questions),
                    output = """{"answers":[{"id":"modules","selected":["app"]}]}""",
                ),
            ),
        )
        assertFalse(record.answers[0].skipped)
        assertTrue(record.answers[1].skipped)
        assertTrue(record.answers[1].selected.isEmpty())
        assertNull(record.answers[1].custom)
        assertTrue(record.answers[2].skipped)
    }

    @Test
    fun malformedJsonReturnsNull() {
        assertNull(
            parseQuestionRecord(
                trace(mapOf("questions" to "[{"), output = """{"answers":[]}"""),
            ),
        )
        assertNull(
            parseQuestionRecord(
                trace(mapOf("questions" to questions), output = "not json"),
            ),
        )
        assertNull(
            parseQuestionRecord(
                trace(mapOf("questions" to questions), output = """{"answers":{}}"""),
            ),
        )
    }

    @Test
    fun otherToolOrMissingOutputReturnsNull() {
        assertNull(
            parseQuestionRecord(
                trace(mapOf("questions" to questions), output = """{"answers":[]}""", name = "bash"),
            ),
        )
        assertNull(
            parseQuestionRecord(
                trace(mapOf("questions" to questions), output = null),
            ),
        )
        assertNull(
            parseQuestionRecord(
                trace(emptyMap(), output = """{"answers":[]}"""),
            ),
        )
    }

    private fun trace(
        arguments: Map<String, String>,
        output: String?,
        name: String = "ask_user_question",
    ): ToolTrace = ToolTrace(id = 1L, name = name, arguments = arguments, output = output)
}
