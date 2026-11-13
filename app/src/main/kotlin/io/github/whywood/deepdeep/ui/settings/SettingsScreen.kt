package io.github.whywood.deepdeep.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.OverlayEntryMode
import io.github.whywood.deepdeep.data.crash.CrashLogStore
import io.github.whywood.deepdeep.design.DdDropdown
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdRowDivider
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMenuItem
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSegmentedControl
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.AvatarBadge
import io.github.whywood.deepdeep.ui.common.rememberRelativeTime
import io.github.whywood.deepdeep.ui.common.shareChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TABLER_ICONS_URL = "https://github.com/tabler/tabler-icons"
private const val PROVIDER_LOGOS_URL = "https://github.com/lobehub/lobe-icons"
private const val MANROPE_URL = "https://fonts.google.com/specimen/Manrope"
private const val LORA_URL = "https://github.com/cyrealtype/Lora-Cyrillic"
private const val OPEN_RUNDE_URL = "https://github.com/lauridskern/open-runde"
private const val FREDOKA_URL = "https://fonts.google.com/specimen/Fredoka"

@Composable
fun SettingsScreen(
    nickname: String,
    avatar: ImageBitmap? = null,
    providerName: String?,
    modelName: String?,
    configured: Boolean,
    themeLabel: String,
    versionName: String,
    fontLabel: String,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenChatSettings: () -> Unit,
    overlayEntryMode: OverlayEntryMode = OverlayEntryMode.VOICE,
    onOpenUsageHabits: () -> Unit = {},
    onOpenScan: () -> Unit = {},
    onOpenProviders: () -> Unit,
    onOpenAccess: () -> Unit = {},
    accessLabel: String? = null,
    onOpenAgentSettings: () -> Unit = {},
    onOpenAndroidSettings: () -> Unit = {},
    onOpenPersonalization: () -> Unit,
    onOpenFonts: () -> Unit,
    onOpenPrivacy: () -> Unit,
    archivedCount: Int = 0,
    onOpenArchived: () -> Unit = {},
    onLanguageChange: () -> Unit = {},
    updateVersion: String? = null,
    onOpenUpdate: () -> Unit = {},
    crashLogs: CrashLogStore? = null,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val languagePreferences = remember(context) { LanguagePreferences(context) }
    var showLicenses by remember { mutableStateOf(false) }
    var showCrashLog by remember { mutableStateOf(false) }
    var crashRevision by remember { mutableStateOf(0) }
    val crashEntry = remember(crashLogs, crashRevision) { crashLogs?.latest() }
    var language by remember { mutableStateOf(languagePreferences.read()) }
    val activeModel = listOfNotNull(
        providerName?.takeIf { it.isNotBlank() },
        modelName?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifBlank { stringResource(R.string.settings_account_no_model) }
    val providerLabel = providerName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_status_unconfigured)

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
        modifier = modifier,
    ) {
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
                avatar = avatar,
                activeModel = activeModel,
                configured = configured,
                onClick = onOpenPersonalization,
            )

            SettingsSectionLabel(stringResource(R.string.settings_group_model))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.Cloud,
                    title = stringResource(R.string.settings_provider),
                    value = providerLabel,
                    onClick = onOpenProviders,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_provider"),
                )
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.Shield,
                    title = stringResource(R.string.settings_access),
                    value = accessLabel,
                    onClick = onOpenAccess,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_access"),
                )
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.Sparkles,
                    title = stringResource(R.string.settings_agent),
                    onClick = onOpenAgentSettings,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_agent"),
                )
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.Phone,
                    title = stringResource(R.string.settings_android),
                    onClick = onOpenAndroidSettings,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_android"),
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_group_appearance))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.Theme,
                    title = stringResource(R.string.settings_theme),
                    value = themeLabel,
                    onClick = onOpenTheme,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_theme"),
                )
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.Font,
                    title = stringResource(R.string.settings_font),
                    value = fontLabel,
                    onClick = onOpenFonts,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_font"),
                )
                DdRowDivider(leadingInset = SettingsDividerInset)
                LanguageRow(
                    language = language,
                    onLanguageChange = { selected ->
                        if (selected != language) {
                            language = selected
                            languagePreferences.save(selected)
                            onLanguageChange()
                            Handler(Looper.getMainLooper()).post {
                                context.findActivity()?.recreate()
                            }
                        }
                    },
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_group_conversation))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.ChatPreferences,
                    title = stringResource(R.string.settings_chat),
                    onClick = onOpenChatSettings,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_chat"),
                )
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.HandClick,
                    title = stringResource(R.string.settings_usage_habits),
                    value = overlayEntryModeLabel(overlayEntryMode),
                    onClick = onOpenUsageHabits,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_usage_habits"),
                )
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.Archive,
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
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.Scan,
                    title = stringResource(R.string.scan_settings_title),
                    onClick = onOpenScan,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_scan"),
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_group_notifications))
            DdGroupCard {
                NotificationRow()
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.Privacy,
                    title = stringResource(R.string.settings_privacy_agreement),
                    onClick = onOpenPrivacy,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_privacy_agreement"),
                )
            }

            SettingsSectionLabel(stringResource(R.string.settings_group_about))
            DdGroupCard {
                DdListRow(
                    icon = DdIcons.Download,
                    title = stringResource(R.string.settings_update),
                    value = if (updateVersion == null) versionName else null,
                    trailing = if (updateVersion != null) {
                        {
                            Text(
                                text = stringResource(R.string.settings_update_available, updateVersion),
                                style = DdType.bodySmall,
                                color = colors.accent,
                                maxLines = 1,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = onOpenUpdate,
                    showChevron = true,
                    modifier = Modifier.testTag("settings_update"),
                )
                if (crashEntry != null) {
                    DdRowDivider(leadingInset = SettingsDividerInset)
                    DdListRow(
                        icon = DdIcons.Info,
                        title = stringResource(R.string.settings_crash_log),
                        value = rememberRelativeTime(crashEntry.recordedAt),
                        onClick = { showCrashLog = true },
                        showChevron = true,
                        modifier = Modifier.testTag("settings_crash_log"),
                    )
                }
                DdRowDivider(leadingInset = SettingsDividerInset)
                DdListRow(
                    icon = DdIcons.License,
                    title = stringResource(R.string.settings_license),
                    onClick = { showLicenses = true },
                    showChevron = true,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showLicenses) {
        LicenseSheet(onClose = { showLicenses = false })
    }

    if (showCrashLog && crashLogs != null && crashEntry != null) {
        CrashLogSheet(
            store = crashLogs,
            entry = crashEntry,
            onClose = { showCrashLog = false },
            onCleared = {
                showCrashLog = false
                crashRevision += 1
            },
        )
    }

}

@Composable
private fun CrashLogSheet(
    store: CrashLogStore,
    entry: CrashLogStore.Entry,
    onClose: () -> Unit,
    onCleared: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val relativeTime = rememberRelativeTime(entry.recordedAt)
    val shareLabel = stringResource(R.string.settings_crash_log_share)
    var text by remember(entry) { mutableStateOf("") }
    LaunchedEffect(entry) {
        text = withContext(Dispatchers.IO) { store.read(entry) }
    }
    DdSheet(onDismiss = onClose) {
        DdSheetHeader(stringResource(R.string.settings_crash_log_title), onClose)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.settings_crash_log_subtitle, relativeTime),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.raised)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(
                    text = text.ifBlank { stringResource(R.string.settings_crash_log_empty) },
                    style = DdType.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                DdTextAction(
                    text = stringResource(R.string.settings_crash_log_copy),
                    enabled = text.isNotBlank(),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("DeepDeep crash", text))
                    },
                )
                DdTextAction(
                    text = shareLabel,
                    enabled = text.isNotBlank(),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(shareChooser(context, send, shareLabel))
                    },
                )
                Spacer(Modifier.weight(1f))
                DdTextAction(
                    text = stringResource(R.string.settings_crash_log_clear),
                    color = colors.danger,
                    onClick = {
                        store.clear()
                        onCleared()
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val SettingsDividerInset = 50.dp

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
        style = DdType.micro,
        color = deepDeepColors().inkTertiary,
    )
}

@Composable
private fun AccountCard(
    userName: String,
    avatar: ImageBitmap?,
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
    DdGroupCard {
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
                AvatarBadge(avatar = avatar, name = userName, size = 44.dp)
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
                title = stringResource(R.string.settings_license_open_runde),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(OPEN_RUNDE_URL) },
            )
            DdHairline()
            LicenseRow(
                title = stringResource(R.string.settings_license_fredoka),
                subtitle = stringResource(R.string.settings_license_ofl),
                onClick = { uriHandler.openUri(FREDOKA_URL) },
            )
            DdHairline()
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
