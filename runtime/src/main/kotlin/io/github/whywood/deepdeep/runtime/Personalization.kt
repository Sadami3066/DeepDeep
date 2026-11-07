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
            appendLine("Date: ${escapePromptXml(now.toLocalDate().toString())} (UTC${escapePromptXml(now.offset.id)}).")
            appendLine("Model: ${escapePromptXml(providerId)}/${escapePromptXml(modelId)}.")
            appendLine("Working directory: ${escapePromptXml(cwd)}.")
            if (nickname.isNotEmpty()) {
                appendLine("Nickname: ${escapePromptXml(nickname)}.")
            } else {
                appendLine("Address the user as \"you\".")
            }
            appendLine("Interaction style: ${interactionLabel(personalization.interaction)}.")
            appendLine(interactionInstructions(personalization.interaction))
        }.trim(),
    )

    val instruction = personalization.instruction.trim()
    if (instruction.isNotEmpty()) {
        val body = buildString {
            appendLine("User's instruction:")
            appendLine()
            appendLine(escapePromptXml(instruction))
        }.trim()
        sections["instruction"] = wrapPromptSection("instruction", body)
    }
    return sections
}

internal fun buildSessionSystemPromptSections(
    personalization: Personalization,
    providerId: String,
    modelId: String,
    cwd: String,
    now: OffsetDateTime,
    sharedStorage: Boolean,
    pluginBody: String?,
    extra: Map<String, String> = emptyMap(),
): Map<String, String> {
    val sections = LinkedHashMap(buildPersonalizationSections(personalization, providerId, modelId, cwd, now))
    if (sharedStorage) sections.putAll(sharedStorageSection())
    val body = pluginBody?.trim().orEmpty()
    if (body.isNotEmpty()) {
        sections["plugins"] = wrapPromptSection("plugins", escapePromptXml(body))
    }
    sections.putAll(extra)
    return sections
}

internal fun sharedStorageSection(): Map<String, String> = mapOf(
    "shared_storage" to wrapPromptSection(
        "shared_storage",
        listOf(
            "Android shared storage is available at /storage/emulated/0; file operations there are allowed.",
            "Do not delete or overwrite user files there unless the user asks.",
        ).joinToString("\n") { "- $it" },
    ),
)

private fun interactionLabel(style: InteractionStyle): String = when (style) {
    InteractionStyle.QUIET -> "Quiet"
    InteractionStyle.BALANCED -> "Balanced"
    InteractionStyle.PROACTIVE -> "Proactive"
}

private fun interactionInstructions(style: InteractionStyle): String = when (style) {
    InteractionStyle.QUIET ->
        "Be quiet: act directly and do not ask questions unless the intent is unclear or the action is risky."

    InteractionStyle.BALANCED ->
        "Be balanced: act directly when the request is clear; ask when the intent is ambiguous or the action is risky."

    InteractionStyle.PROACTIVE ->
        "Be proactive: ask more, suggest more, and act on likely follow-ups."
}

internal fun wrapPromptSection(name: String, content: String): String =
    "<$name>\n$content\n</$name>"

internal fun escapePromptXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
