package io.github.whywood.superpi.plugin

import android.content.Context
import androidx.annotation.StringRes
import io.github.whywood.superpi.data.LocalizedContext

class AndroidPluginStrings(
    context: Context,
) : PluginStrings {
    private val localized = LocalizedContext(context)

    override fun get(@StringRes id: Int): String = localized.resources.getString(id)

    override fun get(@StringRes id: Int, vararg args: Any): String = localized.resources.getString(id, *args)
}
