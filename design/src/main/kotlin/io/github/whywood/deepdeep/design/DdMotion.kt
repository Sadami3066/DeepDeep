package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object DdMotion {
    const val PRESS = 140
    const val FAST = 200
    const val BASE = 260
    const val ENTER = 460
    const val PAGE = 620
    const val DRAWER = 300
    const val SHEET = 320
    const val STAGGER = 40

    const val NAV = 300
    const val SCREEN = 320

    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    fun <T> spec(duration: Int = BASE, delayMillis: Int = 0, easing: Easing = EaseOut): TweenSpec<T> =
        tween(durationMillis = duration, delayMillis = delayMillis, easing = easing)
}
