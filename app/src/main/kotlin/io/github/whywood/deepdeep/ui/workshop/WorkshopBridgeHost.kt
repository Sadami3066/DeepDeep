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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject

private const val TAG = "DeepDeepWorkshop"
const val WORKSHOP_JS_OBJECT = "DeepDeepNative"
private const val BRIDGE_DISPATCH = "window.__deepdeepBridge.dispatch"
private const val BRIDGE_DISPATCH_EVENT = "window.__deepdeepBridge.dispatchEvent"
private const val EVENT_TEXT_DELTA = "text_delta"
private const val EVENT_THINKING_DELTA = "thinking_delta"
private const val EVENT_BATCH_MS = 40L

private sealed interface PendingPush {
    class Event(val payload: String) : PendingPush

    class Delta(val id: Long, val type: String) : PendingPush {
        val text = StringBuilder()
    }
}

class WorkshopBridgeHost(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val router: WorkshopBridgeRouter,
    private val origin: String,
) {
    @Volatile
    private var attached = false

    private val queueLock = Any()
    private val pendingPushes = mutableListOf<PendingPush>()
    private var flushScheduled = false

    private val flushRunnable = Runnable {
        if (canInject()) flushPending()
    }

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
        synchronized(queueLock) {
            pendingPushes.clear()
            flushScheduled = false
        }
        runCatching { WebViewCompat.removeWebMessageListener(webView, WORKSHOP_JS_OBJECT) }
    }

    fun documentStartScript(envJson: String, sdkSource: String): String = buildString {
        append("window.__DEEPDEEP_ENV__ = ")
        append(envJson)
        append(";\n")
        append(sdkSource)
    }

    private suspend fun handleMessage(raw: String) {
        try {
            val response = router.handle(raw) { envelope -> emitEvent(envelope) }
            dispatchJson(response)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Workshop bridge request failed", error)
            dispatchJson(errorEnvelope(requestId(raw), error.message ?: error.javaClass.simpleName))
        }
    }

    private fun emitEvent(envelope: JsonObject) {
        if (!attached) return
        val id = (envelope["id"] as? JsonPrimitive)?.longOrNull ?: return
        val event = envelope["event"] as? JsonObject ?: return
        val type = (event["type"] as? JsonPrimitive)?.contentOrNull ?: return
        val text = (event["text"] as? JsonPrimitive)?.contentOrNull
        val isDelta = (type == EVENT_TEXT_DELTA || type == EVENT_THINKING_DELTA) && text != null
        val scheduled = synchronized(queueLock) {
            if (isDelta) {
                val last = pendingPushes.lastOrNull()
                if (last is PendingPush.Delta && last.id == id && last.type == type) {
                    last.text.append(text)
                } else {
                    pendingPushes.add(PendingPush.Delta(id, type).apply { this.text.append(text) })
                }
            } else {
                pendingPushes.add(PendingPush.Event(envelope.toString()))
            }
            if (flushScheduled) {
                false
            } else {
                flushScheduled = true
                true
            }
        }
        if (scheduled && !webView.postDelayed(flushRunnable, EVENT_BATCH_MS)) {
            synchronized(queueLock) { flushScheduled = false }
        }
    }

    private fun flushPending() {
        val pushes: List<PendingPush>
        synchronized(queueLock) {
            if (pendingPushes.isEmpty()) {
                flushScheduled = false
                return
            }
            pushes = pendingPushes.toList()
            pendingPushes.clear()
            flushScheduled = false
        }
        inject(buildPushScript(pushes))
    }

    private fun dispatchJson(payload: String) {
        postToPage {
            flushPending()
            inject(BRIDGE_DISPATCH + "(" + JSONObject.quote(payload) + ")")
        }
    }

    private fun postToPage(block: () -> Unit) {
        if (!attached) return
        webView.post {
            if (!canInject()) return@post
            runCatching(block).onFailure { error -> Log.e(TAG, "Cannot inject workshop event", error) }
        }
    }

    private fun canInject(): Boolean = attached

    private fun inject(script: String) {
        if (!canInject()) return
        runCatching { webView.evaluateJavascript(script, null) }
            .onFailure { error -> Log.e(TAG, "Cannot inject workshop script", error) }
    }

    private fun buildPushScript(pushes: List<PendingPush>): String = buildString {
        pushes.forEachIndexed { index, push ->
            if (index > 0) append(';')
            val payload = when (push) {
                is PendingPush.Delta -> buildJsonObject {
                    put("id", push.id)
                    putJsonObject("event") {
                        put("type", push.type)
                        put("text", push.text.toString())
                    }
                }.toString()

                is PendingPush.Event -> push.payload
            }
            append(BRIDGE_DISPATCH_EVENT)
            append('(')
            append(JSONObject.quote(payload))
            append(')')
        }
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
}
