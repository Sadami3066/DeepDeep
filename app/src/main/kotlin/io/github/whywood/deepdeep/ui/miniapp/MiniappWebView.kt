package io.github.whywood.deepdeep.ui.miniapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DeepDeepColors
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.MiniappAgentGateway
import io.github.whywood.deepdeep.runtime.MiniappBridgeRouter
import io.github.whywood.deepdeep.runtime.MiniappCreation
import io.github.whywood.deepdeep.runtime.MiniappData
import io.github.whywood.deepdeep.runtime.MiniappManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TAG = "DeepDeepMiniapp"

class MiniappWebViewState {
    internal var webView: WebView? = null

    fun goBack() {
        webView?.goBack()
    }
}

private class MiniappWebViewRefs {
    var webView: WebView? = null
    var bridge: MiniappBridgeHost? = null
}

@Composable
fun MiniappWebView(
    creation: MiniappCreation,
    data: MiniappData,
    agentGateway: MiniappAgentGateway? = null,
    reloadKey: Int,
    onFatalError: (String) -> Unit,
    onHistoryChanged: (Boolean) -> Unit,
    onRenderCrash: () -> Unit,
    modifier: Modifier = Modifier,
    state: MiniappWebViewState = MiniappWebViewState(),
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manifest = creation.manifest
    val refs = remember(manifest.id) { MiniappWebViewRefs() }
    val assetServer = remember(manifest.id) { MiniappAssetServer(creation.directory, manifest) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val router = remember(manifest.id, agentGateway) {
        MiniappBridgeRouter(
            manifest = manifest,
            data = data,
            toast = { text -> mainHandler.post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() } },
            agent = agentGateway,
        )
    }
    val sdkSource = remember {
        runCatching {
            context.assets.open("miniapp/bridge.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    val envJson = remember(manifest.id) { buildEnvJson(manifest, colors) }
    val themeJson = remember(colors.dark, colors.paper, colors.ink, colors.inkSecondary, colors.accent, colors.hairline) {
        themeObject(colors).toString()
    }
    val currentHistory = rememberUpdatedState(onHistoryChanged)
    val currentCrash = rememberUpdatedState(onRenderCrash)
    val currentFatalError = rememberUpdatedState(onFatalError)
    val currentThemeJson = rememberUpdatedState(themeJson)
    var loading by remember(manifest.id) { mutableStateOf(true) }
    var crashKey by remember(manifest.id) { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        if (reloadKey > 0) refs.webView?.reload()
    }
    LaunchedEffect(themeJson) {
        refs.webView?.evaluateJavascript(themeApplyScript(themeJson), null)
    }
    DisposableEffect(manifest.id) {
        onDispose {
            refs.bridge?.detach()
            refs.bridge = null
            val view = refs.webView
            refs.webView = null
            if (state.webView === view) state.webView = null
            if (view != null) destroyWebView(view)
        }
    }

    Box(modifier.fillMaxSize().imePadding()) {
        key(crashKey) {
            AndroidView(
                factory = { viewContext ->
                    createMiniappWebView(
                        context = viewContext,
                        creation = creation,
                        assetServer = assetServer,
                        router = router,
                        scope = scope,
                        envJson = envJson,
                        sdkSource = sdkSource,
                        colors = colors,
                        refs = refs,
                        state = state,
                        onLoadingChanged = { loading = it },
                        onHistoryChanged = { canGoBack -> currentHistory.value(canGoBack) },
                        onRenderCrash = {
                            currentCrash.value()
                            loading = false
                            crashKey++
                        },
                        onFatalError = { message -> currentFatalError.value(message) },
                        themeJson = { currentThemeJson.value },
                    )
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { wrapper ->
                    refs.bridge?.detach()
                    refs.bridge = null
                    val view = refs.webView
                    refs.webView = null
                    if (state.webView === view) state.webView = null
                    if (view != null) destroyWebView(view)
                    (wrapper as? FrameLayout)?.removeAllViews()
                },
            )
        }
        if (loading) {
            Box(
                Modifier.fillMaxSize().background(colors.paper),
                contentAlignment = Alignment.Center,
            ) {
                DdSpinner(size = 22.dp, strokeWidth = 2.dp)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createMiniappWebView(
    context: Context,
    creation: MiniappCreation,
    assetServer: MiniappAssetServer,
    router: MiniappBridgeRouter,
    scope: CoroutineScope,
    envJson: String,
    sdkSource: String,
    colors: DeepDeepColors,
    refs: MiniappWebViewRefs,
    state: MiniappWebViewState,
    onLoadingChanged: (Boolean) -> Unit,
    onHistoryChanged: (Boolean) -> Unit,
    onRenderCrash: () -> Unit,
    onFatalError: (String) -> Unit,
    themeJson: () -> String,
): View {
    val webView = WebView(context)
    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
    webView.setBackgroundColor(colors.paper.toArgb())
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = false
        setSupportZoom(false)
        builtInZoomControls = false
        displayZoomControls = false
    }
    val bridgeHost = MiniappBridgeHost(
        webView = webView,
        scope = scope,
        router = router,
        origin = assetServer.origin,
    )
    webView.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetServer.shouldInterceptRequest(request)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.host.equals(assetServer.domain, ignoreCase = true)) return false
            val launched = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (!launched) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, context.getString(R.string.miniapp_link_failed), Toast.LENGTH_SHORT).show()
                }
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (!isMiniappUrl(url, assetServer.origin)) return
            onLoadingChanged(true)
            onHistoryChanged(view.canGoBack())
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (!isMiniappUrl(url, assetServer.origin)) return
            onLoadingChanged(false)
            onHistoryChanged(view.canGoBack())
            view.evaluateJavascript(themeApplyScript(themeJson()), null)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            onLoadingChanged(false)
            onFatalError(error.description.toString())
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (!request.isForMainFrame) return
            onLoadingChanged(false)
            onFatalError(context.getString(R.string.miniapp_http_error, errorResponse.statusCode))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            onFatalError(context.getString(R.string.miniapp_renderer_crashed))
            onRenderCrash()
            return true
        }
    }
    webView.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            Log.d(TAG, message.message() + " (" + message.sourceId() + ":" + message.lineNumber() + ")")
            return true
        }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            bridgeHost.documentStartScript(envJson, sdkSource),
            setOf(assetServer.origin),
        )
    }
    bridgeHost.attach()
    refs.webView = webView
    refs.bridge = bridgeHost
    state.webView = webView
    val container = FrameLayout(context)
    container.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    webView.loadUrl(assetServer.origin + "/" + creation.manifest.entry)
    return container
}

