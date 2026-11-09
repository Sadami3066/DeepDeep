package io.github.whywood.deepdeep.ui.common.market

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val PREVIEW_MAX_EDGE_PX = 960
internal const val PREVIEW_MAX_BYTES = 8 * 1024 * 1024
internal const val PREVIEW_CACHE_BYTES = 8 * 1024 * 1024
internal const val PREVIEW_FAIL_COOLDOWN_MS = 5 * 60 * 1000L

internal fun previewHttpUrl(url: String): HttpUrl? =
    url.toHttpUrlOrNull()?.takeIf { it.scheme == "https" }

internal fun previewSampleSize(
    width: Int,
    height: Int,
    maxEdge: Int = PREVIEW_MAX_EDGE_PX,
): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    val edge = maxOf(width, height)
    var sample = 1
    while (edge / (sample * 2) >= maxEdge) sample *= 2
    return sample
}

internal fun isPreviewImageType(contentType: String?): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return true
    return type.startsWith("image/")
}

internal fun previewFailureRetryable(
    failedAt: Long?,
    now: Long,
    cooldownMs: Long = PREVIEW_FAIL_COOLDOWN_MS,
): Boolean = failedAt == null || now - failedAt >= cooldownMs
