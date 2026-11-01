package io.github.whywood.deepdeep.ui.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import io.github.whywood.deepdeep.MainActivity

internal fun shareChooser(context: Context, intent: Intent, title: String): Intent =
    Intent.createChooser(intent, title).apply {
        putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(context.packageName, MainActivity::class.java.name)),
        )
    }
