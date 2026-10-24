package io.github.whywood.deepdeep.ui.chat

data class SkillInvocation(
    val name: String,
    val userText: String,
)

private val SKILL_BLOCK = Regex(
    """<skill name="([^"]+)" location="([^"]+)">\r?\n[\s\S]*?\r?\n</skill>""",
)

fun parseSkillInvocation(text: String): SkillInvocation? {
    val match = SKILL_BLOCK.find(text) ?: return null
    val name = match.groupValues[1].trim()
    if (name.isEmpty()) return null
    return SkillInvocation(
        name = name,
        userText = text.substring(match.range.last + 1).trim(),
    )
}

internal fun stripSkillInvocation(text: String): String {
    val match = SKILL_BLOCK.find(text) ?: return text
    return text.removeRange(match.range).trimStart('\n').trimEnd()
}
