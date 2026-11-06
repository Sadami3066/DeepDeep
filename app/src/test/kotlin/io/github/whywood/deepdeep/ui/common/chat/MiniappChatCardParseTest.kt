package io.github.whywood.deepdeep.ui.common.chat


import io.github.whywood.deepdeep.ui.common.chat.parseRegisteredMiniapp

import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.MiniappManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniappChatCardParseTest {
    @Test
    fun parsesMiniappLoad() {
        val app = requireNotNull(
            parseRegisteredMiniapp(
                trace(
                    output = """
                        {"ok":true,"app":{"id":"weekly-board","name":"周报看板","icon":"📊","version":"1.2.0"},"path":"/miniapp/apps/weekly-board"}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals("weekly-board", app.id)
        assertEquals("周报看板", app.name)
        assertEquals("📊", app.icon)
        assertEquals("1.2.0", app.version)
    }

    @Test
    fun parsesLegacyRegister() {
        val app = requireNotNull(
            parseRegisteredMiniapp(trace(name = "miniapp_register")),
        )
        assertEquals("a", app.id)
        assertEquals("A", app.name)
    }

    @Test
    fun deliveryMarkerSuppressesAutoCard() {
        assertNull(
            parseRegisteredMiniapp(
                trace(
                    output = """
                        {"ok":true,"delivery":"present","app":{"id":"a","name":"A","icon":"📦","version":"1.0.0"}}
                    """.trimIndent(),
                ),
            ),
        )
        assertNull(
            parseRegisteredMiniapp(
                trace(
                    output = """
                        {"ok":true,"delivery":"present","app":{"id":"a","name":"A"},"next":"deliver with present"}
                    """.trimIndent(),
                ),
            ),
        )
    }

    @Test
    fun legacyHistoryWithoutDeliveryMarkerStillRenders() {
        val app = requireNotNull(
            parseRegisteredMiniapp(
                trace(
                    output = """
                        {"ok":true,"app":{"id":"legacy-board","name":"旧看板","icon":"🗂️","version":"0.9.0"},"path":"/miniapp/apps/legacy-board"}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals("legacy-board", app.id)
        assertEquals("旧看板", app.name)
    }

    @Test
    fun ignoresOtherToolNames() {
        assertNull(parseRegisteredMiniapp(trace(name = "miniapp_list")))
        assertNull(parseRegisteredMiniapp(trace(name = "present")))
    }

    @Test
    fun malformedOutputReturnsNull() {
        assertNull(parseRegisteredMiniapp(trace(output = null)))
        assertNull(parseRegisteredMiniapp(trace(output = "  ")))
        assertNull(parseRegisteredMiniapp(trace(output = "not json")))
        assertNull(parseRegisteredMiniapp(trace(output = """{}""")))
        assertNull(parseRegisteredMiniapp(trace(output = """{"ok":true}""")))
        assertNull(parseRegisteredMiniapp(trace(output = """{"ok":false,"app":{"id":"a","name":"A"}}""")))
        assertNull(parseRegisteredMiniapp(trace(output = """{"ok":true,"app":{}}""")))
    }

    @Test
    fun missingRequiredFieldsReturnNull() {
        assertNull(parseRegisteredMiniapp(trace(output = appJson(id = null, name = "A"))))
        assertNull(parseRegisteredMiniapp(trace(output = appJson(id = "a", name = null))))
        assertNull(parseRegisteredMiniapp(trace(output = appJson(id = " ", name = "A"))))
        assertNull(parseRegisteredMiniapp(trace(output = appJson(id = "a", name = ""))))
    }

    @Test
    fun fallsBackToIconAndVersionDefaults() {
        val app = requireNotNull(
            parseRegisteredMiniapp(trace(output = appJson(id = "flow", name = "Flow"))),
        )
        assertEquals("✦", app.icon)
        assertEquals(MiniappManifest.DEFAULT_VERSION, app.version)
    }

    private fun appJson(id: String?, name: String?): String {
        val fields = buildList {
            id?.let { add(""""id":"$it"""") }
            name?.let { add(""""name":"$it"""") }
        }
        return """{"ok":true,"app":{${fields.joinToString(",")}}}"""
    }

    private fun trace(
        output: String? = """{"ok":true,"app":{"id":"a","name":"A","icon":"📦","version":"1.0.0"}}""",
        name: String = "miniapp_load",
    ): ToolTrace = ToolTrace(id = 1L, name = name, arguments = emptyMap(), output = output)
}
