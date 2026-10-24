package io.github.whywood.deepdeep.ui.workshop

import io.github.whywood.deepdeep.runtime.ToolTrace
import io.github.whywood.deepdeep.runtime.WorkshopManifest
import io.github.whywood.deepdeep.runtime.WorkshopSpec
import io.github.whywood.deepdeep.runtime.WorkshopType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkshopChatCardParseTest {
    @Test
    fun parsesRegisteredWorkshop() {
        val app = requireNotNull(
            parseRegisteredWorkshop(
                trace(
                    output = """
                        {"ok":true,"app":{"id":"weekly-board","name":"周报看板","type":"${WorkshopType.BOARD.wire}","icon":"📊","version":"1.2.0"},"path":"/workshop/apps/weekly-board"}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals("weekly-board", app.id)
        assertEquals("周报看板", app.name)
        assertEquals(WorkshopType.BOARD, app.type)
        assertEquals("📊", app.icon)
        assertEquals("1.2.0", app.version)
    }

    @Test
    fun ignoresOtherTraceNames() {
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
        assertNull(parseRegisteredWorkshop(trace(output = """{"ok":false,"app":{"id":"a","name":"A","type":"${WorkshopType.BOARD.wire}"}}""")))
        assertNull(parseRegisteredWorkshop(trace(output = """{"ok":true,"app":{}}""")))
    }

    @Test
    fun missingRequiredFieldsReturnNull() {
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = null, name = "A", type = WorkshopType.BOARD.wire))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = "a", name = null, type = WorkshopType.BOARD.wire))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = "a", name = "A", type = ""))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = " ", name = "A", type = WorkshopType.BOARD.wire))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = "a", name = "", type = WorkshopType.BOARD.wire))))
        assertNull(parseRegisteredWorkshop(trace(output = appJson(id = "a", name = "A", type = "spreadsheet"))))
    }

    @Test
    fun fallsBackToTypeDefaults() {
        val app = requireNotNull(
            parseRegisteredWorkshop(
                trace(output = appJson(id = "flow", name = "Flow", type = WorkshopType.WORKFLOW.wire)),
            ),
        )
        assertEquals(WorkshopSpec.defaultIcon(WorkshopType.WORKFLOW), app.icon)
        assertEquals(WorkshopManifest.DEFAULT_VERSION, app.version)
    }

    private fun appJson(
        id: String? = "a",
        name: String? = "A",
        type: String? = WorkshopType.BOARD.wire,
    ): String {
        val fields = buildList {
            id?.let { add(""""id":"$it"""") }
            name?.let { add(""""name":"$it"""") }
            type?.let { add(""""type":"$it"""") }
        }
        return """{"ok":true,"app":{${fields.joinToString(",")}}}"""
    }

    private fun trace(
        output: String? = """{"ok":true,"app":{"id":"a","name":"A","type":"${WorkshopType.BOARD.wire}"}}""",
        name: String = "workshop_register",
    ): ToolTrace = ToolTrace(id = 1L, name = name, arguments = emptyMap(), output = output)
}
