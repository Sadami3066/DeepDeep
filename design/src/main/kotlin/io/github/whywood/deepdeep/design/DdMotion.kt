package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object DdMotion {
    const val PRESS = 140
    const val PRESS_IN = 70
    const val PRESS_OUT = 180
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

    val SwitchSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 700f)
    val SegmentedSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 620f)

    fun <T> spec(duration: Int = BASE, delayMillis: Int = 0, easing: Easing = EaseOut): TweenSpec<T> =
        tween(durationMillis = duration, delayMillis = delayMillis, easing = easing)
}
