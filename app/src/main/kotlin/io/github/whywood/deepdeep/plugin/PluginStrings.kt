package io.github.whywood.deepdeep.plugin

import androidx.annotation.StringRes

interface PluginStrings {
    fun get(@StringRes id: Int): String
}
