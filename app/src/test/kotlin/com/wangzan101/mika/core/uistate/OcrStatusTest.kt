package com.wangzan101.mika.core.uistate

import io.github.whywood.deepdeep.screen.phongentVisibleTextFields
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screen-only cases from Mika's OcrStatusTest, using the Lab readback adapter. */
class OcrStatusTest {
    private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.content

    private suspend fun observe(
        changing: Boolean = false,
        ocr: suspend () -> List<VisibleTextLine>?,
    ) = phongentVisibleTextFields(ScreenSnapshot(
        "p1", "com.example", emptyList(), 1080, 2400, changedDuringRead = changing,
    )) { ocr() }

    @Test fun emptyOcrIsReportedWithAdvice() = runTest {
        val r = observe { emptyList() }
        assertEquals("unusable", r.s("treeQuality"))
        assertEquals("empty", r.s("ocr"))
        assertTrue(r.s("hint")!!.contains("(ocr: empty)"))
        assertFalse(r.s("hint")!!.contains("hand_to_user"))
    }

    @Test fun eachReasonForNoTextIsNamed() = runTest {
        assertEquals("unavailable", observe { null }.s("ocr"))
        assertEquals("failed", observe { throw IllegalStateException("recognizer") }.s("ocr"))
        assertEquals("illegible", observe {
            listOf(VisibleTextLine("・・・", listOf(0, 0, 100, 40)))
        }.s("ocr"))
        assertEquals("timeout", observe { delay(60_000); emptyList() }.s("ocr"))
    }

    @Test fun textIsReadEvenWhenTheTreeChangedDuringTheRead() = runTest {
        val r = observe(changing = true) { listOf(VisibleTextLine("明天见", listOf(200, 150, 500, 190))) }
        assertEquals(listOf("明天见"), r["visibleText"]!!.jsonArray.map { (it as JsonObject).s("text") })
        assertFalse("ocr" in r)
        assertTrue(r.s("hint")!!.startsWith("The accessibility tree is missing much of this screen"))
    }
}
