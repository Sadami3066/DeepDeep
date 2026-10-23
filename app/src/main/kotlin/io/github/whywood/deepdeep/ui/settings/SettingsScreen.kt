package io.github.whywood.deepdeep.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.data.UiToggle
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenu
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.shell.LinuxInstallState

private const val INSTRUMENT_SANS_URL = "https://github.com/Instrument/instrument-sans"
private const val NEWSREADER_URL = "https://github.com/productiontype/Newsreader"
private const val LUCIDE_URL = "https://github.com/lucide-icons/lucide"

@Composable
fun SettingsScreen(
    userName: String,
    providerName: String?,
    modelName: String?,
    configured: Boolean,
    themeMode: ThemeMode,
    preferences: UiPreferences,
    linuxState: LinuxInstallState,
    versionName: String,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToggle: (UiToggle, Boolean) -> Unit,
    onOpenProviders: () -> Unit,
    onOpenRuntime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val languagePreferences = remember(context) { LanguagePreferences(context) }
    var showLicenses by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(languagePreferences.read()) }
    val activeModel = listOfNotNull(
        providerName?.takeIf { it.isNotBlank() },
        modelName?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { stringResource(R.string.settings_account_no_model) }
    val providerLabel = providerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_status_unconfigured)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
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
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(16.dp))
            AccountCard(
                userName = userName,
                activeModel = activeModel,
                configured = configured,
            )

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                PlaceholderRow(DdIcons.User, R.string.settings_profile)
                DdHairline()
                PlaceholderRow(DdIcons.FileText, R.string.settings_billing)
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                PlaceholderRow(DdIcons.Sparkles, R.string.settings_features)
                DdHairline()
                PlaceholderRow(DdIcons.Plug, R.string.settings_connectors)
                DdHairline()
                PlaceholderRow(DdIcons.Shield, R.string.settings_permissions)
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Globe,
                    title = stringResource(R.string.settings_provider),
                    value = providerLabel,
                    onClick = onOpenProviders,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_provider"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Terminal,
                    title = stringResource(R.string.settings_environment),
                    value = linuxStatusLabel(linuxState),
                    onClick = onOpenRuntime,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_environment"),
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                ThemeControl(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )
                DdHairline()
                PlaceholderRow(DdIcons.Font, R.string.settings_font)
                DdHairline()
                LanguageRow(
                    language = language,
                    onLanguageChange = { selected ->
                        if (selected != language) {
                            language = selected
                            languagePreferences.save(selected)
                            context.findActivity()?.recreate()
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SwitchRow(
                    icon = DdIcons.Smartphone,
                    title = R.string.settings_haptics,
                    checked = preferences.hapticsEnabled,
                    onCheckedChange = { onToggle(UiToggle.HAPTICS, it) },
                )
                DdHairline()
                SwitchRow(
                    icon = DdIcons.MessagesSquare,
                    title = R.string.settings_send_hides_keyboard,
                    checked = preferences.sendHidesKeyboard,
                    onCheckedChange = { onToggle(UiToggle.SEND_HIDES_KEYBOARD, it) },
                )
                DdHairline()
                SwitchRow(
                    icon = DdIcons.Volume2,
                    title = R.string.settings_follow_streaming,
                    checked = preferences.followStreaming,
                    onCheckedChange = { onToggle(UiToggle.FOLLOW_STREAMING, it) },
                )
                DdHairline()
                SwitchRow(
                    icon = DdIcons.Brain,
                    title = R.string.settings_show_reasoning,
                    checked = preferences.showReasoning,
                    onCheckedChange = { onToggle(UiToggle.SHOW_REASONING, it) },
                )
                DdHairline()
                SwitchRow(
                    icon = DdIcons.ChevronDown,
                    title = R.string.settings_expand_process,
                    checked = preferences.expandProcessByDefault,
                    onCheckedChange = { onToggle(UiToggle.EXPAND_PROCESS, it) },
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                PlaceholderRow(DdIcons.Volume, R.string.settings_notifications)
                DdHairline()
                PlaceholderRow(DdIcons.Clock, R.string.settings_focus)
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                PlaceholderRow(DdIcons.Shield, R.string.settings_privacy)
                DdHairline()
                PlaceholderRow(DdIcons.Share, R.string.settings_sharing)
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Info,
                    title = stringResource(R.string.settings_version),
                    value = versionName,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.File,
                    title = stringResource(R.string.settings_license),
                    onClick = { showLicenses = true },
                    showChevron = true,
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                PlaceholderRow(DdIcons.ArrowLeft, R.string.settings_sign_out)
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showLicenses) {
        LicenseSheet(onClose = { showLicenses = false })
    }
}

@Composable
private fun AccountCard(
    userName: String,
    activeModel: String,
    configured: Boolean,
) {
    val colors = deepDeepColors()
    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InitialBadge(userName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = userName,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                )
                Text(
                    text = activeModel,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(
                text = stringResource(
                    if (configured) R.string.settings_status_configured else R.string.settings_status_unconfigured,
                ),
                active = configured,
            )
        }
    }
}

@Composable
private fun ThemeControl(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val colors = deepDeepColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdIcon(icon = DdIcons.Moon, tint = colors.inkSecondary, size = 20.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.settings_theme),
                modifier = Modifier.weight(1f),
                style = DdType.body,
                color = colors.ink,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        DdSegmentedControl(
            options = ThemeMode.entries,
            selected = themeMode,
            onSelect = onThemeModeChange,
            modifier = Modifier.fillMaxWidth().testTag("settings_theme"),
            label = { themeLabel(it) },
        )
    }
}

