package io.github.whywood.deepdeep.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class CapabilitySnapshot(
    val tools: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
) {
    companion object {
        fun of(tools: Collection<String>, skills: Collection<String>): CapabilitySnapshot = CapabilitySnapshot(
            tools = tools.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
            skills = skills.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
        )
    }
}

internal object CapabilityNotices {
    const val MAX_NAMES_PER_GROUP = 6

    fun diff(previous: CapabilitySnapshot, current: CapabilitySnapshot): CapabilityDelta? {
        val delta = CapabilityDelta(
            toolsAdded = current.tools.filterNot { it in previous.tools },
            toolsRemoved = previous.tools.filterNot { it in current.tools },
            skillsAdded = current.skills.filterNot { it in previous.skills },
            skillsRemoved = previous.skills.filterNot { it in current.skills },
        )
        return delta.takeUnless { it.isEmpty }
    }

    fun message(delta: CapabilityDelta): String {
        val parts = mutableListOf<String>()
        if (delta.toolsAdded.isNotEmpty()) parts += "added tools: " + names(delta.toolsAdded)
        if (delta.toolsRemoved.isNotEmpty()) parts += "removed tools: " + names(delta.toolsRemoved)
        if (delta.skillsAdded.isNotEmpty()) parts += "added skills: " + names(delta.skillsAdded)
        if (delta.skillsRemoved.isNotEmpty()) parts += "removed skills: " + names(delta.skillsRemoved)
        return "Capabilities changed for this turn: ${parts.joinToString("; ")}."
    }

    fun encodeSnapshot(snapshot: CapabilitySnapshot): JsonObject = buildJsonObject {
        put("tools", JsonArray(snapshot.tools.map(::JsonPrimitive)))
        put("skills", JsonArray(snapshot.skills.map(::JsonPrimitive)))
    }

    fun decodeSnapshot(element: JsonElement?): CapabilitySnapshot? {
        val source = element as? JsonObject ?: return null
        val tools = source.stringList("tools") ?: return null
        val skills = source.stringList("skills") ?: return null
        return CapabilitySnapshot.of(tools, skills)
    }

    fun encodeDelta(delta: CapabilityDelta): JsonObject = buildJsonObject {
        put("toolsAdded", JsonArray(delta.toolsAdded.map(::JsonPrimitive)))
        put("toolsRemoved", JsonArray(delta.toolsRemoved.map(::JsonPrimitive)))
        put("skillsAdded", JsonArray(delta.skillsAdded.map(::JsonPrimitive)))
        put("skillsRemoved", JsonArray(delta.skillsRemoved.map(::JsonPrimitive)))
    }

    fun decodeDelta(element: JsonElement?): CapabilityDelta? {
        val source = element as? JsonObject ?: return null
        return CapabilityDelta(
            toolsAdded = source.stringList("toolsAdded").orEmpty(),
            toolsRemoved = source.stringList("toolsRemoved").orEmpty(),
            skillsAdded = source.stringList("skillsAdded").orEmpty(),
            skillsRemoved = source.stringList("skillsRemoved").orEmpty(),
        )
    }

    private fun names(values: List<String>): String {
        val shown = values.take(MAX_NAMES_PER_GROUP).joinToString(", ")
        val remaining = values.size - MAX_NAMES_PER_GROUP
        return if (remaining > 0) "$shown (+$remaining more)" else shown
    }

    private fun JsonObject.stringList(key: String): List<String>? {
        val array = this[key] as? JsonArray ?: return null
        return array.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter { it.isNotBlank() }
    }
}
