package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.ui.common.chat.toPluginToolBlocks

import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.runtime.ToolResultPayload
import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.whywood.deepdeep.plugin.api.fileRefBlock
import io.github.whywood.deepdeep.plugin.api.textBlock

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
    fun textAndEmptyPayloadsStayTextOnly() {
        assertEquals(
            listOf(textBlock("done")),
            ToolResultPayload.Text("done").toPluginToolBlocks(),
        )
        assertEquals(emptyList<ToolBlock>(), ToolResultPayload.Empty.toPluginToolBlocks())
    }
}
