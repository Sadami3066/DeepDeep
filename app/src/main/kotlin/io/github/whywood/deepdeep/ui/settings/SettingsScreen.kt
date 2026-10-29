package io.github.whywood.deepdeep.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.shell.LinuxInstallState

private const val TABLER_ICONS_URL = "https://github.com/tabler/tabler-icons"
private const val PROVIDER_LOGOS_URL = "https://github.com/lobehub/lobe-icons"
private const val MANROPE_URL = "https://fonts.google.com/specimen/Manrope"
private const val LORA_URL = "https://github.com/cyrealtype/Lora-Cyrillic"
private const val NOTO_SANS_SC_URL = "https://fonts.google.com/specimen/Noto+Sans+SC"

@Composable
fun SettingsScreen(
    nickname: String,
    providerName: String?,
    modelName: String?,
    configured: Boolean,
    themeMode: ThemeMode,
    linuxState: LinuxInstallState,
    versionName: String,
    fontLabel: String,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenChatSettings: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenRuntime: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenFonts: () -> Unit,
    onOpenPrivacy: () -> Unit,
    archivedCount: Int = 0,
    onOpenArchived: () -> Unit = {},
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
                userName = nickname,
                activeModel = activeModel,
                configured = configured,
                onClick = onOpenPersonalization,
            )

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.settings_personalization),
                    onClick = onOpenPersonalization,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_personalization"),
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Folder,
                    title = stringResource(R.string.settings_archived),
                    value = if (archivedCount > 0) {
                        stringResource(R.string.settings_archived_count, archivedCount)
                    } else {
                        null
                    },
                    onClick = onOpenArchived,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_archived"),
                )
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
                DdListRow(
                    icon = DdIcons.Font,
                    title = stringResource(R.string.settings_font),
                    value = fontLabel,
                    onClick = onOpenFonts,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_font"),
                )
                DdHairline()
                LanguageRow(
                    language = language,
                    onLanguageChange = { selected ->
                        if (selected != language) {
                            language = selected
                            languagePreferences.save(selected)
                            Handler(Looper.getMainLooper()).post {
                                context.findActivity()?.recreate()
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Message,
                    title = stringResource(R.string.settings_chat),
                    onClick = onOpenChatSettings,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_chat"),
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                NotificationRow()
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                DdListRow(
                    icon = DdIcons.Shield,
                    title = stringResource(R.string.settings_privacy),
                    onClick = onOpenPrivacy,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_privacy"),
                )
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
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val noModel = stringResource(R.string.settings_account_no_model)
    val modelLabel = if (activeModel.isBlank() || activeModel == noModel) {
        noModel
    } else {
        "${stringResource(R.string.settings_new_chat_model)} · $activeModel"
    }
    SettingsGroup {
        DdPressable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            contentDescription = userName,
        ) {
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
                        text = modelLabel,
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
}

@Composable
private fun NotificationRow() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleResumeEffect(Unit) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose {}
    }
    DdListRow(
        icon = DdIcons.Volume,
        title = stringResource(R.string.settings_notifications),
        value = stringResource(
            if (enabled) R.string.settings_notifications_on else R.string.settings_notifications_off,
        ),
        onClick = {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            context.startActivity(intent)
        },
        showChevron = true,
        modifier = Modifier.testTag("settings_notifications"),
    )
}

@Composable
private fun ThemeControl(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val colors = deepDeepColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DdIcon(
                icon = when (themeMode) {
                    ThemeMode.SYSTEM -> DdIcons.Monitor
                    ThemeMode.LIGHT -> DdIcons.Sun
                    ThemeMode.DARK -> DdIcons.Moon
                },
                tint = colors.inkSecondary,
                size = 20.dp,
            )
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
    var pending by remember { mutableStateOf<AppLanguage?>(null) }
    LaunchedEffect(pending) {
        val selected = pending ?: return@LaunchedEffect
        withFrameNanos { }
        onLanguageChange(selected)
        pending = null
    }
    DdDropdown(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        anchor = {
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
                    Text(
                        text = languageLabel(language),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    DdIcon(icon = DdIcons.ChevronDown, tint = colors.inkTertiary, size = 16.dp)
                }
            }
        },
    ) {
        AppLanguage.entries.forEach { option ->
            DdMenuItem(
                text = languageLabel(option),
                onClick = {
                    expanded = false
                    if (option != language) pending = option
                },
                selected = option == language,
                trailing = if (option == language) {
                    {
                        DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 18.dp)
                    }
                } else {
                    null
                },
            )
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
                title = stringResource(R.string.settings_license_manrope),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(MANROPE_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_lora),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(LORA_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_cjk),
                subtitle = stringResource(R.string.settings_license_cjk_body),
                onClick = { uriHandler.openUri(NOTO_SANS_SC_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_icons),
                subtitle = stringResource(R.string.settings_license_mit),
                onClick = { uriHandler.openUri(TABLER_ICONS_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_provider_icons),
                subtitle = stringResource(R.string.settings_license_provider_icons_body),
                onClick = { uriHandler.openUri(PROVIDER_LOGOS_URL) },
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
