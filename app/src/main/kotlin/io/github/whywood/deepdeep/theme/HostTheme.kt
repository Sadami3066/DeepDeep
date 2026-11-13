package io.github.whywood.deepdeep.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.DdFonts
import io.github.whywood.deepdeep.design.DeepDeepTheme

@Composable
fun HostTheme(
    controller: ThemeController,
    fontStyle: DdFontStyle = DdFonts.style,
    content: @Composable () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val colors = remember(state, systemDark) {
        state.colors ?: ThemePalettes.of(state.choice, systemDark)
    }
    HostThemeRuntime.dark = colors.dark
    SystemBarIcons(dark = colors.dark)
    DeepDeepTheme(colors = colors, fontStyle = fontStyle, content = content)
}

@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() } ?: return
    SideEffect {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
