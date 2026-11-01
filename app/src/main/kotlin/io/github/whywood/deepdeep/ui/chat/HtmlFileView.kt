package io.github.whywood.deepdeep.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.File

internal class HtmlWebViewHolder {
    private var container: View? = null
    private var webView: WebView? = null

    fun attach(container: View, webView: WebView) {
        this.container = container
        this.webView = webView
    }

    fun release(released: View? = null) {
        val heldContainer = container
        if (released != null && heldContainer != null && heldContainer !== released) return
        val current = webView ?: return
        container = null
        webView = null
        runCatching { current.stopLoading() }
        runCatching { current.destroy() }
    }
}

@Composable
internal fun HtmlFileView(
    path: String,
    modifier: Modifier,
    onActivate: (() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    val holder = remember(path) { HtmlWebViewHolder() }
    var crashKey by remember(path) { mutableIntStateOf(0) }
    var renderFailed by remember(path) { mutableStateOf(false) }
    var loadFailed by remember(path) { mutableStateOf(false) }
    DisposableEffect(path) {
        onDispose { holder.release() }
    }
    Box(modifier) {
        if (renderFailed || loadFailed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.files_read_failed),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        } else {
            key(crashKey) {
                AndroidView(
                    factory = { context ->
                        val webView = createHtmlWebView(
                            context = context,
                            path = path,
                            onRenderCrash = {
                                holder.release()
                                if (crashKey == 0) crashKey++ else renderFailed = true
                            },
                            onLoadFailed = { loadFailed = true },
                        )
                        val frame = FrameLayout(context)
                        frame.addView(
                            webView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        holder.attach(frame, webView)
                        frame
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { released -> holder.release(released) },
                )
            }
        }
        if (onActivate != null && !renderFailed && !loadFailed) {
            Box(Modifier.matchParentSize().clickable(onClick = onActivate))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createHtmlWebView(
    context: Context,
    path: String,
    onRenderCrash: () -> Unit,
    onLoadFailed: () -> Unit,
): WebView =
    WebView(context).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "file") {
                    val folder = File(path).parentFile?.absolutePath ?: return true
                    val target = uri.path ?: return true
                    return !target.startsWith(folder)
                }
                val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                onLoadFailed()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
                onRenderCrash()
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean = true
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        loadUrl(Uri.fromFile(File(path)).toString())
    }
