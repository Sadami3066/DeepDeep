package io.github.whywood.deepdeep.runtime

import android.util.Log

internal fun logInfo(tag: String, message: String) {
    runCatching { Log.i(tag, message) }
}

internal fun logWarn(tag: String, message: String, error: Throwable? = null) {
    runCatching { Log.w(tag, message, error) }
}

internal fun logError(tag: String, message: String, error: Throwable? = null) {
    runCatching { Log.e(tag, message, error) }
}
