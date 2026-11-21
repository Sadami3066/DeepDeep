package io.github.whywood.superpi.plugin.compat

import io.github.whywood.superpi.plugin.api.Cards
import io.github.whywood.superpi.plugin.api.PLUGIN_API_LEVEL
import io.github.whywood.superpi.plugin.api.PluginCapability
import io.github.whywood.superpi.plugin.api.PluginPermission
import io.github.whywood.superpi.plugin.api.PluginRunSignal
import io.github.whywood.superpi.plugin.api.PluginToolCall
import io.github.whywood.superpi.plugin.api.PluginToolResult
import io.github.whywood.superpi.plugin.host.external.PluginManifestCodec
import io.github.whywood.superpi.plugin.host.external.PluginValidation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperPICompatibilityTest {

    @Test
    fun manifestInspectionDetectsAllViolations() {
        val nonCompliantManifest = buildJsonObject {
            put("id", "my-weather-tool")
            put("version", "1.0")
            put("apiLevel", 5)
            put("name", "Weather Assistant")
            put("summary", "Query weather")
            putJsonArray("permissions") {
                add(JsonPrimitive(PluginPermission.LOCATION_FINE))
            }
            putJsonObject("provides") {
                putJsonArray("tools") {
                    add(JsonPrimitive("read"))
                    add(JsonPrimitive("getWeatherInfo"))
                }
            }
        }

        val report = SuperPICompatibilityInspector.inspectManifest(nonCompliantManifest)
        assertFalse(report.isCompatible)

        val issueCodes = report.issues.map { it.code }.toSet()
        assertTrue(issueCodes.contains(CompatibilityCode.INVALID_ID))
        assertTrue(issueCodes.contains(CompatibilityCode.INVALID_VERSION))
        assertTrue(issueCodes.contains(CompatibilityCode.API_LEVEL_OUTDATED))
        assertTrue(issueCodes.contains(CompatibilityCode.ENTRY_CLASS_MISSING))
        assertTrue(issueCodes.contains(CompatibilityCode.MISSING_DEFAULT_LOCALE))
        assertTrue(issueCodes.contains(CompatibilityCode.PERMISSION_WITHOUT_CAPABILITY))
        assertTrue(issueCodes.contains(CompatibilityCode.RESERVED_TOOL_NAME))
        assertTrue(issueCodes.contains(CompatibilityCode.INVALID_TOOL_NAME))

        assertTrue(report.issues.all { it.options.isNotEmpty() })
        assertTrue(report.issues.all { it.options.any { opt -> opt.isRecommended } })
    }

    @Test
    fun manifestAutoFixMakesManifestFullyCompliant() {
        val nonCompliantManifest = buildJsonObject {
            put("id", "my-weather-tool")
            put("version", "1.0")
            put("apiLevel", 6)
            put("name", "Weather Assistant")
            put("summary", "Query weather")
            putJsonArray("permissions") {
                add(JsonPrimitive(PluginPermission.LOCATION_FINE))
            }
            putJsonObject("provides") {
                putJsonArray("tools") {
                    add(JsonPrimitive("read"))
                    add(JsonPrimitive("getWeatherInfo"))
                }
            }
        }

        val repaired = SuperPICompatibilityFixer.applyAllRecommendedManifest(nonCompliantManifest)

        val report = SuperPICompatibilityInspector.inspectManifest(repaired)
        assertTrue(report.isCompatible)
        assertTrue(report.errors.isEmpty())

        val id = (repaired["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        assertTrue(id.startsWith("io.github.adapted."))
        assertEquals("1.0.0", (repaired["version"] as? JsonPrimitive)?.contentOrNull)
        assertEquals(PLUGIN_API_LEVEL.toString(), (repaired["apiLevel"] as? JsonPrimitive)?.contentOrNull)

        val parsed = PluginManifestCodec.decode(repaired)
        assertTrue(parsed is PluginManifestCodec.Result.Valid)
    }

    @Test
    fun toolInspectionDetectsAndFixesNamingAndSchemaIssues() {
        val rawTool = buildJsonObject {
            put("name", "fetchUserData")
            put("description", "Fetch user info")
            putJsonObject("parameters") {
                putJsonObject("properties") {
                    putJsonObject("userId") { put("type", "int") }
                    putJsonObject("userName") { put("type", "str") }
                }
            }
        }

        val report = SuperPICompatibilityInspector.inspectTool(rawTool)
        assertFalse(report.isCompatible)

        val issueCodes = report.issues.map { it.code }.toSet()
        assertTrue(issueCodes.contains(CompatibilityCode.INVALID_TOOL_NAME))
        assertTrue(issueCodes.contains(CompatibilityCode.INVALID_SCHEMA))
        assertTrue(issueCodes.contains(CompatibilityCode.SCHEMA_NON_STANDARD_TYPES))

        val fixed = SuperPICompatibilityFixer.applyAllRecommendedTool(rawTool)
        val fixedReport = SuperPICompatibilityInspector.inspectTool(fixed)
        assertTrue(fixedReport.isCompatible)
        assertTrue(fixedReport.errors.isEmpty())

        assertEquals("fetch_user_data", (fixed["name"] as? JsonPrimitive)?.contentOrNull)
        val params = fixed["parameters"]?.jsonObject
        assertNotNull(params)
        assertEquals("object", (params?.get("type") as? JsonPrimitive)?.contentOrNull)

        val props = params?.get("properties")?.jsonObject
        assertNotNull(props)
        val idProp = props?.get("userId")?.jsonObject
        assertEquals("integer", (idProp?.get("type") as? JsonPrimitive)?.contentOrNull)
        val nameProp = props?.get("userName")?.jsonObject
        assertEquals("string", (nameProp?.get("type") as? JsonPrimitive)?.contentOrNull)
    }

    @Test
    fun reservedToolNameIsPrefixed() {
        val tool = buildJsonObject {
            put("name", "system_shell")
            put("description", "Run command")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {}
            }
        }

        val report = SuperPICompatibilityInspector.inspectTool(tool)
        assertFalse(report.isCompatible)
        assertTrue(report.issues.any { it.code == CompatibilityCode.RESERVED_TOOL_NAME })

        val fixed = SuperPICompatibilityFixer.applyAllRecommendedTool(tool)
        assertEquals("ext_system_shell", (fixed["name"] as? JsonPrimitive)?.contentOrNull)
    }

    @Test
    fun externalToolBridgeDeliversThreeLayerCardsProtocol() = runBlocking {
        val toolJson = buildJsonObject {
            put("name", "queryStockPrice")
            put("description", "Get stock quote")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("symbol") { put("type", "string") }
                }
            }
        }

        val pluginTool = ExternalToolBridge.createPluginTool(toolJson) { call ->
            val symbol = (call.args["symbol"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            ExternalToolExecutionOutput(
                stdout = "\u001B[32mStock $symbol: 150.25 USD\u001B[0m",
                guidance = "Advise user that market closes at 4 PM EST.",
            )
        }

        assertEquals("query_stock_price", pluginTool.name)

        val call = object : PluginToolCall {
            override val callId: String = "call-123"
            override val args: JsonObject = buildJsonObject { put("symbol", "GOOG") }
            override val signal: PluginRunSignal? = null
            override val sessionId: String = "session-1"
            override fun partial(result: PluginToolResult) {}
        }

        val result = pluginTool.execute(call)
        assertFalse(result.isError)

        val text = result.text.orEmpty()
        assertTrue(text.contains("Full return (upstream, verbatim):"))
        assertTrue(text.contains("Stock GOOG: 150.25 USD"))
        assertTrue(text.contains("Usage:"))
        assertTrue(text.contains("Advise user that market closes at 4 PM EST."))
        assertFalse(text.contains("\u001B[32m"))
    }
}
