package io.github.whywood.deepdeep.plugin.micloud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.FontPreferences
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.ThemePreferences
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MiCloudLoginActivity : ComponentActivity() {
    private val cookieStore by lazy { MiCloudCookieStore(applicationContext) }
    private val state = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    private var webView: WebView? = null
    private var watchJob: Job? = null
    private var ignoredCookie: String? = null
    private var triedCookie: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by produceState(initialValue = ThemeMode.SYSTEM) {
                value = runCatching { ThemePreferences(applicationContext).theme.first() }
                    .getOrDefault(ThemeMode.SYSTEM)
            }
            val fontStyle = remember { FontPreferences(applicationContext).read() }
            val uiState by state.collectAsStateWithLifecycle()
            DeepDeepTheme(theme, fontStyle) {
                LoginContent(uiState)
            }
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val view = webView
                    if (view != null && view.canGoBack()) view.goBack() else finish()
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        startWatching()
    }

    override fun onStop() {
        watchJob?.cancel()
        watchJob = null
        super.onStop()
    }

    override fun onDestroy() {
        watchJob?.cancel()
        watchJob = null
        val view = webView
        webView = null
        view?.let { released ->
            runCatching { released.stopLoading() }
            runCatching { released.removeAllViews() }
            runCatching { released.destroy() }
        }
        super.onDestroy()
    }

    private fun startWatching() {
        if (watchJob?.isActive == true) return
        watchJob = lifecycleScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (isFinishing) continue
                val cookie = currentCookie() ?: continue
                if (parseCookie(cookie)["serviceToken"] == null) continue
                if (cookie == ignoredCookie || cookie == triedCookie) continue
                verify(cookie)
            }
        }
    }

    private suspend fun verify(cookie: String) {
        state.value = LoginUiState.Verifying
        triedCookie = cookie
        try {
            MiCloudClient(cookieProvider = { cookie }).profile()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expired: MiCloudAuthException) {
            ignoredCookie = cookie
            clearOriginCookies()
            state.value = LoginUiState.Loading
            webView?.loadUrl(ORIGIN)
            return
        } catch (error: Exception) {
            state.value = LoginUiState.Error(getString(R.string.plugin_micloud_login_verify_error))
            return
        }
        cookieStore.save(cookie)
        runCatching { CookieManager.getInstance().flush() }
        state.value = LoginUiState.Loading
        finish()
    }

    private fun retry() {
        ignoredCookie = null
        triedCookie = null
        state.value = LoginUiState.Loading
        clearOriginCookies()
        webView?.loadUrl(ORIGIN)
    }

    private fun currentCookie(): String? =
        runCatching { CookieManager.getInstance().getCookie(ORIGIN) }.getOrNull()

    private fun clearOriginCookies() {
        clearMiCloudWebCookies()
    }

    @Composable
    private fun LoginContent(uiState: LoginUiState) {
        val colors = deepDeepColors()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.paper)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.plugin_micloud_login_title),
                    modifier = Modifier.weight(1f),
                    style = DdType.label,
                    color = colors.ink,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = { finish() },
                    contentDescription = stringResource(R.string.plugin_micloud_login_close),
                    tint = colors.inkSecondary,
                    iconSize = 18.dp,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    factory = { viewContext ->
                        createWebView(viewContext).also { created -> webView = created }
                    },
                    modifier = Modifier.fillMaxSize().testTag("micloud_login_webview"),
                    onRelease = { released ->
                        if (webView === released) {
                            webView = null
                            released.removeAllViews()
                            released.destroy()
                        }
                    },
                )
                when (uiState) {
                    LoginUiState.Loading -> Unit

                    LoginUiState.Verifying -> Box(
                        modifier = Modifier.fillMaxSize().background(colors.paper.copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DdSpinner(size = 22.dp, strokeWidth = 2.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.plugin_micloud_login_checking),
                                style = DdType.bodySmall,
                                color = colors.inkSecondary,
                            )
                        }
                    }

                    is LoginUiState.Error -> Box(
                        modifier = Modifier.fillMaxSize().background(colors.paper),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = uiState.message,
                                style = DdType.body,
                                color = colors.ink,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            DdPrimaryButton(
                                text = stringResource(R.string.plugin_micloud_login_retry),
                                onClick = { retry() },
                                modifier = Modifier.fillMaxWidth().testTag("micloud_login_retry"),
                            )
                            Spacer(Modifier.height(6.dp))
                            DdTextAction(
                                text = stringResource(R.string.plugin_micloud_login_close),
                                onClick = { finish() },
                                color = colors.inkSecondary,
                            )
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val view = WebView(context)
        view.setBackgroundColor(Color.WHITE)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "")
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
                if (!host.endsWith("mi.com") && !host.endsWith("xiaomi.com")) return
                view.evaluateJavascript(VIEWPORT_FIX_SCRIPT, null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                state.value = LoginUiState.Error(getString(R.string.plugin_micloud_login_page_error))
            }
        }
        view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            private var started = false

            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                if (started || v.width <= 0 || v.height <= 0) return
                started = true
                view.removeOnLayoutChangeListener(this)
                view.loadUrl(ORIGIN)
            }
        })
        return view
    }

    private sealed interface LoginUiState {
        data object Loading : LoginUiState

        data object Verifying : LoginUiState

        data class Error(val message: String) : LoginUiState
    }

    private companion object {
        const val ORIGIN = "https://i.mi.com"
        const val POLL_INTERVAL_MS = 1_000L

        val VIEWPORT_FIX_SCRIPT = """
            (function () {
              var probe = document.createElement('div');
              probe.style.cssText = 'position:fixed;left:0;top:0;width:0;height:100vh;visibility:hidden;';
              (document.body || document.documentElement).appendChild(probe);
              var viewportHeight = probe.getBoundingClientRect().height;
              if (probe.parentNode) probe.parentNode.removeChild(probe);
              if (viewportHeight !== 0 || window.innerHeight <= 0) return;
              var style = document.getElementById('deepdeep-viewport-fix');
              if (!style) {
                style = document.createElement('style');
                style.id = 'deepdeep-viewport-fix';
                (document.head || document.documentElement).appendChild(style);
              }
              var height = window.innerHeight;
              style.textContent = 'html,body,#root,#app,.micloud-app{height:auto!important;min-height:' +
                height + 'px!important}body{overflow:auto!important}';
            })();
        """.trimIndent()
    }
}

internal fun clearMiCloudWebCookies() {
    val manager = CookieManager.getInstance()
    val cookie = runCatching { manager.getCookie("https://i.mi.com") }.getOrNull().orEmpty()
    if (cookie.isNotBlank()) {
        parseCookie(cookie).keys.forEach { name ->
            listOf("", "Domain=.i.mi.com; ", "Domain=.mi.com; ").forEach { domain ->
                runCatching { manager.setCookie("https://i.mi.com", "$name=; Max-Age=0; Path=/; $domain") }
            }
        }
    }
    manager.flush()
}
