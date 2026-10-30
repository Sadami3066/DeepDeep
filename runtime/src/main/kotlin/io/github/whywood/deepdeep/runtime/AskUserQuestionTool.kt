package io.github.whywood.deepdeep.runtime

import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val MAX_QUESTIONS = 5
private const val MAX_OPTIONS = 8

private val askUserQuestionParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("questions") {
            put("type", "array")
            put("minItems", 1)
            put("maxItems", MAX_QUESTIONS)
            put("description", "Questions to ask the user, one to five entries")
            put("items", buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("id") {
                        put("type", "string")
                        put("description", "Unique short id for this question, echoed back in the answers")
                    }
                    putJsonObject("header") {
                        put("type", "string")
                        put("description", "Short header shown above the question, at most a few words")
                    }
                    putJsonObject("question") {
                        put("type", "string")
                        put("description", "The question text")
                    }
                    putJsonObject("multi_select") {
                        put("type", "boolean")
                        put("description", "Whether the user may pick more than one option (default false)")
                    }
                    putJsonObject("options") {
                        put("type", "array")
                        put("maxItems", MAX_OPTIONS)
                        put("description", "Selectable options, at most eight. Omit to let the user type a free-form answer.")
                        put("items", buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("label") {
                                    put("type", "string")
                                    put("description", "Option label; append (Recommended) to mark the recommended option")
                                }
                                putJsonObject("description") {
                                    put("type", "string")
                                    put("description", "Optional one-line explanation")
                                }
                            }
                            putJsonArray("required") { add(JsonPrimitive("label")) }
                        })
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("id"))
                    add(JsonPrimitive("question"))
                }
            })
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("questions")) })
}

fun askUserQuestionTool(questionCenter: QuestionCenter, binding: SessionBinding): CodingTool = CodingTool(
    name = "ask_user_question",
    description = "Ask the user one to five structured questions and wait for the answer. Use options for choices and omit options for free-form input. Mark a recommended option by appending (Recommended) to its label.",
    parameters = askUserQuestionParameters,
    label = "ask_user_question",
    promptSnippet = "Ask the user a structured question",
    promptGuidelines = listOf(
        "Use ask_user_question when you truly need the user to choose between approaches or provide missing information.",
        "Ask at most five questions per call and keep each question short.",
        "To recommend an option, append (Recommended) to its label; do not change the labels the user selected.",
    ),
    execute = { toolCallId, args, signal, _ ->
        signal?.throwIfAborted()
        val sessionId = binding.current()?.sessionId
        if (sessionId == null) {
            textResult("Session is not bound", isError = true)
        } else {
            try {
                val questions = parseQuestions(args)
                when (val outcome = questionCenter.ask(sessionId, toolCallId, questions, signal)) {
                    is QuestionOutcome.Answered -> textResult(answersJson(questions, outcome.answers).toString())
                    QuestionOutcome.Cancelled -> textResult("用户取消了提问", isError = true)
                }
            } catch (error: RequestAbortedException) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                textResult(error.message ?: "提问参数无效", isError = true)
            }
        }
    },
)

private fun parseQuestions(args: JsonObject): List<UserQuestion> {
    val array = args["questions"] as? JsonArray
        ?: throw IllegalArgumentException("questions 必须是数组")
    if (array.size !in 1..MAX_QUESTIONS) {
        throw IllegalArgumentException("questions 需要 1..$MAX_QUESTIONS 个问题")
    }
    val ids = mutableSetOf<String>()
    return array.mapIndexed { index, element ->
        val item = element as? JsonObject
            ?: throw IllegalArgumentException("questions[$index] 必须是对象")
        val id = item.stringOrNull("id")?.trim().orEmpty()
        if (id.isEmpty()) {
            throw IllegalArgumentException("questions[$index].id 不能为空")
        }
        if (!ids.add(id)) {
            throw IllegalArgumentException("问题 id 重复：$id")
        }
        val question = item.stringOrNull("question")?.trim().orEmpty()
        if (question.isEmpty()) {
            throw IllegalArgumentException("questions[$index].question 不能为空")
        }
        val optionElements = item["options"] as? JsonArray
        if (optionElements != null && optionElements.size > MAX_OPTIONS) {
            throw IllegalArgumentException("问题 $id 的选项不能超过 $MAX_OPTIONS 个")
        }
        val options = optionElements.orEmpty().mapIndexed { optionIndex, optionElement ->
            val option = optionElement as? JsonObject
                ?: throw IllegalArgumentException("问题 $id 的 options[$optionIndex] 必须是对象")
            val label = option.stringOrNull("label")?.trim().orEmpty()
            if (label.isEmpty()) {
                throw IllegalArgumentException("问题 $id 的 options[$optionIndex].label 不能为空")
            }
            QuestionOption(
                label = label,
                description = option.stringOrNull("description")?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        UserQuestion(
            id = id,
            header = item.stringOrNull("header")?.trim()?.takeIf { it.isNotEmpty() },
            question = question,
            options = options,
            multiSelect = item.boolOrNull("multi_select") ?: item.boolOrNull("multiSelect") ?: false,
        )
    }
}

private fun answersJson(questions: List<UserQuestion>, answers: List<QuestionAnswer>): JsonObject = buildJsonObject {
    val byQuestion = answers.associateBy { it.questionId }
    putJsonArray("answers") {
        questions.forEach { question ->
            val answer = byQuestion[question.id] ?: return@forEach
            if (answer.skipped) return@forEach
            val selected = answer.selected.map { it.trim() }.filter { it.isNotEmpty() }
            val custom = answer.custom?.trim()?.takeIf { it.isNotEmpty() }
            if (selected.isEmpty() && custom == null) return@forEach
            add(buildJsonObject {
                put("id", question.id)
                putJsonArray("selected") { selected.forEach { add(JsonPrimitive(it)) } }
                if (custom != null) {
                    put("custom", custom)
                }
            })
        }
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

private fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