@Composable
private fun SwitchRow(
    icon: DdIcon,
    @StringRes title: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val stateDescription = stringResource(
        if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
    )
    DdListRow(
        icon = icon,
        title = stringResource(title),
        onClick = { onCheckedChange(!checked) },
        trailing = {
            DdSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                stateDescription = stateDescription,
            )
        },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun LanguageRow(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    val colors = deepDeepColors()
    var expanded by remember { mutableStateOf(false) }
    DdPressable(
        onClick = { expanded = true },
        modifier = Modifier.fillMaxWidth().testTag("settings_language"),
        shape = RoundedCornerShape(20.dp),
        contentDescription = stringResource(R.string.settings_language),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(icon = DdIcons.Globe, tint = colors.inkSecondary, size = 20.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                text = stringResource(R.string.settings_language),
                modifier = Modifier.weight(1f),
                style = DdType.body,
                color = colors.ink,
                maxLines = 1,
            )
            Box {
                Text(
                    text = languageLabel(language),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
                DdMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    menuWidth = 184.dp,
                ) {
                    AppLanguage.entries.forEach { option ->
                        DdMenuItem(
                            text = languageLabel(option),
                            onClick = {
                                onLanguageChange(option)
                                expanded = false
                            },
                            selected = option == language,
                            trailing = {
                                if (option == language) {
                                    DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(6.dp))
            DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.CHINESE -> R.string.settings_language_chinese
        AppLanguage.ENGLISH -> R.string.settings_language_english
    },
)

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun LicenseSheet(onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_license_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            LicenseRow(
                title = stringResource(R.string.settings_license_sans),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(INSTRUMENT_SANS_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_serif),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(NEWSREADER_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_icons),
                subtitle = stringResource(R.string.settings_license_isc),
                onClick = { uriHandler.openUri(LUCIDE_URL) },
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LicenseRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = DdType.body, color = colors.ink)
                Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
            }
            DdIcon(icon = DdIcons.External, tint = colors.inkTertiary, size = 16.dp)
        }
    }
}

@Composable
private fun linuxStatusLabel(state: LinuxInstallState): String = when (state) {
    LinuxInstallState.NotInstalled -> stringResource(R.string.settings_linux_status_not_installed)
    LinuxInstallState.Installing -> stringResource(R.string.settings_linux_status_installing)
    is LinuxInstallState.Installed -> stringResource(R.string.settings_linux_status_installed, state.version)
    is LinuxInstallState.Failed -> stringResource(R.string.settings_linux_status_failed, state.message)
}
