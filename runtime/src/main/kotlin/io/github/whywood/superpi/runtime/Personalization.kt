package io.github.whywood.superpi.runtime

import io.github.whywood.superpi.sdk.coding.prompt.DEFAULT_SYSTEM_PROMPT_PREAMBLE
import java.time.OffsetDateTime

data class Personalization(
    val nickname: String = "Milous",
    val instruction: String = "",
)

internal fun buildPersonalizationSections(
    personalization: Personalization,
    providerId: String,
    modelId: String,
    now: OffsetDateTime,
    environmentLines: List<String>? = null,
): Map<String, String> {
    val nickname = personalization.nickname.trim()
    val sections = linkedMapOf<String, String>()
    sections["environment"] = wrapPromptSection(
        "environment",
        buildString {
            appendLine("Date: ${now.toLocalDate()} (UTC${now.offset.id}).")
            appendLine("Model: $providerId/$modelId.")
            if (!environmentLines.isNullOrEmpty()) {
                environmentLines.forEach { appendLine(it) }
            } else {
                appendLine("Env: Alpine Linux via proot and Android.")
            }
            if (nickname.isNotEmpty()) {
                appendLine("Nickname: $nickname.")
            } else {
                appendLine("Address the user as \"you\".")
            }
        }.trim(),
    )

    val instruction = personalization.instruction.trim()
    if (instruction.isNotEmpty()) {
        sections["instruction"] = wrapPromptSection("instruction", "User's instruction:\n\n$instruction")
    }
    return sections
}

internal fun buildSessionSystemPromptSections(
    personalization: Personalization,
    providerId: String,
    modelId: String,
    now: OffsetDateTime,
    pluginBody: String?,
    environmentLines: List<String>? = null,
    promptPreamble: String? = null,
    promptRules: String? = null,
    extra: Map<String, String> = emptyMap(),
): Map<String, String> {
    val sections = LinkedHashMap(
        buildPersonalizationSections(personalization, providerId, modelId, now, environmentLines),
    )
    val body = pluginBody?.trim().orEmpty()
    if (body.isNotEmpty()) {
        sections["plugins"] = wrapPromptSection("plugins", body)
    }
    sections["preamble"] = promptPreamble?.trim()?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_SYSTEM_PROMPT_PREAMBLE.replace("SuperPI", "PI")
    promptRules?.trim()?.takeIf { it.isNotEmpty() }?.let { sections["rules"] = wrapPromptSection("rules", it) }
    sections.putAll(extra)
    return sections
}

internal fun wrapPromptSection(name: String, content: String, escape: Boolean = true): String =
    "<$name>\n${if (escape) escapePromptXml(content) else content}\n</$name>"

internal fun escapePromptXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
