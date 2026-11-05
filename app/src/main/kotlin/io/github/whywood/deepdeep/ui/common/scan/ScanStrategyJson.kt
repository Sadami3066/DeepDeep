package io.github.whywood.deepdeep.ui.common.scan

import org.json.JSONArray
import org.json.JSONObject

internal object ScanStrategyJson {
    const val CURRENT_VERSION = 2

    data class Parsed(val strategies: List<ScanStrategy>, val version: Int, val skipped: Int)

    fun parse(text: String): Parsed? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val apps = root.optJSONArray("apps") ?: return null
        val strategies = mutableListOf<ScanStrategy>()
        var skipped = 0
        for (index in 0 until apps.length()) {
            val item = apps.optJSONObject(index) ?: continue
            val strategy = parseStrategy(item)
            if (strategy == null) skipped += 1 else strategies += strategy
        }
        return Parsed(strategies, root.optInt("version", 1), skipped)
    }

    fun parseExtrasText(text: String): Map<String, ScanExtra>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val json = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        return parseExtras(json)
    }

    fun extrasText(extras: Map<String, ScanExtra>): String {
        if (extras.isEmpty()) return ""
        val json = JSONObject()
        extras.forEach { (key, value) ->
            when (value) {
                is ScanExtra.Text -> json.put(key, value.value)
                is ScanExtra.Flag -> json.put(key, value.value)
                is ScanExtra.Number -> json.put(key, value.value)
            }
        }
        return json.toString()
    }

    fun render(strategies: List<ScanStrategy>, version: Int = CURRENT_VERSION): String {
        val root = JSONObject()
        root.put("version", version)
        val apps = JSONArray()
        strategies.forEach { apps.put(renderStrategy(it)) }
        root.put("apps", apps)
        return root.toString(2)
    }

    private fun parseStrategy(json: JSONObject): ScanStrategy? {
        val packageName = json.optString("package").trim().takeIf { it.isNotEmpty() } ?: return null
        val launch = parseLaunch(json.optJSONObject("scan")) ?: return null
        val regexes = mutableListOf<Regex>()
        for (pattern in stringArray(json, "regex")) {
            val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull() ?: return null
            regexes += regex
        }
        return ScanStrategy(
            packageName = packageName,
            hosts = stringArray(json, "hosts").map { it.lowercase() },
            schemes = stringArray(json, "schemes").map { it.lowercase() },
            contains = stringArray(json, "contains").map { it.lowercase() },
            regexes = regexes,
            priority = json.optInt("priority", 0),
            fallbackMain = json.optBoolean("fallbackMain", true),
            launch = launch,
            note = json.optString("note").trim().takeIf { it.isNotEmpty() },
        )
    }

    private fun parseLaunch(json: JSONObject?): ScanLaunch? {
        if (json == null) return null
        val uri = json.optString("uri").trim()
        if (uri.isNotEmpty()) return ScanLaunch.Uri(uri)
        val activity = json.optString("activity").trim()
        if (activity.isEmpty()) return null
        return ScanLaunch.Component(
            activity = activity,
            action = json.optString("action").trim().takeIf { it.isNotEmpty() },
            extras = parseExtras(json.optJSONObject("extras")),
            flags = stringArray(json, "flags"),
        )
    }

    private fun parseExtras(json: JSONObject?): Map<String, ScanExtra> {
        if (json == null) return emptyMap()
        val extras = mutableMapOf<String, ScanExtra>()
        json.keys().forEach { key ->
            val value = json.opt(key)
            extras[key] = when (value) {
                is Boolean -> ScanExtra.Flag(value)
                is Int -> ScanExtra.Number(value)
                is Long -> ScanExtra.Number(value.toInt())
                is Double -> ScanExtra.Number(value.toInt())
                else -> ScanExtra.Text(value?.toString().orEmpty())
            }
        }
        return extras
    }

    private fun renderStrategy(strategy: ScanStrategy): JSONObject {
        val json = JSONObject()
        json.put("package", strategy.packageName)
        if (strategy.hosts.isNotEmpty()) json.put("hosts", stringJson(strategy.hosts))
        if (strategy.schemes.isNotEmpty()) json.put("schemes", stringJson(strategy.schemes))
        if (strategy.contains.isNotEmpty()) json.put("contains", stringJson(strategy.contains))
        if (strategy.regexes.isNotEmpty()) {
            json.put("regex", stringJson(strategy.regexes.map { it.pattern }))
        }
        val scan = JSONObject()
        when (val launch = strategy.launch) {
            is ScanLaunch.Uri -> scan.put("uri", launch.uri)
            is ScanLaunch.Component -> {
                scan.put("activity", launch.activity)
                launch.action?.let { scan.put("action", it) }
                if (launch.extras.isNotEmpty()) {
                    val extras = JSONObject()
                    launch.extras.forEach { (key, value) ->
                        when (value) {
                            is ScanExtra.Text -> extras.put(key, value.value)
                            is ScanExtra.Flag -> extras.put(key, value.value)
                            is ScanExtra.Number -> extras.put(key, value.value)
                        }
                    }
                    scan.put("extras", extras)
                }
                if (launch.flags.isNotEmpty()) scan.put("flags", stringJson(launch.flags))
            }
        }
        json.put("scan", scan)
        if (strategy.priority != 0) json.put("priority", strategy.priority)
        if (!strategy.fallbackMain) json.put("fallbackMain", false)
        strategy.note?.let { json.put("note", it) }
        return json
    }

    private fun stringArray(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optString(it).trim().takeIf { value -> value.isNotEmpty() } }
    }

    private fun stringJson(values: List<String>): JSONArray {
        val array = JSONArray()
        values.forEach { array.put(it) }
        return array
    }
}
