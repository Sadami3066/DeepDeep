package io.github.whywood.deepdeep.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class DdIcon(@param:DrawableRes val res: Int)

@Composable
fun DdIcon(
    icon: DdIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(icon.res),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

object DdIcons {
    val Menu = DdIcon(R.drawable.ic_dd_menu)
    val More = DdIcon(R.drawable.ic_dd_more)
    val Close = DdIcon(R.drawable.ic_dd_close)
    val Plus = DdIcon(R.drawable.ic_dd_plus)
    val ArrowUp = DdIcon(R.drawable.ic_dd_arrow_up)
    val ArrowLeft = DdIcon(R.drawable.ic_dd_arrow_left)
    val ChevronDown = DdIcon(R.drawable.ic_dd_chevron_down)
    val ChevronRight = DdIcon(R.drawable.ic_dd_chevron_right)
    val Mic = DdIcon(R.drawable.ic_dd_mic)
    val Brain = DdIcon(R.drawable.ic_dd_brain)
    val Paperclip = DdIcon(R.drawable.ic_dd_paperclip)
    val Camera = DdIcon(R.drawable.ic_dd_camera)
    val Scan = DdIcon(R.drawable.ic_dd_scan)
    val Image = DdIcon(R.drawable.ic_dd_image)
    val File = DdIcon(R.drawable.ic_dd_file)
    val Globe = DdIcon(R.drawable.ic_dd_globe)
    val Settings = DdIcon(R.drawable.ic_dd_settings)
    val Search = DdIcon(R.drawable.ic_dd_search)
    val Message = DdIcon(R.drawable.ic_dd_message)
    val Compose = DdIcon(R.drawable.ic_dd_compose)
    val Phone = DdIcon(R.drawable.ic_dd_phone)
    val Info = DdIcon(R.drawable.ic_dd_info)
    val External = DdIcon(R.drawable.ic_dd_external)
    val AudioLines = DdIcon(R.drawable.ic_dd_audio_lines)
    val Sparkles = DdIcon(R.drawable.ic_dd_sparkles)
    val Check = DdIcon(R.drawable.ic_dd_check)
    val Sun = DdIcon(R.drawable.ic_dd_sun)
    val Moon = DdIcon(R.drawable.ic_dd_moon)
    val Monitor = DdIcon(R.drawable.ic_dd_monitor)
    val Trash = DdIcon(R.drawable.ic_dd_trash)
    val Stop = DdIcon(R.drawable.ic_dd_stop)
    val Terminal = DdIcon(R.drawable.ic_dd_terminal)
    val Folder = DdIcon(R.drawable.ic_dd_folder)
    val Play = DdIcon(R.drawable.ic_dd_play)
    val Clock = DdIcon(R.drawable.ic_dd_clock)
    val Plug = DdIcon(R.drawable.ic_dd_plug)
    val Puzzle = DdIcon(R.drawable.ic_dd_puzzle)
    val Copy = DdIcon(R.drawable.ic_dd_copy)
    val Quote = DdIcon(R.drawable.ic_dd_quote)
    val SelectAll = DdIcon(R.drawable.ic_dd_select_all)
    val Share = DdIcon(R.drawable.ic_dd_share)
    val Download = DdIcon(R.drawable.ic_dd_download)
    val Eye = DdIcon(R.drawable.ic_dd_eye)
    val Raw = DdIcon(R.drawable.ic_dd_raw)
    val Refresh = DdIcon(R.drawable.ic_dd_refresh)
    val Volume = DdIcon(R.drawable.ic_dd_volume)
    val VolumeX = DdIcon(R.drawable.ic_dd_volume_x)
    val Pin = DdIcon(R.drawable.ic_dd_pin)
    val Font = DdIcon(R.drawable.ic_dd_font)
    val Shield = DdIcon(R.drawable.ic_dd_shield)
    val Apps = DdIcon(R.drawable.ic_dd_apps)
    val HandClick = DdIcon(R.drawable.ic_dd_hand_click)
    val Cloud = DdIcon(R.drawable.ic_dd_cloud)
    val Target = DdIcon(R.drawable.ic_dd_target)
    val DotsVertical = DdIcon(R.drawable.ic_dd_dots_vertical)
    val GitFork = DdIcon(R.drawable.ic_dd_git_fork)
}
