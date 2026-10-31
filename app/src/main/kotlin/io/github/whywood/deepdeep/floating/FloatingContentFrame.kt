package io.github.whywood.deepdeep.floating

data class FloatingContentFrame(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

data class FloatingFrameInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val isEmpty: Boolean
        get() = left == 0 && top == 0 && right == 0 && bottom == 0
}

fun floatingContentFrame(
    displayWidth: Int,
    displayHeight: Int,
    insets: FloatingFrameInsets,
): FloatingContentFrame = FloatingContentFrame(
    left = insets.left,
    top = insets.top,
    width = (displayWidth - insets.left - insets.right).coerceAtLeast(1),
    height = (displayHeight - insets.top - insets.bottom).coerceAtLeast(1),
)

fun floatingFullContentFrame(
    displayWidth: Int,
    displayHeight: Int,
): FloatingContentFrame = FloatingContentFrame(
    left = 0,
    top = 0,
    width = displayWidth.coerceAtLeast(1),
    height = displayHeight.coerceAtLeast(1),
)
