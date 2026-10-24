package io.github.whywood.deepdeep.runtime

import java.time.OffsetDateTime

enum class InteractionStyle {
    QUIET,
    BALANCED,
    PROACTIVE,
}

data class Personalization(
    val nickname: String = "Milous",
    val interaction: InteractionStyle = InteractionStyle.BALANCED,
    val instruction: String = "",
)

internal fun buildPersonalizationSections(
    personalization: Personalization,
    providerId: String,
    modelId: String,
    cwd: String,
    now: OffsetDateTime,
): Map<String, String> {
    val nickname = escapePromptXml(personalization.nickname.trim())
    val sections = linkedMapOf<String, String>()
    sections["environment"] = wrapPromptSection(
        "environment",
        buildString {
            appendLine("当前时间：${escapePromptXml(now.toString())}。")
            appendLine("模型：${escapePromptXml(providerId)}/${escapePromptXml(modelId)}。")
            appendLine("工作区：${escapePromptXml(cwd)}。")
            if (nickname.isEmpty()) {
                appendLine("直接称呼「你」，不要使用名字。")
            } else {
                appendLine("称呼用户为「$nickname」。")
            }
            appendLine("当前交互风格：${interactionLabel(personalization.interaction)}。")
            appendLine(interactionInstructions(personalization.interaction))
        }.trim(),
    )

    val instruction = personalization.instruction.trim()
    if (instruction.isNotEmpty()) {
        val body = buildString {
            appendLine("以下是用户长期偏好。在不违背事实、安全和系统指令的前提下遵守；若冲突，以系统指令为准。")
            appendLine()
            appendLine(escapePromptXml(instruction))
        }.trim()
        sections["instruction"] = wrapPromptSection("instruction", body)
    }
    return sections
}

private fun interactionLabel(style: InteractionStyle): String = when (style) {
    InteractionStyle.QUIET -> "安静"
    InteractionStyle.BALANCED -> "默认"
    InteractionStyle.PROACTIVE -> "主动"
}

private fun interactionInstructions(style: InteractionStyle): String = when (style) {
    InteractionStyle.QUIET ->
        "少提问、少打断。能根据上下文合理推断的就直接做，做完简要说明假设。只有缺少关键信息且无法安全推断时才提问，一次最多一个问题。不要主动追加与当前任务无关的建议。"

    InteractionStyle.BALANCED ->
        "必要时提问，一次最多一个问题；能自行判断的不要打断。任务完成后可以给一条简短的下一步建议，但不要反复追问。"

    InteractionStyle.PROACTIVE ->
        "更主动地澄清关键歧义，必要时追问到任务清楚；任务完成后主动给出可执行的下一步或可选方案。不要为了表现主动而问无意义的问题。"
}

internal fun wrapPromptSection(name: String, content: String): String =
    "<$name>\n$content\n</$name>"

internal fun escapePromptXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
