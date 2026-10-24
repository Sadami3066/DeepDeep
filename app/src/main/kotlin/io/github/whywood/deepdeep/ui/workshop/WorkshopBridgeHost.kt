package io.github.whywood.deepdeep.ui.workshop

import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.whywood.deepdeep.runtime.WorkshopBridgeRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "DeepDeepWorkshop"
const val WORKSHOP_JS_OBJECT = "DeepDeepNative"
private const val BRIDGE_DISPATCH = "window.__deepdeepBridge.dispatch"

class WorkshopBridgeHost(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val router: WorkshopBridgeRouter,
    private val origin: String,
) {
    private var attached = false

    private val listener = WebViewCompat.WebMessageListener { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame) return@WebMessageListener
        if (sourceOrigin.toString().trimEnd('/') != origin.trimEnd('/')) return@WebMessageListener
        val raw = message.data ?: return@WebMessageListener
        scope.launch(Dispatchers.IO) { handleMessage(raw) }
    }

    fun attach(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        if (attached) return true
        return runCatching {
            WebViewCompat.addWebMessageListener(webView, WORKSHOP_JS_OBJECT, setOf(origin), listener)
            attached = true
            true
        }.getOrElse { error ->
            Log.e(TAG, "Cannot install workshop bridge", error)
            false
        }
    }

    fun detach() {
        if (!attached) return
        attached = false
        runCatching { WebViewCompat.removeWebMessageListener(webView, WORKSHOP_JS_OBJECT) }
    }

    fun documentStartScript(envJson: String, sdkSource: String): String = buildString {
        append("window.__DEEPDEEP_ENV__ = ")
        append(envJson)
        append(";\n")
        append(sdkSource)
    }

    private suspend fun handleMessage(raw: String) {
        val response = try {
            router.handle(raw) { }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Workshop bridge request failed", error)
            errorEnvelope(requestId(raw), error.message ?: error.javaClass.simpleName)
        }
        dispatchJson(response)
    }

    private fun requestId(raw: String): Long? = runCatching {
        val message = JSONObject(raw)
        if (message.has("id") && !message.isNull("id")) message.getLong("id") else null
    }.getOrNull()

    private fun errorEnvelope(id: Long?, message: String): String = JSONObject().apply {
        put("id", id ?: JSONObject.NULL)
        put("ok", false)
        put("error", JSONObject().apply {
            put("code", "invalid_request")
            put("message", message)
        })
    }.toString()

    private fun dispatchJson(payload: String) {
        webView.post {
            webView.evaluateJavascript(BRIDGE_DISPATCH + "(" + JSONObject.quote(payload) + ")", null)
        }
    }
}
