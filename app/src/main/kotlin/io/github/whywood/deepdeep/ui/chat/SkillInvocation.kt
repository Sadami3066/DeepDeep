package io.github.whywood.deepdeep.ui.chat

data class SkillInvocation(
    val name: String,
    val userText: String,
)

private val SKILL_BLOCK = Regex(
    """<skill name="([^"]+)" location="([^"]+)">\r?\n[\s\S]*?\r?\n</skill>""",
)

fun parseSkillInvocation(text: String): SkillInvocation? {
    val matches = SKILL_BLOCK.findAll(text).toList()
    if (matches.isEmpty()) return null
    val name = matches.first().groupValues[1].trim()
    if (name.isEmpty()) return null
    return SkillInvocation(
        name = name,
        userText = text.substring(matches.last().range.last + 1).trim(),
    )
}

internal fun stripSkillInvocation(text: String): String {
    val matches = SKILL_BLOCK.findAll(text).toList()
    if (matches.isEmpty()) return text
    val stripped = StringBuilder(text)
    for (match in matches.asReversed()) {
        stripped.delete(match.range.first, match.range.last + 1)
    }
    return stripped.toString().trimStart('\n').trimEnd()
}
