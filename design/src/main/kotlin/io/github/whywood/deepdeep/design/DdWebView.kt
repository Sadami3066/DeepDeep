package io.github.whywood.deepdeep.design

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

sealed interface DdWebContent {
    data class Url(val url: String) : DdWebContent

    data class Html(val html: String, val baseUrl: String? = null) : DdWebContent
}

enum class DdWebViewErrorKind {
    NETWORK,
    HTTP,
    RENDERER,
}

data class DdWebViewError(
    val kind: DdWebViewErrorKind,
    val url: String?,
    val description: String,
    val httpStatusCode: Int? = null,
)

class DdWebViewState {
    internal var webView: WebView? = null

    val canGoBack: Boolean
        get() = webView?.canGoBack() == true

    val canGoForward: Boolean
        get() = webView?.canGoForward() == true

    val currentUrl: String?
        get() = webView?.url

    fun goBack() {
        webView?.goBack()
    }

    fun goForward() {
        webView?.goForward()
    }

    fun reload() {
        webView?.reload()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun evaluateJavascript(script: String, callback: ((String?) -> Unit)? = null) {
        webView?.evaluateJavascript(script) { value -> callback?.invoke(value) }
    }

    fun dispose() {
        val view = webView ?: return
        webView = null
        destroyDdWebView(view)
    }
}

@Composable
fun DdWebView(
    url: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    state: DdWebViewState = remember { DdWebViewState() },
    javaScriptEnabled: Boolean = true,
    domStorageEnabled: Boolean = true,
    allowFileAccess: Boolean = false,
    darkMode: Boolean = true,
    onPageStarted: ((String?) -> Unit)? = null,
    onPageFinished: ((String?) -> Unit)? = null,
    onUrlChanged: ((String) -> Unit)? = null,
    onError: ((DdWebViewError) -> Unit)? = null,
) {
    DdWebView(
        content = DdWebContent.Url(url),
        modifier = modifier,
        state = state,
        javaScriptEnabled = javaScriptEnabled,
        domStorageEnabled = domStorageEnabled,
        allowFileAccess = allowFileAccess,
        darkMode = darkMode,
        onPageStarted = onPageStarted,
        onPageFinished = onPageFinished,
        onUrlChanged = onUrlChanged,
        onError = onError,
    )
}

@Composable
fun DdWebView(
    content: DdWebContent,
    modifier: Modifier = Modifier.fillMaxSize(),
    state: DdWebViewState = remember { DdWebViewState() },
    javaScriptEnabled: Boolean = true,
    domStorageEnabled: Boolean = true,
    allowFileAccess: Boolean = false,
    darkMode: Boolean = true,
    onPageStarted: ((String?) -> Unit)? = null,
    onPageFinished: ((String?) -> Unit)? = null,
    onUrlChanged: ((String) -> Unit)? = null,
    onError: ((DdWebViewError) -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val currentStarted = rememberUpdatedState(onPageStarted)
    val currentFinished = rememberUpdatedState(onPageFinished)
    val currentUrlChanged = rememberUpdatedState(onUrlChanged)
    val currentError = rememberUpdatedState(onError)
    DisposableEffect(state) {
        onDispose { state.dispose() }
    }
    key(content) {
        AndroidView(
            factory = { viewContext ->
                createDdWebView(
                    context = viewContext,
                    content = content,
                    state = state,
                    background = colors.paper.toArgb(),
                    javaScriptEnabled = javaScriptEnabled,
                    domStorageEnabled = domStorageEnabled,
                    allowFileAccess = allowFileAccess,
                    darkMode = darkMode,
                    onPageStarted = { url -> currentStarted.value?.invoke(url) },
                    onPageFinished = { url -> currentFinished.value?.invoke(url) },
                    onUrlChanged = { url -> currentUrlChanged.value?.invoke(url) },
                    onError = { error -> currentError.value?.invoke(error) },
                )
            },
            modifier = modifier,
            onRelease = { view ->
                if (state.webView === view) state.dispose() else destroyDdWebView(view)
            },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createDdWebView(
    context: Context,
    content: DdWebContent,
    state: DdWebViewState,
    background: Int,
    javaScriptEnabled: Boolean,
    domStorageEnabled: Boolean,
    allowFileAccess: Boolean,
    darkMode: Boolean,
    onPageStarted: (String?) -> Unit,
    onPageFinished: (String?) -> Unit,
    onUrlChanged: (String) -> Unit,
    onError: (DdWebViewError) -> Unit,
): WebView {
    val webView = WebView(context)
    webView.setBackgroundColor(background)
    webView.settings.apply {
        this.javaScriptEnabled = javaScriptEnabled
        this.domStorageEnabled = domStorageEnabled
        this.allowFileAccess = allowFileAccess
        allowContentAccess = allowFileAccess
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        applyDarkMode(darkMode)
    }
    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onPageFinished(url)
            view.url?.let(onUrlChanged)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            url?.let(onUrlChanged)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            onError(
                DdWebViewError(
                    kind = DdWebViewErrorKind.NETWORK,
                    url = request.url.toString(),
                    description = error.description?.toString().orEmpty(),
                ),
            )
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            onError(
                DdWebViewError(
                    kind = DdWebViewErrorKind.HTTP,
                    url = request.url.toString(),
                    description = "",
                    httpStatusCode = errorResponse.statusCode,
                ),
            )
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            onError(
                DdWebViewError(
                    kind = DdWebViewErrorKind.RENDERER,
                    url = view.url,
                    description = "",
                ),
            )
            return true
        }
    }
    state.webView = webView
    when (content) {
        is DdWebContent.Url -> webView.loadUrl(content.url)
        is DdWebContent.Html -> {
            val baseUrl = content.baseUrl
            if (baseUrl == null) webView.loadDataWithBaseURL(null, content.html, "text/html", "utf-8", null)
            else webView.loadDataWithBaseURL(baseUrl, content.html, "text/html", "utf-8", null)
        }
    }
    return webView
}

private fun WebSettings.applyDarkMode(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        isAlgorithmicDarkeningAllowed = enabled
    } else {
        @Suppress("DEPRECATION")
        forceDark = if (enabled) WebSettings.FORCE_DARK_AUTO else WebSettings.FORCE_DARK_OFF
    }
}

private fun destroyDdWebView(view: WebView) {
    runCatching { view.stopLoading() }
    runCatching { view.loadUrl("about:blank") }
    runCatching { view.removeAllViews() }
    runCatching { (view.parent as? ViewGroup)?.removeView(view) }
    runCatching { view.destroy() }
}
