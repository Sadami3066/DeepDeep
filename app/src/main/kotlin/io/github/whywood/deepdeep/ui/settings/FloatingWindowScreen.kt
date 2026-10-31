package io.github.whywood.deepdeep.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.FloatingWindowPreferences
import io.github.whywood.deepdeep.data.FloatingWindowSettings
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.coroutines.launch

@Composable
fun FloatingWindowScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { FloatingWindowPreferences(context) }
    val settings by preferences.settings.collectAsStateWithLifecycle(
        initialValue = FloatingWindowSettings(),
    )
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        onPauseOrDispose {}
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("floating_settings"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.floating_settings_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(20.dp))
            DdGroupCard {
                FloatingSwitchRow(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        scope.launch { preferences.setEnabled(enabled) }
                        if (enabled && !canDrawOverlays) openOverlayPermission(context)
                    },
                )
                if (settings.enabled) {
                    DdHairline()
                    DdListRow(
                        icon = DdIcons.Shield,
                        title = stringResource(R.string.floating_settings_permission),
                        value = stringResource(
                            if (canDrawOverlays) {
                                R.string.floating_settings_permission_granted
                            } else {
                                R.string.floating_settings_permission_missing
                            },
                        ),
                        onClick = { openOverlayPermission(context) },
                        showChevron = true,
                        modifier = Modifier.testTag("floating_permission"),
                    )
                }
            }
            if (settings.enabled && !canDrawOverlays) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.floating_settings_permission_hint),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.floating_settings_description),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FloatingSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val stateDescription = stringResource(
        if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
    )
    DdListRow(
        icon = DdIcons.Monitor,
        title = stringResource(R.string.floating_settings_enable),
        subtitle = stringResource(R.string.floating_settings_enable_hint),
        onClick = { onCheckedChange(!checked) },
        trailing = {
            DdSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                stateDescription = stateDescription,
            )
        },
        modifier = Modifier.testTag("floating_enable"),
    )
}

private fun openOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
}
