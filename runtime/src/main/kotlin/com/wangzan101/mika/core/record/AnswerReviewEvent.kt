package com.wangzan101.mika.core.record

/** Events from Phongent RunEvent used by the answer review. */
internal sealed interface AnswerReviewEvent {
    val atMs: Long
    data class AnswerCheck(override val atMs: Long, val step: Int, val claims: Int, val unmatched: List<String>, val outward: Boolean = false) : AnswerReviewEvent
    data class Verifier(override val atMs: Long, val verdict: String, val detailJson: String, val step: Int? = null) : AnswerReviewEvent

    companion object {
        const val MAX_FIELD = 2000
        fun clip(s: String): String = if (s.length <= MAX_FIELD) s else s.take(MAX_FIELD) + "…(已截短)"
    }
}
