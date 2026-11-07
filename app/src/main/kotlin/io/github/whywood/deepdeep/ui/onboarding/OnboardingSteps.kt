package io.github.whywood.deepdeep.ui.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AssistantRole
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.TonePreset
import io.github.whywood.deepdeep.design.DdChip
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.ui.common.ProviderIcon
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TonePresets = listOf(TonePreset.WARM, TonePreset.CUTE, TonePreset.CALM)

@Composable
internal fun WelcomeStep(modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    val transition = rememberInfiniteTransition(label = "onboarding-welcome-float")
    val offset by transition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = DdMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onboarding-welcome-offset",
    )
    Box(
        modifier.fillMaxSize().padding(horizontal = 32.dp).testTag("onboarding_step_welcome"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.mascot_welcome),
                contentDescription = null,
                modifier = Modifier
                    .size(208.dp)
                    .graphicsLayer { translationY = offset.dp.toPx() },
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(24.dp))
            DdWordmark(
                color = colors.ink,
                dotColor = colors.accent,
                style = DdType.display,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_tagline),
                style = DdType.body,
                color = colors.inkSecondary,
            )
        }
    }
}

@Composable
internal fun AccountStep(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    tone: TonePreset,
    onToneChange: (TonePreset) -> Unit,
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    avatarVersion: Int,
    loadAvatar: () -> Bitmap?,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val avatar by produceState<ImageBitmap?>(initialValue = null, avatarVersion) {
        value = withContext(Dispatchers.IO) { loadAvatar()?.asImageBitmap() }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("onboarding_step_account"),
    ) {
        Spacer(Modifier.height(8.dp))
        StepHeading(
            title = stringResource(R.string.onboarding_account_title),
            subtitle = stringResource(R.string.onboarding_account_subtitle),
        )
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                DdPressable(
                    onClick = onPickAvatar,
                    modifier = Modifier.size(104.dp).testTag("onboarding_avatar"),
                    shape = CircleShape,
                    contentDescription = stringResource(R.string.onboarding_avatar_pick),
                ) {
                    val bitmap = avatar
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().clip(CircleShape).background(colors.wash),
                            contentAlignment = Alignment.Center,
                        ) {
                            DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 30.dp)
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.paper)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    DdIcon(icon = DdIcons.Plus, tint = colors.onAccent, size = 15.dp)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        DdSectionLabel(
            text = stringResource(R.string.onboarding_nickname_label),
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        DdTextField(
            value = nickname,
            onValueChange = { onNicknameChange(it.take(PersonalizationStore.MAX_NICKNAME_LENGTH)) },
            placeholder = stringResource(R.string.onboarding_nickname_hint),
            testTag = "onboarding_nickname",
        )
        Spacer(Modifier.height(24.dp))
        DdSectionLabel(
            text = stringResource(R.string.onboarding_tone_label),
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonePresets.forEach { preset ->
                DdChip(
                    text = toneLabel(preset),
                    onClick = { onToneChange(preset) },
                    selected = tone == preset,
                    modifier = Modifier.testTag("onboarding_tone_${preset.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        DdSectionLabel(
            text = stringResource(R.string.onboarding_language_label),
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppLanguage.entries.forEach { option ->
                DdChip(
                    text = languageLabel(option),
                    onClick = { onLanguageChange(option) },
                    selected = language == option,
                    modifier = Modifier.testTag("onboarding_language_${option.tag}"),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ProviderStep(
    provider: ProviderSummary?,
    deepseek: ProviderSummary?,
    providers: List<ProviderSummary>,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    keyState: Int,
    oauthSignedIn: Boolean,
    oauthBusy: Boolean,
    oauthError: String?,
    onPickDeepSeek: () -> Unit,
    onSelectProvider: (ProviderSummary) -> Unit,
    onStartOAuth: () -> Unit,
    onSignOutOAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var sheetOpen by remember { mutableStateOf(false) }
    val deepSeekSelected = provider == null || provider.id == ProviderCatalog.DEFAULT_PROVIDER_ID
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("onboarding_step_provider"),
    ) {
        Spacer(Modifier.height(8.dp))
        StepHeading(
            title = stringResource(R.string.onboarding_provider_title),
            subtitle = stringResource(R.string.onboarding_provider_subtitle),
        )
        Spacer(Modifier.height(24.dp))
        DdGroupCard {
            ProviderOption(
                providerId = ProviderCatalog.DEFAULT_PROVIDER_ID,
                name = deepseek?.name ?: "DeepSeek",
                badge = stringResource(R.string.onboarding_provider_recommended),
                selected = deepSeekSelected,
                onClick = onPickDeepSeek,
                testTag = "onboarding_provider_deepseek",
            )
            DdHairline()
            OtherProviderRow(
                selectedProvider = if (deepSeekSelected) null else provider,
                count = (providers.size - 1).coerceAtLeast(0),
                onClick = { sheetOpen = true },
            )
        }
        Spacer(Modifier.height(24.dp))
        if (provider != null) {
            val showOAuth = provider.hasOAuth
            if (showOAuth) {
                DdSectionLabel(
                    text = stringResource(R.string.onboarding_provider_oauth_label),
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                DdGroupCard {
                    OAuthOptionRow(
                        signedIn = oauthSignedIn,
                        busy = oauthBusy,
                        onLogin = onStartOAuth,
                    )
                    if (oauthSignedIn) {
                        DdHairline()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            DdTextAction(
                                text = stringResource(R.string.onboarding_provider_oauth_sign_out),
                                onClick = onSignOutOAuth,
                                color = colors.inkTertiary,
                            )
                        }
                    }
                    if (oauthError != null) {
                        DdHairline()
                        Text(
                            text = oauthError,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("onboarding_provider_oauth_error"),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (provider.supportsApiKey) {
                DdSectionLabel(
                    text = stringResource(R.string.onboarding_provider_api_key_label),
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                DdTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    placeholder = stringResource(R.string.onboarding_provider_api_key_hint),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    testTag = "onboarding_provider_key",
                )
                when (keyState) {
                    KEY_STATE_SAVING -> {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DdSpinner(size = 14.dp, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.onboarding_provider_key_saving),
                                style = DdType.bodySmall,
                                color = colors.inkSecondary,
                            )
                        }
                    }

                    KEY_STATE_SAVED -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_provider_key_saved),
                            style = DdType.bodySmall,
                            color = colors.accent,
                        )
                    }

                    KEY_STATE_FAILED -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_provider_key_failed),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
            } else if (!showOAuth) {
                Text(
                    text = stringResource(R.string.onboarding_provider_extra_config),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (sheetOpen) {
        ProviderPickerSheet(
            providers = providers,
            selectedId = provider?.id.orEmpty(),
            onSelect = { picked ->
                sheetOpen = false
                onSelectProvider(picked)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
internal fun PermissionsStep(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(notificationsEnabled(context)) }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var assistant by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifications = granted }
    LifecycleResumeEffect(Unit) {
        notifications = notificationsEnabled(context)
        overlay = Settings.canDrawOverlays(context)
        assistant = AssistantRole.isHeld(context)
        onPauseOrDispose {}
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("onboarding_step_permissions"),
    ) {
        Spacer(Modifier.height(8.dp))
        StepHeading(
            title = stringResource(R.string.onboarding_permissions_title),
            subtitle = stringResource(R.string.onboarding_permissions_subtitle),
        )
        Spacer(Modifier.height(24.dp))
        DdGroupCard {
            PermissionRow(
                icon = DdIcons.Message,
                title = stringResource(R.string.onboarding_permission_notification),
                granted = notifications,
                testTag = "onboarding_permission_notification",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !notifications) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Monitor,
                title = stringResource(R.string.onboarding_permission_overlay),
                granted = overlay,
                testTag = "onboarding_permission_overlay",
                onClick = { openOverlaySettings(context) },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Sparkles,
                title = stringResource(R.string.onboarding_permission_assistant),
                granted = assistant,
                testTag = "onboarding_permission_assistant",
                onClick = { openAssistantSettings(context) },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun LinuxStep(
    state: LinuxInstallState,
    supported: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("onboarding_step_linux"),
    ) {
        Spacer(Modifier.height(8.dp))
        StepHeading(
            title = stringResource(R.string.onboarding_linux_title),
            subtitle = stringResource(R.string.onboarding_linux_subtitle),
        )
        Spacer(Modifier.height(24.dp))
        DdGroupCard {
            DdListRow(
                icon = DdIcons.Terminal,
                title = stringResource(R.string.onboarding_linux_status),
                modifier = Modifier.testTag("onboarding_linux_status"),
                trailing = { LinuxStatus(state) },
            )
            if (supported) {
                when (state) {
                    LinuxInstallState.NotInstalled -> {
                        DdHairline()
                        DdListRow(
                            icon = DdIcons.Download,
                            title = stringResource(R.string.onboarding_linux_install),
                            subtitle = stringResource(R.string.onboarding_linux_size_hint),
                            onClick = onInstall,
                            showChevron = true,
                            modifier = Modifier.testTag("onboarding_linux_install"),
                        )
                    }

                    LinuxInstallState.Installing -> {
                        DdHairline()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            IndeterminateBar()
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DdSpinner(size = 14.dp, strokeWidth = 1.5.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_linux_installing),
                                    style = DdType.bodySmall,
                                    color = colors.inkSecondary,
                                )
                            }
                        }
                    }

                    is LinuxInstallState.Installed -> {
                        val sizeBytes = state.sizeBytes
                        if (sizeBytes != null && sizeBytes > 0) {
                            DdHairline()
                            DdListRow(
                                title = stringResource(R.string.onboarding_linux_used),
                                value = android.text.format.Formatter.formatShortFileSize(context, sizeBytes),
                            )
                        }
                    }

                    is LinuxInstallState.Failed -> {
                        DdHairline()
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = stringResource(R.string.onboarding_linux_failed),
                                style = DdType.bodySmall,
                                color = colors.danger,
                            )
                            Spacer(Modifier.height(12.dp))
                            DdTextAction(
                                text = stringResource(R.string.onboarding_linux_install),
                                onClick = onInstall,
                            )
                        }
                    }
                }
            }
        }
        if (!supported) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.onboarding_linux_unsupported),
                style = DdType.bodySmall,
                color = colors.warning,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun PrivilegeStep(
    selected: PrivilegeLevel,
    onSelect: (PrivilegeLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag("onboarding_step_privilege"),
    ) {
        Spacer(Modifier.height(8.dp))
        StepHeading(
            title = stringResource(R.string.onboarding_privilege_title),
            subtitle = stringResource(R.string.onboarding_privilege_subtitle),
        )
        Spacer(Modifier.height(24.dp))
        DdGroupCard {
            PrivilegeRow(
                level = PrivilegeLevel.NORMAL,
                body = stringResource(R.string.onboarding_privilege_normal_body),
                selected = selected == PrivilegeLevel.NORMAL,
                onSelect = onSelect,
                testTag = "onboarding_privilege_normal",
            )
            DdHairline()
            PrivilegeRow(
                level = PrivilegeLevel.ADB,
                body = stringResource(R.string.onboarding_privilege_adb_body),
                selected = selected == PrivilegeLevel.ADB,
                onSelect = onSelect,
                testTag = "onboarding_privilege_adb",
            )
            DdHairline()
            PrivilegeRow(
                level = PrivilegeLevel.ROOT,
                body = stringResource(R.string.onboarding_privilege_root_body),
                selected = selected == PrivilegeLevel.ROOT,
                onSelect = onSelect,
                testTag = "onboarding_privilege_root",
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProviderOption(
    providerId: String,
    name: String,
    badge: String?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(providerId, name, size = 40.dp, iconSize = 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = name, style = DdType.body, color = colors.ink)
                if (badge != null) {
                    Text(text = badge, style = DdType.bodySmall, color = colors.accent)
                }
            }
            SelectionCheck(visible = selected)
        }
    }
}

@Composable
private fun OtherProviderRow(
    selectedProvider: ProviderSummary?,
    count: Int,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val selected = selectedProvider != null
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_provider_other"),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedProvider != null) {
                ProviderIcon(selectedProvider.id, selectedProvider.name, size = 40.dp, iconSize = 24.dp)
            } else {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.wash),
                    contentAlignment = Alignment.Center,
                ) {
                    DdIcon(icon = DdIcons.Apps, tint = colors.inkSecondary, size = 20.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_provider_other),
                    style = DdType.body,
                    color = colors.ink,
                )
                Text(
                    text = selectedProvider?.name
                        ?: stringResource(R.string.onboarding_provider_other_count, count),
                    style = DdType.bodySmall,
                    color = if (selected) colors.accent else colors.inkTertiary,
                )
            }
            SelectionCheck(visible = selected)
            Spacer(Modifier.width(6.dp))
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 16.dp)
        }
    }
}

@Composable
private fun OAuthOptionRow(
    signedIn: Boolean,
    busy: Boolean,
    onLogin: () -> Unit,
) {
    val colors = deepDeepColors()
    DdListRow(
        icon = DdIcons.Shield,
        title = when {
            signedIn -> stringResource(R.string.onboarding_provider_oauth_signed_in)
            busy -> stringResource(R.string.onboarding_provider_oauth_signing_in)
            else -> stringResource(R.string.onboarding_provider_oauth_login)
        },
        onClick = if (signedIn || busy) null else onLogin,
        showChevron = !signedIn && !busy,
        modifier = Modifier.testTag("onboarding_provider_oauth"),
        trailing = when {
            signedIn -> {
                { DdIcon(icon = DdIcons.Check, tint = colors.success, size = 16.dp) }
            }

            busy -> {
                { DdSpinner(size = 14.dp, strokeWidth = 1.5.dp) }
            }

            else -> null
        },
    )
}

@Composable
private fun PermissionRow(
    icon: DdIcon,
    title: String,
    granted: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    DdListRow(
        icon = icon,
        title = title,
        onClick = onClick,
        showChevron = true,
        modifier = Modifier.testTag(testTag),
        trailing = { GrantStatus(granted = granted) },
    )
}

@Composable
private fun LinuxStatus(state: LinuxInstallState) {
    val colors = deepDeepColors()
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(tween(DdMotion.FAST, easing = DdMotion.EaseOut)) togetherWith fadeOut(tween(120))
        },
        label = "onboarding-linux-status",
    ) { current ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (current) {
                LinuxInstallState.NotInstalled -> Text(
                    text = stringResource(R.string.onboarding_linux_not_installed),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )

                LinuxInstallState.Installing -> {
                    DdSpinner(size = 14.dp, strokeWidth = 1.5.dp)
                    Text(
                        text = stringResource(R.string.onboarding_linux_installing),
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                }

                is LinuxInstallState.Installed -> {
                    DdIcon(icon = DdIcons.Check, tint = colors.success, size = 15.dp)
                    Text(
                        text = stringResource(R.string.onboarding_linux_installed),
                        style = DdType.bodySmall,
                        color = colors.success,
                    )
                }

                is LinuxInstallState.Failed -> Text(
                    text = stringResource(R.string.onboarding_linux_failed),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
        }
    }
}

@Composable
private fun PrivilegeRow(
    level: PrivilegeLevel,
    body: String,
    selected: Boolean,
    onSelect: (PrivilegeLevel) -> Unit,
    testTag: String,
) {
    DdListRow(
        title = privilegeLabel(level),
        subtitle = body,
        subtitleMaxLines = 2,
        onClick = { onSelect(level) },
        modifier = Modifier
            .testTag(testTag)
            .semantics { this.selected = selected },
        trailing = { RadioMark(checked = selected) },
    )
}

@Composable
private fun toneLabel(tone: TonePreset): String = stringResource(
    when (tone) {
        TonePreset.WARM -> R.string.onboarding_tone_warm
        TonePreset.CUTE -> R.string.onboarding_tone_cute
        TonePreset.CALM -> R.string.onboarding_tone_calm
        TonePreset.CUSTOM -> R.string.onboarding_tone_warm
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.onboarding_language_system
        AppLanguage.CHINESE -> R.string.onboarding_language_chinese
        AppLanguage.ENGLISH -> R.string.onboarding_language_english
    },
)

@Composable
private fun privilegeLabel(level: PrivilegeLevel): String = stringResource(
    when (level) {
        PrivilegeLevel.NORMAL -> R.string.onboarding_privilege_normal
        PrivilegeLevel.ADB -> R.string.onboarding_privilege_adb
        PrivilegeLevel.ROOT -> R.string.onboarding_privilege_root
    },
)

private fun notificationsEnabled(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    if (!manager.areNotificationsEnabled()) return false
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
}

private fun openAssistantSettings(context: Context) {
    AssistantRole.defaultAppsIntent(context)?.let { intent ->
        runCatching { context.startActivity(intent) }
    }
}
