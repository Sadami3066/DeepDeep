package io.github.whywood.deepdeep.runtime

data class QuestionOption(
    val label: String,
    val description: String? = null,
)

data class UserQuestion(
    val id: String,
    val header: String? = null,
    val question: String,
    val options: List<QuestionOption> = emptyList(),
    val multiSelect: Boolean = false,
)
