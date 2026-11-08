package io.github.whywood.deepdeep.ui.remote

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.remote.RemoteConnection
import io.github.whywood.deepdeep.data.remote.RemoteGatewayException
import io.github.whywood.deepdeep.data.remote.RemoteGatewayFailure
import io.github.whywood.deepdeep.data.remote.RemoteSession
import io.github.whywood.deepdeep.data.remote.RemoteTls
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Composable
internal fun RemoteConnectScreen(
    computerId: String,
    connection: RemoteConnection,
    onBack: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var session by remember(computerId) { mutableStateOf<RemoteSession?>(null) }
    var failure by remember(computerId) { mutableStateOf<RemoteGatewayFailure?>(null) }
    var tlsRejected by remember(computerId) { mutableStateOf(false) }
    var attempt by remember(computerId) { mutableIntStateOf(0) }
    val webViewState = remember(computerId) { RemoteWebViewState() }

    LaunchedEffect(computerId, attempt) {
        session = null
        failure = null
        tlsRejected = false
        runCatching {
            connection.refreshComputer(computerId)
            connection.connect(computerId)
        }.onSuccess { session = it }
            .onFailure { error -> failure = (error as? RemoteGatewayException)?.failure }
    }

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = { if (!webViewState.goBack()) onBack() },
                contentDescription = stringResource(R.string.remote_back),
                iconSize = 21.dp,
                tint = colors.ink,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = session?.computer?.name ?: stringResource(R.string.remote_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (session != null) {
                DdIconButton(
                    icon = DdIcons.Refresh,
                    onClick = { webViewState.reload() },
                    contentDescription = stringResource(R.string.remote_connect_refresh),
                    iconSize = 21.dp,
                    tint = colors.ink,
                )
            }
        },
        modifier = modifier.testTag("remote_connect_screen"),
    ) {
        val current = session
        when {
            tlsRejected || failure == RemoteGatewayFailure.CertificateRejected -> ConnectFailureContent(
                failure = RemoteGatewayFailure.CertificateRejected,
                onRetry = { attempt += 1 },
                onRepair = onRepair,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            current != null -> RemoteWebView(
                session = current,
                state = webViewState,
                onTlsRejected = { tlsRejected = true },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            failure != null -> ConnectFailureContent(
                failure = failure,
                onRetry = { attempt += 1 },
                onRepair = onRepair,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            else -> ConnectLoadingContent(
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun ConnectLoadingContent(modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DdSpinner(color = colors.inkTertiary)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.remote_connect_loading),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun ConnectFailureContent(
    failure: RemoteGatewayFailure?,
    onRetry: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Globe, tint = colors.inkTertiary, size = 28.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(failure.messageRes()),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DdTextAction(
                text = stringResource(R.string.remote_connect_retry),
                onClick = onRetry,
            )
            DdTextAction(
                text = stringResource(R.string.remote_connect_repair),
                onClick = onRepair,
                color = colors.inkSecondary,
            )
        }
    }
}

internal class RemoteWebViewState {
    var webView: WebView? = null
    var canGoBack by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set

    fun started(view: WebView) {
        webView = view
        loading = true
        canGoBack = view.canGoBack()
    }

    fun finished(view: WebView) {
        loading = false
        canGoBack = view.canGoBack()
    }

    fun goBack(): Boolean {
        val view = webView ?: return false
        if (!view.canGoBack()) return false
        view.goBack()
        return true
    }

    fun reload() {
        webView?.reload()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun RemoteWebView(
    session: RemoteSession,
    state: RemoteWebViewState,
    onTlsRejected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = deepDeepColors()
    val origin = session.entry.origin
    val originUri = remember(origin) { runCatching { URI(origin) }.getOrNull() }
    val pinnedCa = remember(session.entry.certDerBase64) {
        session.entry.certDerBase64?.let(RemoteTls::decode)
    }
    val script = remember(colors) { RemoteInjection.documentStartScript(colors) }
    val startUrl = remember(session) { session.startUrl }
    var pageError by remember(session) { mutableStateOf(false) }
    var crashKey by remember(session) { mutableIntStateOf(0) }
    var documentStartSupported by remember(session) { mutableStateOf(true) }
    var fileCallback by remember(session) { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingPermission by remember(session) { mutableStateOf<PermissionRequest?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileCallback ?: return@rememberLauncherForActivityResult
        fileCallback = null
        callback.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val request = pendingPermission ?: return@rememberLauncherForActivityResult
        pendingPermission = null
        if (results.values.any { it }) {
            request.grant(request.resources)
        } else {
            request.deny()
        }
    }

    key(crashKey) {
        AndroidView(
            factory = { viewContext ->
                if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                WebView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(colors.paper.toArgb())
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        userAgentString = "$userAgentString DeepDeep"
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            if (!request.isForMainFrame) return false
                            val url = request.url
                            if (originUri != null && url.host == originUri.host && url.port == originUri.port) {
                                return false
                            }
                            if (url.scheme == "http" || url.scheme == "https") {
                                runCatching {
                                    viewContext.startActivity(Intent(Intent.ACTION_VIEW, url))
                                }
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            pageError = false
                            state.started(view)
                            if (!documentStartSupported) {
                                view.evaluateJavascript(script, null)
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            state.finished(view)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) pageError = true
                        }

                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: SslError,
                        ) {
                            if (verifyPinnedCertificate(pinnedCa, error.certificate)) {
                                handler.proceed()
                            } else {
                                handler.cancel()
                                onTlsRejected()
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: android.webkit.RenderProcessGoneDetail,
                        ): Boolean {
                            state.webView = null
                            (view.parent as? ViewGroup)?.removeView(view)
                            view.destroy()
                            crashKey += 1
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            val needed = request.resources.mapNotNull { resource ->
                                when (resource) {
                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                                    else -> null
                                }
                            }.distinct()
                            val missing = needed.filter { permission ->
                                ContextCompat.checkSelfPermission(viewContext, permission) !=
                                    PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isEmpty()) {
                                request.grant(request.resources)
                            } else {
                                pendingPermission = request
                                permissionLauncher.launch(missing.toTypedArray())
                            }
                        }

                        override fun onShowFileChooser(
                            view: WebView,
                            callback: ValueCallback<Array<Uri>>,
                            params: FileChooserParams,
                        ): Boolean {
                            fileCallback = callback
                            val intent = params.createIntent()
                            return runCatching { fileLauncher.launch(intent) }
                                .onFailure {
                                    fileCallback = null
                                    callback.onReceiveValue(null)
                                }
                                .isSuccess
                        }
                    }
                    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                        enqueueDownload(viewContext, url, userAgent, contentDisposition, mimeType)
                    }
                    runCatching {
                        WebViewCompat.addDocumentStartJavaScript(this, script, setOf(origin))
                    }.onFailure {
                        documentStartSupported = false
                    }
                    state.webView = this
                    loadUrl(startUrl)
                }
            },
            modifier = modifier,
            onRelease = { view ->
                state.webView = null
                view.stopLoading()
                view.loadUrl("about:blank")
                view.removeAllViews()
                view.destroy()
            },
        )
    }

    BackHandler(enabled = state.canGoBack) {
        state.webView?.goBack()
    }

    if (state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.paper),
            contentAlignment = Alignment.Center,
        ) {
            DdSpinner(color = colors.inkTertiary)
        }
    }
    if (pageError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.paper),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.remote_web_error),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(12.dp))
                DdTextAction(
                    text = stringResource(R.string.remote_connect_retry),
                    onClick = {
                        pageError = false
                        state.reload()
                    },
                )
            }
        }
    }
}

private fun verifyPinnedCertificate(pinnedCa: X509Certificate?, certificate: SslCertificate): Boolean {
    val anchor = pinnedCa ?: return false
    val leaf = certificate.toX509Certificate() ?: return false
    return runCatching {
        leaf.checkValidity()
        leaf.verify(anchor.publicKey)
    }.isSuccess
}

private fun SslCertificate.toX509Certificate(): X509Certificate? {
    val bundle = SslCertificate.saveState(this) ?: return null
    val bytes = bundle.getByteArray("x509-certificate") ?: return null
    return runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }.getOrNull()
}

private fun enqueueDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    if (uri.scheme != "http" && uri.scheme != "https") return
    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val request = DownloadManager.Request(uri)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
    if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
    val cookie = CookieManager.getInstance().getCookie(url)
    if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
    runCatching { context.getSystemService(DownloadManager::class.java)?.enqueue(request) }
}
