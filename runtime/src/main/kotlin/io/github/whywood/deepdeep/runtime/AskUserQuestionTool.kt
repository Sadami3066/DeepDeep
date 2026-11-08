package io.github.whywood.deepdeep.runtime

import io.github.whywood.deepdeep.sdk.ai.RequestAbortedException
import io.github.whywood.deepdeep.sdk.coding.tools.CodingTool
import io.github.whywood.deepdeep.sdk.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
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
private const val ANSWER_WAIT_TIMEOUT_MS = 10 * 60_000L

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
                    putJsonObject("multiSelect") {
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
    description = "Ask the user one to five structured questions and wait for answers. Use options for choices; omit options for free-form input. Append (Recommended) to a recommended label. Ask only when a real choice or missing fact blocks progress.",
    parameters = askUserQuestionParameters,
    label = "ask_user_question",
    execute = { toolCallId, args, signal, _ ->
        signal?.throwIfAborted()
        val sessionId = binding.current()?.sessionId
        if (sessionId == null) {
            textResult("Session is not bound", isError = true)
        } else {
            try {
                val questions = parseQuestions(args)
                val outcome = withTimeoutOrNull(ANSWER_WAIT_TIMEOUT_MS) {
                    questionCenter.ask(sessionId, toolCallId, questions, signal)
                }
                when (outcome) {
                    null -> textResult("Timed out waiting for the user's answer.")
                    is QuestionOutcome.Answered -> textResult(answersJson(questions, outcome.answers).toString())
                    QuestionOutcome.Cancelled -> textResult("The user cancelled the question", isError = true)
                }
            } catch (error: RequestAbortedException) {
                throw error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                textResult(error.message ?: "Invalid question parameters", isError = true)
            }
        }
    },
)

private fun parseQuestions(args: JsonObject): List<UserQuestion> {
    val array = args["questions"] as? JsonArray
        ?: throw IllegalArgumentException("questions must be an array")
    if (array.size !in 1..MAX_QUESTIONS) {
        throw IllegalArgumentException("questions needs 1..$MAX_QUESTIONS items")
    }
    val ids = mutableSetOf<String>()
    return array.mapIndexed { index, element ->
        val item = element as? JsonObject
            ?: throw IllegalArgumentException("questions[$index] must be an object")
        val id = item.stringOrNull("id")?.trim().orEmpty()
        if (id.isEmpty()) {
            throw IllegalArgumentException("questions[$index].id must not be empty")
        }
        if (!ids.add(id)) {
            throw IllegalArgumentException("Duplicate question id: $id")
        }
        val question = item.stringOrNull("question")?.trim().orEmpty()
        if (question.isEmpty()) {
            throw IllegalArgumentException("questions[$index].question must not be empty")
        }
        val optionElements = item["options"] as? JsonArray
        if (optionElements != null && optionElements.size > MAX_OPTIONS) {
            throw IllegalArgumentException("Question $id has more than $MAX_OPTIONS options")
        }
        val options = optionElements.orEmpty().mapIndexed { optionIndex, optionElement ->
            val option = optionElement as? JsonObject
                ?: throw IllegalArgumentException("Question $id options[$optionIndex] must be an object")
            val label = option.stringOrNull("label")?.trim().orEmpty()
            if (label.isEmpty()) {
                throw IllegalArgumentException("Question $id options[$optionIndex].label must not be empty")
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
            multiSelect = item.boolOrNull("multiSelect") ?: item.boolOrNull("multi_select") ?: false,
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
