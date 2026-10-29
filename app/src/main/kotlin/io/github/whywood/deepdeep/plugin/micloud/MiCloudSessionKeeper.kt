package io.github.whywood.deepdeep.plugin.micloud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal interface MiCloudWebCookieProbe {
    suspend fun awaitServiceTokenCookie(timeoutMs: Long, previousCookie: String?): String?
}

@SuppressLint("SetJavaScriptEnabled")
internal class OffscreenMiCloudWebCookieProbe(context: Context) : MiCloudWebCookieProbe {
    private val appContext = context.applicationContext

    override suspend fun awaitServiceTokenCookie(
        timeoutMs: Long,
        previousCookie: String?,
    ): String? = withContext(Dispatchers.Main) {
        val previousToken = previousCookie?.let { parseCookie(it)[SERVICE_TOKEN] }
        var view: WebView? = null
        try {
            val created = WebView(appContext)
            view = created
            created.setBackgroundColor(Color.WHITE)
            created.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                userAgentString = WebSettings.getDefaultUserAgent(appContext).replace("; wv", "")
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(created, true)
            }
            created.loadUrl(MICLOUD_ORIGIN)
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val cookie = currentCookie()
                val token = cookie?.let { parseCookie(it)[SERVICE_TOKEN] }
                if (token != null && token != previousToken) return@withContext cookie
                delay(POLL_INTERVAL_MS)
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            view?.let(::destroyWebView)
        }
    }

    private fun currentCookie(): String? =
        runCatching { CookieManager.getInstance().getCookie(MICLOUD_ORIGIN) }.getOrNull()

    private fun destroyWebView(view: WebView) {
        runCatching { view.stopLoading() }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
    }

    private companion object {
        const val SERVICE_TOKEN = "serviceToken"
        const val POLL_INTERVAL_MS = 500L
    }
}

internal class MiCloudSessionKeeper(
    private val readSnapshot: suspend () -> String?,
    private val readJarCookie: suspend () -> String?,
    private val verifyCookie: suspend (String) -> Boolean,
    private val saveCookie: suspend (String) -> Unit,
    private val webCookieProbe: MiCloudWebCookieProbe,
    private val readRecoveryBlocked: suspend () -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val webViewGate = AtomicBoolean(false)
    private val lastWebViewAttemptAt = AtomicLong(-WEBVIEW_ATTEMPT_INTERVAL_MS)

    fun canAttemptWebView(): Boolean = clock() - lastWebViewAttemptAt.get() >= WEBVIEW_ATTEMPT_INTERVAL_MS

    suspend fun recoverFromJar(): Boolean {
        if (readRecoveryBlocked()) return false
        val jar = readJarCookie() ?: return false
        if (parseCookie(jar)[SERVICE_TOKEN] == null) return false
        if (readSnapshot() == jar) return false
        if (!verifyCookie(jar)) return false
        saveCookie(jar)
        return true
    }

    suspend fun recoverFromWebView(): Boolean {
        if (readRecoveryBlocked()) return false
        if (!webViewGate.compareAndSet(false, true)) return false
        try {
            if (!canAttemptWebView()) return false
            lastWebViewAttemptAt.set(clock())
            val previous = readJarCookie()
            val cookie = webCookieProbe.awaitServiceTokenCookie(WEBVIEW_TIMEOUT_MS, previous) ?: return false
            if (parseCookie(cookie)[SERVICE_TOKEN] == null) return false
            if (!verifyCookie(cookie)) return false
            saveCookie(cookie)
            return true
        } finally {
            webViewGate.set(false)
        }
    }

    suspend fun recover(): Boolean {
        if (readRecoveryBlocked()) return false
        val snapshot = readSnapshot()
        if (snapshot != null && parseCookie(snapshot)[SERVICE_TOKEN] != null && verifyCookie(snapshot)) return true
        if (recoverFromJar()) return true
        return recoverFromWebView()
    }

    private companion object {
        const val SERVICE_TOKEN = "serviceToken"
        const val WEBVIEW_TIMEOUT_MS = 12_000L
        const val WEBVIEW_ATTEMPT_INTERVAL_MS = 5L * 60L * 1000L
    }
}
