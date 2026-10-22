package io.github.whywood.deepdeep.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
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
    linuxState: LinuxInstallState,
    linuxSupported: Boolean,
    linuxEnabled: Boolean,
    versionName: String,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenProviders: () -> Unit,
    onLinuxInstall: () -> Unit,
    onLinuxReset: () -> Unit,
    onLinuxEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var placeholder by remember { mutableStateOf<String?>(null) }
    var linuxExpanded by rememberSaveable { mutableStateOf(false) }
    val themeLabel = stringResource(
        when (themeMode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )
    val activeModel = listOfNotNull(
        providerName?.takeIf { it.isNotBlank() },
        modelName?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { stringResource(R.string.settings_account_no_model) }
    val providerLabel = providerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_status_unconfigured)
    val chevronRotation by animateFloatAsState(
        targetValue = if (linuxExpanded) 180f else 0f,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "linux-chevron",
    )

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
            Spacer(Modifier.height(22.dp))
            AccountCard(
                userName = userName,
                activeModel = activeModel,
                configured = configured,
            )

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_account)) {
                PlaceholderRow(
                    icon = DdIcons.Settings,
                    title = R.string.settings_profile,
                    subtitle = R.string.settings_profile_subtitle,
                ) { placeholder = it }
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.File,
                    title = R.string.settings_billing,
                    subtitle = R.string.settings_billing_subtitle,
                ) { placeholder = it }
            }

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_features)) {
                PlaceholderRow(
                    icon = DdIcons.Sparkles,
                    title = R.string.settings_features,
                    subtitle = R.string.settings_features_subtitle,
                ) { placeholder = it }
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.Plug,
                    title = R.string.settings_connectors,
                    subtitle = R.string.settings_connectors_subtitle,
                ) { placeholder = it }
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.Shield,
                    title = R.string.settings_permissions,
                    subtitle = R.string.settings_permissions_subtitle,
                ) { placeholder = it }
            }

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_model)) {
                DdListRow(
                    icon = DdIcons.Globe,
                    title = stringResource(R.string.settings_provider),
                    subtitle = stringResource(R.string.settings_provider_subtitle),
                    value = providerLabel,
                    onClick = onOpenProviders,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_provider"),
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.Terminal,
                    title = stringResource(R.string.settings_environment),
                    subtitle = linuxStatusLabel(linuxState),
                    onClick = { linuxExpanded = !linuxExpanded },
                    trailing = {
                        DdIcon(
                            icon = DdIcons.ChevronDown,
                            tint = colors.inkTertiary,
                            modifier = Modifier.rotate(chevronRotation),
                            size = 16.dp,
                        )
                    },
                    modifier = Modifier.testTag("settings_environment"),
                )
                AnimatedVisibility(visible = linuxExpanded) {
                    Column(Modifier.padding(top = 4.dp, bottom = 12.dp)) {
                        LinuxEnvironmentSection(
                            state = linuxState,
                            supported = linuxSupported,
                            enabled = linuxEnabled,
                            onInstall = onLinuxInstall,
                            onReset = onLinuxReset,
                            onEnabledChange = onLinuxEnabledChange,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_appearance)) {
                DdListRow(
                    icon = DdIcons.Moon,
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(R.string.settings_theme_subtitle),
                    value = themeLabel,
                    onClick = { showThemeSheet = true },
                    showChevron = true,
                    modifier = Modifier.testTag("settings_theme"),
                )
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.Font,
                    title = R.string.settings_font,
                    subtitle = R.string.settings_font_subtitle,
                ) { placeholder = it }
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.Globe,
                    title = R.string.settings_language,
                    subtitle = R.string.settings_language_subtitle,
                ) { placeholder = it }
            }

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_app)) {
                PlaceholderRow(
                    icon = DdIcons.Phone,
                    title = R.string.settings_haptics,
                    subtitle = R.string.settings_haptics_subtitle,
                ) { placeholder = it }
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.Volume,
                    title = R.string.settings_notifications,
                    subtitle = R.string.settings_notifications_subtitle,
                ) { placeholder = it }
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.Clock,
                    title = R.string.settings_focus,
                    subtitle = R.string.settings_focus_subtitle,
                ) { placeholder = it }
            }

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_privacy)) {
                PlaceholderRow(
                    icon = DdIcons.Shield,
                    title = R.string.settings_privacy,
                    subtitle = R.string.settings_privacy_subtitle,
                ) { placeholder = it }
                DdHairline()
                PlaceholderRow(
                    icon = DdIcons.Share,
                    title = R.string.settings_sharing,
                    subtitle = R.string.settings_sharing_subtitle,
                ) { placeholder = it }
            }

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = stringResource(R.string.settings_group_about)) {
                DdListRow(
                    icon = DdIcons.Info,
                    title = stringResource(R.string.settings_version),
                    value = versionName,
                )
                DdHairline()
                DdListRow(
                    icon = DdIcons.File,
                    title = stringResource(R.string.settings_license),
                    subtitle = stringResource(R.string.settings_license_subtitle),
                    onClick = { showLicenses = true },
                    showChevron = true,
                )
            }

            Spacer(Modifier.height(28.dp))
            SettingsGroup(label = null) {
                PlaceholderRow(
                    icon = DdIcons.ArrowLeft,
                    title = R.string.settings_sign_out,
                    subtitle = R.string.settings_sign_out_subtitle,
                ) { placeholder = it }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showThemeSheet) {
        ThemeModeSheet(
            current = themeMode,
            onSelect = {
                onThemeModeChange(it)
                showThemeSheet = false
            },
            onClose = { showThemeSheet = false },
        )
    }

    if (showLicenses) {
        LicenseSheet(onClose = { showLicenses = false })
    }

    placeholder?.let { title ->
        PlaceholderSheet(title = title, onClose = { placeholder = null })
    }
}

