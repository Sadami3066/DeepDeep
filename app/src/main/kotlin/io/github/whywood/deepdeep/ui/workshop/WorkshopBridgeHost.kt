package io.github.whywood.deepdeep.ui.workshop

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.whywood.deepdeep.runtime.WorkshopBridgeRouter
import io.github.whywood.deepdeep.runtime.WorkshopPageError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.json.JSONObject

private const val TAG = "DeepDeepWorkshop"
const val WORKSHOP_JS_OBJECT = "DeepDeepNative"
private const val TEXT_BATCH_MILLIS = 50L
private const val BRIDGE_DISPATCH = "window.__deepdeepBridge.dispatch"

class WorkshopBridgeHost(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val router: WorkshopBridgeRouter,
    private val origin: String,
    private val onPageError: (WorkshopPageError) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val textLock = Any()
    private val pendingText = StringBuilder()
    private var flushJob: Job? = null
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
        flushJob?.cancel()
        flushJob = null
        synchronized(textLock) { pendingText.setLength(0) }
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
        val message = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        val event = message.stringField("event")
        if (event != null) {
            if (event == "log.error") recordPageError(message)
            return
        }
        val id = (message["id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
        val response = try {
            val result = router.handle(raw) { agentEvent -> emitEvent(agentEvent) }
            flushText()
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Workshop bridge request failed", error)
            errorEnvelope(id, error.message ?: error.javaClass.simpleName)
        }
        dispatchJson(response)
    }

    private suspend fun emitEvent(event: JsonObject) {
        val isText = event.stringField("event") == "agent" && event.stringField("type") == "text"
        if (isText) {
            appendText(event.stringField("delta").orEmpty())
            return
        }
        flushText()
        dispatchJson(event.toString())
    }

    private fun appendText(delta: String) {
        if (delta.isEmpty()) return
        synchronized(textLock) { pendingText.append(delta) }
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            delay(TEXT_BATCH_MILLIS)
            flushText()
        }
    }

    private fun flushText() {
        val text = synchronized(textLock) {
            pendingText.toString().also { pendingText.setLength(0) }
        }
        if (text.isEmpty()) return
        val event = buildJsonObject {
            put("event", "agent")
            put("type", "text")
            put("delta", text)
        }
        dispatchJson(event.toString())
    }

    private fun recordPageError(message: JsonObject) {
        val error = WorkshopPageError(
            message = message.stringField("message").orEmpty(),
            source = message.stringField("source"),
            line = message.stringField("line")?.toIntOrNull(),
            column = message.stringField("column")?.toIntOrNull(),
            stack = message.stringField("stack"),
            timestamp = System.currentTimeMillis(),
        )
        mainHandler.post { onPageError(error) }
    }

    private fun errorEnvelope(id: Long?, message: String): String = buildJsonObject {
        if (id == null) put("id", kotlinx.serialization.json.JsonNull) else put("id", id)
        put("ok", false)
        put("error", buildJsonObject {
            put("code", "invalid_request")
            put("message", message)
        })
    }.toString()

    private fun dispatchJson(payload: String) {
        webView.post {
            webView.evaluateJavascript(BRIDGE_DISPATCH + "(" + JSONObject.quote(payload) + ")", null)
        }
    }

    private fun JsonObject.stringField(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull
}