private fun destroyWebView(view: WebView) {
    Log.d(TAG, "destroying miniapp webview")
    runCatching { view.stopLoading() }
    runCatching { view.loadUrl("about:blank") }
    runCatching { view.removeAllViews() }
    runCatching { view.destroy() }
}

private fun buildEnvJson(manifest: MiniappManifest, colors: DeepDeepColors): String = buildJsonObject {
    putJsonObject("app") {
        put("id", manifest.id)
        put("name", manifest.name)
        put("icon", manifest.icon.ifBlank { "✦" })
        put("version", manifest.version)
    }
    put("theme", themeObject(colors))
}.toString()

private fun themeObject(colors: DeepDeepColors): JsonObject = buildJsonObject {
    put("dark", colors.dark)
    put("bg", hexColor(colors.paper))
    put("ink", hexColor(colors.ink))
    put("inkSecondary", hexColor(colors.inkSecondary))
    put("accent", hexColor(colors.accent))
    put("hairline", hexColor(colors.hairline))
}

private fun hexColor(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)

private fun isMiniappUrl(url: String?, origin: String): Boolean =
    url != null && (url == origin || url.startsWith(origin + "/"))

private fun themeApplyScript(themeJson: String): String =
    "if (typeof window.__deepdeepApplyTheme === 'function') { window.__deepdeepApplyTheme($themeJson) }"
