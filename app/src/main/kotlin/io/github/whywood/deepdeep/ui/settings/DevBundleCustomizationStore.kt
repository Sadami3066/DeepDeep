package io.github.whywood.deepdeep.ui.settings

import android.content.Context
import io.github.whywood.deepdeep.data.ApkMirrors
import io.github.whywood.deepdeep.data.DevBundleCustomization
import io.github.whywood.deepdeep.data.DevPackageCatalog
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class DevBundleCustomizationStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "dev_bundle_customization.json")

    fun load(): DevBundleCustomization = runCatching {
        if (file.isFile) decodeCustomization(file.readText()) else DevBundleCustomization()
    }.getOrDefault(DevBundleCustomization())

    fun save(customization: DevBundleCustomization) {
        runCatching { file.writeText(encodeCustomization(customization)) }
    }
}

internal fun decodeCustomization(text: String?): DevBundleCustomization {
    if (text.isNullOrBlank()) return DevBundleCustomization()
    val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        ?: return DevBundleCustomization()
    val extras = root["extras"].asIdMap().mapValues { (_, ids) ->
        ids.distinct().filter { DevPackageCatalog.packageById(it) != null }
    }.filterValues { it.isNotEmpty() }
    val overlays = root["overlays"].asIdMap().mapValues { (_, ids) ->
        ids.distinct().filter { DevPackageCatalog.bundleById(it) != null }
    }.filterValues { it.isNotEmpty() }
    val mirrors = root["mirrors"].asStringList().distinct().filter { ApkMirrors.byId(it) != null }
    return DevBundleCustomization(extras = extras, overlays = overlays, mirrors = mirrors)
}

internal fun encodeCustomization(customization: DevBundleCustomization): String = buildJsonObject {
    put(
        "extras",
        buildJsonObject {
            customization.extras.filterValues { it.isNotEmpty() }.forEach { (bundleId, ids) ->
                put(bundleId, buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            }
        },
    )
    put(
        "overlays",
        buildJsonObject {
            customization.overlays.filterValues { it.isNotEmpty() }.forEach { (bundleId, ids) ->
                put(bundleId, buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            }
        },
    )
    put("mirrors", buildJsonArray { customization.mirrors.forEach { add(JsonPrimitive(it)) } })
}.toString()

private fun JsonElement?.asIdMap(): Map<String, List<String>> {
    val obj = this as? JsonObject ?: return emptyMap()
    return obj.entries.mapNotNull { (key, value) ->
        val ids = value.asStringList()
        if (ids.isEmpty()) null else key to ids
    }.toMap()
}

private fun JsonElement?.asStringList(): List<String> {
    val array = this as? JsonArray ?: return emptyList()
    return array.mapNotNull { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }
}
