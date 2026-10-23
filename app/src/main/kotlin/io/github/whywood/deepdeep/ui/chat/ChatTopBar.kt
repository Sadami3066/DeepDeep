package io.github.whywood.deepdeep.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun ChatTopBar(
    onMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 6.dp),
    ) {
        DdIconButton(
            icon = DdIcons.Menu,
            onClick = onMenu,
            contentDescription = stringResource(R.string.assistant_menu),
            modifier = Modifier.align(Alignment.CenterStart),
            tint = colors.inkSecondary,
            iconSize = 21.dp,
        )
        DdWordmark(
            modifier = Modifier.align(Alignment.Center),
            color = colors.ink,
            dotColor = colors.accent,
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                DdIconButton(
                    icon = DdIcons.More,
                    onClick = { menuOpen = true },
                    contentDescription = stringResource(R.string.assistant_more),
                    modifier = Modifier.testTag("assistant_more"),
                    tint = colors.inkSecondary,
                    iconSize = 21.dp,
                )
                DdMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    menuWidth = 148.dp,
                ) {
                    DdMenuItem(
                        text = stringResource(R.string.assistant_settings),
                        icon = DdIcons.Settings,
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            }
        }
    }
}
