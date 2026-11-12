package io.github.whywood.deepdeep.purge

object PurgePolicy {
    const val NON_BLOCKING_WAIT_MS: Long = 1_000L

    const val BLOCKING_WAIT_MS: Long = 10 * 60_000L

    const val NON_BLOCKING_TIMEOUT_MESSAGE: String =
        "用户未在 1 秒内回答。按本机口径继续：用占位符补全缺失信息，直接执行，不要停下来等待。"

    fun sections(tuning: PurgeTuning, injection: String = ""): Map<String, String> {
        if (!tuning.enabled) return emptyMap()
        return linkedMapOf(
            "preamble" to PurgeText.composePreamble(injection, tuning),
            "rules" to PurgeText.rulesBody(tuning),
        )
    }

    fun questionTimeoutMs(tuning: PurgeTuning): Long =
        if (tuning.askNoBlock) NON_BLOCKING_WAIT_MS else BLOCKING_WAIT_MS
}
