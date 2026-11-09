package io.github.whywood.deepdeep.ui.common.market

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdText
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request

internal sealed class PreviewState {
    data object Loading : PreviewState()
    data class Success(val bitmap: ImageBitmap) : PreviewState()
    data object Failure : PreviewState()
}

internal object MarketPreviewLoader {
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cache(null)
            .build()
    }
    private val cache = object : LruCache<String, ImageBitmap>(PREVIEW_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap<String, Deferred<PreviewState>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun state(url: String): PreviewState {
        if (previewHttpUrl(url) == null) return PreviewState.Failure
        cached(url)?.let { return PreviewState.Success(it) }
        val failed = failedAt[url]
        return if (previewFailureRetryable(failed, SystemClock.elapsedRealtime())) {
            PreviewState.Loading
        } else {
            PreviewState.Failure
        }
    }

    fun retry(url: String) {
        failedAt.remove(url)
    }

    suspend fun load(url: String): PreviewState {
        if (previewHttpUrl(url) == null) return PreviewState.Failure
        cached(url)?.let { return PreviewState.Success(it) }
        if (!previewFailureRetryable(failedAt[url], SystemClock.elapsedRealtime())) {
            return PreviewState.Failure
        }
        failedAt.remove(url)
        val request = inFlight.computeIfAbsent(url) { scope.async { fetch(url) } }
        return try {
            request.await()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            PreviewState.Failure
        } finally {
            inFlight.remove(url, request)
        }
    }

    private fun cached(url: String): ImageBitmap? = synchronized(cache) { cache.get(url) }

    private suspend fun fetch(url: String): PreviewState {
        val state = runCatching { download(url) }.getOrDefault(PreviewState.Failure)
        if (state is PreviewState.Success) {
            synchronized(cache) { cache.put(url, state.bitmap) }
            failedAt.remove(url)
        } else if (state is PreviewState.Failure) {
            failedAt[url] = SystemClock.elapsedRealtime()
        }
        return state
    }

    private fun download(url: String): PreviewState {
        val httpUrl = previewHttpUrl(url) ?: return PreviewState.Failure
        val request = Request.Builder().url(httpUrl).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return PreviewState.Failure
            if (!isPreviewImageType(response.header("Content-Type"))) return PreviewState.Failure
            val body = response.body
            if (body.contentLength() > PREVIEW_MAX_BYTES) return PreviewState.Failure
            val bytes = body.bytes()
            if (bytes.size > PREVIEW_MAX_BYTES) return PreviewState.Failure
            return decode(bytes)
        }
    }

    private fun decode(bytes: ByteArray): PreviewState {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return PreviewState.Failure
        val options = BitmapFactory.Options().apply {
            inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: return PreviewState.Failure
        return PreviewState.Success(bitmap.asImageBitmap())
    }
}

private class PreviewLoad(
    val state: PreviewState,
    val retry: () -> Unit,
)

@Composable
private fun rememberPreviewLoad(url: String): PreviewLoad {
    var attempt by remember(url) { mutableIntStateOf(0) }
    var state by remember(url) { mutableStateOf(MarketPreviewLoader.state(url)) }
    LaunchedEffect(url, attempt) {
        if (state is PreviewState.Loading) state = MarketPreviewLoader.load(url)
    }
    return PreviewLoad(
        state = state,
        retry = {
            MarketPreviewLoader.retry(url)
            state = PreviewState.Loading
            attempt++
        },
    )
}

@Composable
internal fun MarketPreviewCard(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val load = rememberPreviewLoad(url)
    Box(
        modifier = modifier
            .background(colors.wash, DdShapes.Row)
            .clickable(enabled = load.state !is PreviewState.Loading) {
                when (load.state) {
                    is PreviewState.Success -> onClick()
                    is PreviewState.Failure -> load.retry()
                    PreviewState.Loading -> Unit
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (val state = load.state) {
            is PreviewState.Success -> Image(
                bitmap = state.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            PreviewState.Loading -> DdSpinner(size = 20.dp, strokeWidth = 2.dp)
            PreviewState.Failure -> PreviewRetry(
                text = stringResource(R.string.market_preview_retry),
                tint = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun PreviewRetry(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(DdSpacing.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DdSpacing.Xs),
    ) {
        DdIcon(icon = DdIcons.Refresh, tint = tint, size = 20.dp)
        DdText(
            text = text,
            style = DdType.chip,
            color = tint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun MarketPreviewViewer(
    urls: List<String>,
    initialPage: Int,
    title: String,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(urls.indices)) {
            urls.size
        }
        val retryHint = stringResource(R.string.market_preview_retry)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val load = rememberPreviewLoad(urls[page])
                when (val state = load.state) {
                    is PreviewState.Success -> Image(
                        bitmap = state.bitmap,
                        contentDescription = stringResource(
                            R.string.market_preview_content_description,
                            title,
                            page + 1,
                        ),
                        modifier = Modifier.fillMaxSize().padding(DdSpacing.Xxl),
                        contentScale = ContentScale.Fit,
                    )
                    PreviewState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        DdSpinner(size = 28.dp, strokeWidth = 2.5.dp, color = Color.White)
                    }
                    PreviewState.Failure -> Box(
                        modifier = Modifier.fillMaxSize().clickable(onClick = load.retry),
                        contentAlignment = Alignment.Center,
                    ) {
                        PreviewRetry(text = retryHint, tint = Color.White.copy(alpha = 0.72f))
                    }
                }
            }
            DdIconButton(
                icon = DdIcons.Close,
                onClick = onDismiss,
                contentDescription = stringResource(R.string.market_preview_close),
                modifier = Modifier.align(Alignment.TopEnd).padding(DdSpacing.Lg),
                tint = Color.White,
                background = Color.Black.copy(alpha = 0.32f),
                iconSize = 22.dp,
            )
            if (urls.size > 1) {
                DdText(
                    text = stringResource(
                        R.string.market_preview_page,
                        pagerState.currentPage + 1,
                        urls.size,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = DdSpacing.Xxl)
                        .background(Color.Black.copy(alpha = 0.42f), CircleShape)
                        .padding(horizontal = DdSpacing.Md, vertical = DdSpacing.Xs),
                    style = DdType.label,
                    color = Color.White,
                )
            }
        }
    }
}
