package io.github.whywood.deepdeep.plugin.host.events

import android.util.Log

internal enum class PluginHookStage {
    OBSERVE,
    PROMPT_TRANSFORM,
    INPUT_TRANSFORM,
    BEFORE_TOOL_CALL,
    AFTER_TOOL_EXECUTION,
}

internal data class PluginHookFailure(
    val pluginId: String,
    val stage: PluginHookStage,
    val message: String,
    val cause: Throwable? = null,
) {
    val description: String
        get() = "plugin $pluginId ${stage.name.lowercase()}: $message"
}

internal fun interface PluginHookReporter {
    fun report(failure: PluginHookFailure)

    companion object {
        val None: PluginHookReporter = object : PluginHookReporter {
            override fun report(failure: PluginHookFailure) = Unit
        }

        val Logging: PluginHookReporter = object : PluginHookReporter {
            override fun report(failure: PluginHookFailure) {
                runCatching { Log.w(PLUGIN_HOOK_TAG, failure.description, failure.cause) }
            }
        }
    }
}

private const val PLUGIN_HOOK_TAG = "PluginHooks"
