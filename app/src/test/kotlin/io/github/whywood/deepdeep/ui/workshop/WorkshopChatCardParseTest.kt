package io.github.whywood.deepdeep.ui.workshop

import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.WorkshopManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkshopChatCardParseTest {
    @Test
    fun parsesWorkshopLoad() {
        val app = requireNotNull(
            parseRegisteredWorkshop(
                trace(
                    output = """
                        {"ok":true,"app":{"id":"weekly-board","name":"周报看板","icon":"📊","version":"1.2.0"},"path":"/workshop/apps/weekly-board"}
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
            parseRegisteredWorkshop(trace(name = "workshop_register")),
        )
        assertEquals("a", app.id)
        assertEquals("A", app.name)
    }

    @Test
    fun ignoresOtherToolNames() {
        assertNull(parseRegisteredWorkshop(trace(name = "workshop_list")))
        assertNull(parseRegisteredWorkshop(trace(name = "present")))
    }

    @Test
    fun malformedOutputReturnsNull() {
        assertNull(parseRegisteredWorkshop(trace(output = null)))
        assertNull(parseRegisteredWorkshop(trace(output = "  ")))
        assertNull(parseRegisteredWorkshop(trace(output = "not json")))
        assertNull(parseRegisteredWorkshop(trace(output = """{}""")))
        assertNull(parseRegisteredWorkshop(trace(output = """{"ok":true}""")))
        assertNull(parseRegisteredWorkshop(trace(output = """{"ok":false,"app":{"id":"a","name":"A"}}""")))
        assertNull(parseRegisteredWorkshop(trace(output = """{"ok":true,"app":{}}""")))
    }

    @Test
    fun missingRequiredFieldsReturnNull() {
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = null, name = "A"))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = "a", name = null))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = " ", name = "A"))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = "a", name = ""))))
    }

    @Test
    fun fallsBackToIconAndVersionDefaults() {
        val app = requireNotNull(
            parseRegisteredWorkshop(trace(output = appJson(id = "flow", name = "Flow"))),
        )
        assertEquals("✦", app.icon)
        assertEquals(WorkshopManifest.DEFAULT_VERSION, app.version)
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
        name: String = "workshop_load",
    ): ToolTrace = ToolTrace(id = 1L, name = name, arguments = emptyMap(), output = output)
}
