package io.github.whywood.deepdeep.plugin

import android.content.Context
import androidx.annotation.StringRes

class AndroidPluginStrings(
    private val context: Context,
) : PluginStrings {
    override fun get(@StringRes id: Int): String = context.getString(id)

    override fun format(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)
}
