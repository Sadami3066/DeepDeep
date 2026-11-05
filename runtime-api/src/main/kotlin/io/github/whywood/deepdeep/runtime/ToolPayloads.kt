package io.github.whywood.deepdeep.runtime

sealed interface ToolResultPayload {
    data class Text(val text: String) : ToolResultPayload

    data class Blocks(val blocks: List<Block>) : ToolResultPayload

    data object Empty : ToolResultPayload

    sealed interface Block {
        data class Text(val text: String) : Block

        data class Image(
            val data: String,
            val mimeType: String,
        ) : Block
    }
}

fun ToolResultPayload.text(): String = when (this) {
    is ToolResultPayload.Text -> text
    is ToolResultPayload.Blocks -> blocks.filterIsInstance<ToolResultPayload.Block.Text>()
        .joinToString("") { it.text }

    ToolResultPayload.Empty -> ""
}
