package io.github.whywood.deepdeep.design

import androidx.compose.animation.core.CubicBezierEasing

object DdMotion {
    const val PRESS = 140
    const val FAST = 200
    const val BASE = 260
    const val ENTER = 460
    const val PAGE = 620
    const val DRAWER = 300
    const val SHEET = 320
    const val STAGGER = 40

    val EaseOut = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}
