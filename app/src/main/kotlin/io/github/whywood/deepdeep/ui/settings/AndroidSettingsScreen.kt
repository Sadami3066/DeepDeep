package io.github.whywood.deepdeep.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AssistantEntry
import io.github.whywood.deepdeep.core.AssistantRole
import io.github.whywood.deepdeep.data.FloatingWindowPreferences
import io.github.whywood.deepdeep.data.FloatingWindowSettings
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.coroutines.launch

@Composable
fun AndroidSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember(context) { FloatingWindowPreferences(context) }
    val floatingSettings by preferences.settings.collectAsStateWithLifecycle(
        initialValue = FloatingWindowSettings(),
    )
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var roleHeld by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    val powerUnavailableMessage = stringResource(R.string.assistant_settings_power_unavailable)
    LifecycleResumeEffect(Unit) {
        canDrawOverlays = Settings.canDrawOverlays(context)
        roleHeld = AssistantRole.isHeld(context)
        onPauseOrDispose {}
    }

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        },
        modifier = modifier.testTag("android_settings"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.settings_android),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Xl))
            SectionLabel(R.string.floating_settings_title)
            Spacer(Modifier.height(DdSpacing.Sm))
            DdGroupCard {
                DdListRow(
                    title = stringResource(R.string.floating_settings_enable),
                    onClick = {
                        val enabled = !floatingSettings.enabled
                        scope.launch { preferences.setEnabled(enabled) }
                        if (enabled && !canDrawOverlays) openOverlayPermission(context)
                    },
                    trailing = {
                        DdSwitch(
                            checked = floatingSettings.enabled,
                            onCheckedChange = { enabled ->
                                scope.launch { preferences.setEnabled(enabled) }
                                if (enabled && !canDrawOverlays) openOverlayPermission(context)
                            },
                            stateDescription = stringResource(
                                if (floatingSettings.enabled) R.string.settings_switch_on
                                else R.string.settings_switch_off,
                            ),
                        )
                    },
                    modifier = Modifier.testTag("floating_enable"),
                )
                if (floatingSettings.enabled) {
                    DdHairline()
                    DdListRow(
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
            Spacer(Modifier.height(DdSpacing.Xxl))
            SectionLabel(R.string.assistant_settings_title)
            Spacer(Modifier.height(DdSpacing.Sm))
            DdGroupCard {
                DdListRow(
                    title = stringResource(R.string.assistant_settings_role),
                    value = stringResource(
                        if (roleHeld) R.string.assistant_settings_role_held
                        else R.string.assistant_settings_role_missing,
                    ),
                    onClick = {
                        AssistantRole.defaultAppsIntent(context)?.let { intent ->
                            runCatching {
                                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("assistant_role"),
                )
                DdHairline()
                DdListRow(
                    title = stringResource(R.string.assistant_settings_power),
                    onClick = {
                        val intent = AssistantEntry.powerSettingsIntent(context)
                        if (intent == null) {
                            Toast.makeText(context, powerUnavailableMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            runCatching { context.startActivity(intent) }
                        }
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("assistant_power_settings"),
                )
            }
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }
}

@Composable
private fun SectionLabel(titleRes: Int) {
    val colors = deepDeepColors()
    Text(
        text = stringResource(titleRes),
        style = DdType.bodySmall,
        color = colors.inkTertiary,
        modifier = Modifier.padding(horizontal = DdSpacing.Xs),
    )
}

private fun openOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
}
