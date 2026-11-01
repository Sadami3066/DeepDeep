package io.github.whywood.deepdeep.runtime

import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal fun toolVariant(
    delegate: CodingTool,
    name: String,
    description: String,
    fields: Set<String>,
    required: Set<String> = emptySet(),
    fixedAction: String? = null,
    actions: List<String>? = null,
): CodingTool {
    val properties = delegate.parameters.getValue("properties").jsonObject.filterKeys { it in fields }.toMutableMap()
    if (actions != null) {
        properties["action"] = JsonObject(properties.getValue("action").jsonObject +
            ("enum" to JsonArray(actions.map(::JsonPrimitive))) +
            ("description" to JsonPrimitive(actions.joinToString(", "))))
    }
    return CodingTool(
        name = name,
        description = description,
        label = name,
        parameters = buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(properties))
            put("additionalProperties", false)
            put("required", JsonArray(required.map(::JsonPrimitive)))
        },
        execute = { id, args, signal, update ->
            signal?.throwIfAborted()
            val unexpected = args.keys - fields
            val invalid = args.keys.filter { key ->
                val value = args[key] as? JsonPrimitive
                val type = (properties[key]?.jsonObject?.get("type") as? JsonPrimitive)?.content
                when (type) {
                    "string" -> value == null || !value.isString
                    "boolean" -> value == null || value.isString || value.booleanOrNull == null
                    "number", "integer" -> value == null || value.isString || value.doubleOrNull?.isFinite() != true ||
                        (type == "integer" && value.doubleOrNull!! % 1.0 != 0.0)
                    else -> false
                }
            }
            val missing = required.filter { key -> args[key] == null || args[key].toString() == "null" }
            when {
                unexpected.isNotEmpty() -> textResult("$name does not accept: ${unexpected.joinToString()}", isError = true)
                invalid.isNotEmpty() -> textResult("Invalid argument type: ${invalid.joinToString()}", isError = true)
                missing.isNotEmpty() -> textResult("$name requires: ${missing.joinToString()}", isError = true)
                actions != null && (args["action"] as? JsonPrimitive)?.content !in actions ->
                    textResult("action must be ${actions.joinToString()}", isError = true)
                else -> delegate.execute(id, if (fixedAction == null) args else JsonObject(args + ("action" to JsonPrimitive(fixedAction))), signal, update)
            }
        },
    )
}
