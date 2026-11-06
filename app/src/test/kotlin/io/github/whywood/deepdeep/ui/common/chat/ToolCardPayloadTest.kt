package io.github.whywood.deepdeep.ui.common.chat

import io.github.whywood.deepdeep.ui.common.chat.toPluginToolBlocks

import io.github.whywood.deepdeep.plugin.api.ToolBlock
import io.github.whywood.deepdeep.runtime.ToolResultPayload
import org.junit.Assert.assertEquals
import org.junit.Test

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
                ToolBlock.Text("before"),
                ToolBlock.FileRef("/tmp/report.pdf", "application/pdf"),
                ToolBlock.FileRef("/tmp/raw.bin"),
            ),
            payload.toPluginToolBlocks(),
        )
    }

    @Test
    fun textAndEmptyPayloadsStayTextOnly() {
        assertEquals(
            listOf(ToolBlock.Text("done")),
            ToolResultPayload.Text("done").toPluginToolBlocks(),
        )
        assertEquals(emptyList<ToolBlock>(), ToolResultPayload.Empty.toPluginToolBlocks())
    }
}
