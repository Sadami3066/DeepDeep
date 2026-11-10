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
    now: OffsetDateTime,
    rootBash: Boolean = false,
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
            } else if (rootBash) {
                appendLine("Env: Alpine Linux chroot as real root (uid 0), Android filesystems at native paths.")
                appendLine("- /data (rw): Android /data. Other apps' private data: /data/data/<package>.")
                appendLine("- /system /vendor /product /system_ext /odm (ro): Android system files.")
                appendLine("- /sdcard = /storage/emulated/0 (rw), same data as /data/media/0.")
                appendLine("- /proc /sys /dev: host.")
                appendLine("/system/bin is on PATH: run pm, cmd, settings, dumpsys and getprop directly.")
                appendLine("read, edit and write reach the same native tree as bash; pass absolute paths without an /android prefix.")
            } else {
                appendLine("Env: Alpine Linux via proot and Android.")
            }
            if (nickname.isNotEmpty()) {
                appendLine("Nickname: $nickname.")
            } else {
                appendLine("Address the user as \"you\".")
            }
            appendLine("Interaction style: ${interactionLabel(personalization.interaction)}.")
            appendLine(interactionInstructions(personalization.interaction))
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
    rootBash: Boolean = false,
    environmentLines: List<String>? = null,
    extra: Map<String, String> = emptyMap(),
): Map<String, String> {
    val sections = LinkedHashMap(
        buildPersonalizationSections(personalization, providerId, modelId, now, rootBash, environmentLines),
    )
    val body = pluginBody?.trim().orEmpty()
    if (body.isNotEmpty()) {
        sections["plugins"] = wrapPromptSection("plugins", body)
    }
    sections.putAll(extra)
    return sections
}

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

internal fun wrapPromptSection(name: String, content: String, escape: Boolean = true): String =
    "<$name>\n${if (escape) escapePromptXml(content) else content}\n</$name>"

internal fun escapePromptXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
