package io.github.whywood.superpi.plugin

import androidx.annotation.StringRes

interface PluginStrings {
    fun get(@StringRes id: Int): String

    fun get(@StringRes id: Int, vararg args: Any): String
}