@Composable
private fun AccountCard(
    userName: String,
    activeModel: String,
    configured: Boolean,
) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialBadge(userName, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = userName,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.settings_account_local),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
            StatusPill(
                text = stringResource(
                    if (configured) R.string.settings_status_configured else R.string.settings_status_unconfigured,
                ),
                active = configured,
            )
        }
        Spacer(Modifier.height(16.dp))
        DdHairline()
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.settings_account_current_model),
            style = DdType.micro,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = activeModel,
            style = DdType.body,
            color = colors.ink,
        )
    }
}

@Composable
private fun ThemeModeSheet(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onClose: () -> Unit,
) {
    val colors = deepDeepColors()
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_theme_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))
            ThemeMode.entries.forEachIndexed { index, mode ->
                DdListRow(
                    title = stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        },
                    ),
                    onClick = { onSelect(mode) },
                    trailing = {
                        if (mode == current) {
                            DdIcon(DdIcons.Check, tint = colors.accent, size = 20.dp)
                        }
                    },
                )
                if (index != ThemeMode.entries.lastIndex) DdHairline()
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LicenseSheet(onClose: () -> Unit) {
    val colors = deepDeepColors()
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
            Spacer(Modifier.height(24.dp))
            DdPrimaryButton(
                text = stringResource(R.string.settings_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun LicenseRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = DdType.body, color = colors.ink)
            Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
        }
        DdIcon(DdIcons.External, tint = colors.inkTertiary, size = 16.dp)
    }
}

@Composable
private fun linuxStatusLabel(state: LinuxInstallState): String = when (state) {
    LinuxInstallState.NotInstalled -> stringResource(R.string.settings_linux_status_not_installed)
    LinuxInstallState.Installing -> stringResource(R.string.settings_linux_status_installing)
    is LinuxInstallState.Installed -> stringResource(R.string.settings_linux_status_installed, state.version)
    is LinuxInstallState.Failed -> stringResource(R.string.settings_linux_status_failed, state.message)
}
