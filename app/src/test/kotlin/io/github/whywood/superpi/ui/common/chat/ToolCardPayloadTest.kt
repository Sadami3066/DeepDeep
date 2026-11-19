package io.github.whywood.superpi.ui.common.chat

import io.github.whywood.superpi.ui.common.chat.toPluginToolBlocks

import io.github.whywood.superpi.plugin.api.ToolBlock
import io.github.whywood.superpi.runtime.ToolActivityStatus
import io.github.whywood.superpi.runtime.ToolResultPayload
import io.github.whywood.superpi.runtime.ToolTrace
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.whywood.superpi.plugin.api.fileRefBlock
import io.github.whywood.superpi.plugin.api.textBlock

class ToolCardPayloadTest {
    @Test
    fun fileRefBlocksBecomeStructuredToolBlocks() {
        val payload = ToolResultPayload.Blocks(
            listOf(
                ToolResultPayload.Block.Text("before"),
                ToolResultPayload.Block.FileRef("/tmp/report.pdf", "application/pdf"),
                ToolResultPayload.Block.FileRef("/tmp/raw.bin"),
            ),
        )

        assertEquals(
            listOf(
                textBlock("before"),
                fileRefBlock("/tmp/report.pdf", "application/pdf"),
                fileRefBlock("/tmp/raw.bin"),
            ),
            payload.toPluginToolBlocks(),
        )
    }

    @Test
    fun slotResultKeepsStructuredDetails() {
        val details = buildJsonObject { put("draftId", "draft-1") }
        val trace = ToolTrace(
            id = 1L,
            name = "mail_draft",
            arguments = emptyMap(),
            status = ToolActivityStatus.DONE,
            output = "ok",
            resultPayload = ToolResultPayload.Text("ok"),
            resultDetails = details,
        )

        val result = trace.pluginSlotResult()

        assertEquals("ok", result?.text)
        assertEquals(details, result?.details)
    }

    @Test
    fun textAndEmptyPayloadsStayTextOnly() {
        assertEquals(
            listOf(textBlock("done")),
            ToolResultPayload.Text("done").toPluginToolBlocks(),
        )
        assertEquals(emptyList<ToolBlock>(), ToolResultPayload.Empty.toPluginToolBlocks())
    }
}
