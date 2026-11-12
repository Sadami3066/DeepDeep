package io.github.whywood.deepdeep.purge

object PurgeRefusal {
    private val markers = listOf(
        "抱歉", "对不起", "我不能", "我无法", "无法协助", "无法提供", "不能提供", "不能帮",
        "不能满足", "不便提供", "我不会", "恕不", "拒绝", "建议你", "建议通过", "请理解",
        "我不能帮", "不做这个", "这个我不做", "这类内容我不做",
        "I can't", "I cannot", "I won't", "I'm sorry", "unable to", "not able to",
        "as an AI", "against my", "I must decline", "I have to decline",
    )

    private val productiveMarkers = listOf("```", "```bash", "```sh", "```powershell", "```python", "TARGET", "HOST")

    fun looksLikeRefusal(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isEmpty()) return false
        if (productiveMarkers.any { normalized.contains(it) }) return false
        return markers.any { normalized.contains(it, ignoreCase = true) }
    }
}
