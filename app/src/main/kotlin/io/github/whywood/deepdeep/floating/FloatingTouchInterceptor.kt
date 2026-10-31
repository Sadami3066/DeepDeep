package io.github.whywood.deepdeep.floating

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

internal class FloatingTouchInterceptor(context: Context) : FrameLayout(context) {
    var onTouch: ((MotionEvent) -> Boolean)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (onTouch?.invoke(event) == true) return true
        return super.dispatchTouchEvent(event)
    }
}
