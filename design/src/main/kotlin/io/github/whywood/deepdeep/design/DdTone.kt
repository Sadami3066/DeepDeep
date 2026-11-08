package io.github.whywood.deepdeep.design

import androidx.compose.ui.graphics.Color

/**
 * 语义色调，供展示型组件（DdBadge、DdNotice、DdInlineMessage）复用。
 */
enum class DdTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Danger,
}

internal fun DdTone.tint(colors: DeepDeepColors): Color = when (this) {
    DdTone.Neutral -> colors.inkSecondary
    DdTone.Accent -> colors.accent
    DdTone.Success -> colors.success
    DdTone.Warning -> colors.warning
    DdTone.Danger -> colors.danger
}
