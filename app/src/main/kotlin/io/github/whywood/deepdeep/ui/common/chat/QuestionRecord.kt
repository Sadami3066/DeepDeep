package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.UserQuestion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class QuestionRecord(
    val questions: List<UserQuestion>,
    val answers: List<QuestionAnswer>,
)

private const val ASK_USER_QUESTION = "ask_user_question"

fun parseQuestionRecord(trace: ToolTrace): QuestionRecord? {
    if (trace.name != ASK_USER_QUESTION) return null
    val rawQuestions = trace.arguments["questions"]?.takeIf { it.isNotBlank() } ?: return null
    val rawOutput = trace.output?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val questions = parseQuestions(rawQuestions)
        QuestionRecord(questions = questions, answers = parseAnswers(rawOutput, questions))
    } catch (_: Exception) {
        null
    }
}

private fun parseQuestions(raw: String): List<UserQuestion> {
    val array = Json.parseToJsonElement(raw).jsonArray
    if (array.isEmpty()) error("questions 不能为空")
    return array.mapIndexed { index, element ->
        val item = element.jsonObject
        val id = item.string("id")?.trim().orEmpty()
        if (id.isEmpty()) error("questions[$index].id 不能为空")
        val question = item.string("question")?.trim().orEmpty()
        if (question.isEmpty()) error("questions[$index].question 不能为空")
        val options = (item["options"] as? JsonArray)?.mapIndexed { optionIndex, optionElement ->
            val option = optionElement.jsonObject
            val label = option.string("label")?.trim().orEmpty()
            if (label.isEmpty()) error("questions[$index].options[$optionIndex].label 不能为空")
            QuestionOption(
                label = label,
                description = option.string("description")?.trim()?.takeIf { it.isNotEmpty() },
            )
        }.orEmpty()
        UserQuestion(
            id = id,
            header = item.string("header")?.trim()?.takeIf { it.isNotEmpty() },
            question = question,
            options = options,
            multiSelect = item.boolean("multi_select") ?: item.boolean("multiSelect") ?: false,
        )
    }
}

private fun parseAnswers(raw: String, questions: List<UserQuestion>): List<QuestionAnswer> {
    val array = Json.parseToJsonElement(raw).jsonObject["answers"] as? JsonArray
        ?: error("answers 必须是数组")
    val items = array.associate { element ->
        val item = element.jsonObject
        val id = item.string("id")?.trim().orEmpty()
        if (id.isEmpty()) error("answers[].id 不能为空")
        id to item
    }
    return questions.map { question ->
        val item = items[question.id]
        if (item == null) {
            QuestionAnswer(questionId = question.id, skipped = true)
        } else {
            val selected = (item["selected"] as? JsonArray)?.mapNotNull { element ->
                ((element as? JsonPrimitive)?.contentOrNull)?.trim()?.takeIf { it.isNotEmpty() }
            }.orEmpty()
            QuestionAnswer(
                questionId = question.id,
                selected = selected,
                custom = item.string("custom")?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull
