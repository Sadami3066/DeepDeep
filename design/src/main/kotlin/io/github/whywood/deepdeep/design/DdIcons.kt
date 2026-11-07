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
    val Menu = DdIcon(R.drawable.ic_milo_menu)
    val More = DdIcon(R.drawable.ic_milo_more)
    val Close = DdIcon(R.drawable.ic_milo_close)
    val Plus = DdIcon(R.drawable.ic_milo_add)
    val ArrowUp = DdIcon(R.drawable.ic_milo_send)
    val ArrowLeft = DdIcon(R.drawable.ic_milo_back)
    val ChevronDown = DdIcon(R.drawable.ic_dd_chevron_down)
    val ChevronRight = DdIcon(R.drawable.ic_dd_chevron_right)
    val Mic = DdIcon(R.drawable.ic_milo_microphone)
    val Brain = DdIcon(R.drawable.ic_milo_memory)
    val Paperclip = DdIcon(R.drawable.ic_milo_attachment)
    val Camera = DdIcon(R.drawable.ic_milo_camera)
    val Scan = DdIcon(R.drawable.ic_milo_scan)
    val Image = DdIcon(R.drawable.ic_milo_image)
    val File = DdIcon(R.drawable.ic_milo_license)
    val Globe = DdIcon(R.drawable.ic_milo_language)
    val Settings = DdIcon(R.drawable.ic_milo_settings)
    val Search = DdIcon(R.drawable.ic_milo_search)
    val Message = DdIcon(R.drawable.ic_milo_chat_preferences)
    val Compose = DdIcon(R.drawable.ic_milo_new_chat)
    val Phone = DdIcon(R.drawable.ic_milo_device)
    val Info = DdIcon(R.drawable.ic_milo_info)
    val External = DdIcon(R.drawable.ic_milo_export)
    val Expand = DdIcon(R.drawable.ic_milo_expand)
    val AudioLines = DdIcon(R.drawable.ic_milo_voice_wave)
    val Sparkles = DdIcon(R.drawable.ic_milo_agent)
    val Check = DdIcon(R.drawable.ic_milo_confirm)
    val Sun = DdIcon(R.drawable.ic_milo_today)
    val Moon = DdIcon(R.drawable.ic_milo_appearance)
    val Monitor = DdIcon(R.drawable.ic_milo_device)
    val Trash = DdIcon(R.drawable.ic_milo_delete)
    val Stop = DdIcon(R.drawable.ic_milo_stop)
    val Terminal = DdIcon(R.drawable.ic_milo_terminal)
    val Folder = DdIcon(R.drawable.ic_milo_workspace)
    val FolderOpen = DdIcon(R.drawable.ic_dd_folder_open)
    val Play = DdIcon(R.drawable.ic_milo_play)
    val Clock = DdIcon(R.drawable.ic_milo_timer)
    val Plug = DdIcon(R.drawable.ic_milo_connectors)
    val Puzzle = DdIcon(R.drawable.ic_milo_plugins)
    val Book = DdIcon(R.drawable.ic_milo_skills)
    val Package = DdIcon(R.drawable.ic_milo_plugins)
    val Copy = DdIcon(R.drawable.ic_milo_copy)
    val Quote = DdIcon(R.drawable.ic_dd_quote)
    val SelectAll = DdIcon(R.drawable.ic_dd_select_all)
    val Share = DdIcon(R.drawable.ic_milo_share)
    val Download = DdIcon(R.drawable.ic_milo_download)
    val Eye = DdIcon(R.drawable.ic_milo_privacy)
    val Raw = DdIcon(R.drawable.ic_milo_code)
    val Refresh = DdIcon(R.drawable.ic_milo_retry)
    val Volume = DdIcon(R.drawable.ic_milo_headphones)
    val VolumeX = DdIcon(R.drawable.ic_dd_volume_x)
    val Pin = DdIcon(R.drawable.ic_milo_pin)
    val Font = DdIcon(R.drawable.ic_milo_typography)
    val Shield = DdIcon(R.drawable.ic_milo_permissions)
    val Apps = DdIcon(R.drawable.ic_milo_mini_apps)
    val HandClick = DdIcon(R.drawable.ic_milo_interaction)
    val Cloud = DdIcon(R.drawable.ic_milo_model_provider)
    val Target = DdIcon(R.drawable.ic_milo_goal)
    val DotsVertical = DdIcon(R.drawable.ic_milo_more)
    val GitFork = DdIcon(R.drawable.ic_milo_branch)
}
