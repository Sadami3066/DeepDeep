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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AssistantRole
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.PersonalizationStore
import io.github.whywood.deepdeep.data.TonePreset
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.ui.common.rememberAvatarBitmap
import io.github.whywood.deepdeep.ui.common.ProviderIcon
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TonePresets = listOf(TonePreset.WARM, TonePreset.CUTE, TonePreset.CALM)

@Composable
internal fun WelcomeStep(modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    val transition = rememberInfiniteTransition(label = "onboarding-welcome-float")
    val floatOffset by transition.animateFloat(
        initialValue = 5f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = DdMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onboarding-welcome-offset",
    )
    Column(
        modifier.fillMaxSize().padding(horizontal = DdSpacing.Xxl).testTag("onboarding_step_welcome"),
    ) {
        Spacer(Modifier.height(DdSpacing.Xs))
        DdWordmark(color = colors.ink, dotColor = colors.accent, style = DdType.wordmark)
        Spacer(Modifier.height(26.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = DdType.pageTitle,
            color = colors.ink,
        )
        Spacer(Modifier.height(DdSpacing.Md))
        Text(
            text = stringResource(R.string.onboarding_welcome_lead),
            style = DdType.bodySmall,
            color = colors.inkSecondary,
        )
        Spacer(Modifier.height(DdSpacing.Xl))
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val artSize = minOf(maxWidth, maxHeight, 300.dp)
            Canvas(Modifier.size(artSize)) {
                val radius = size.minDimension / 2f
                drawCircle(
                    color = colors.accent.copy(alpha = DdAlpha.IndicatorDark),
                    radius = radius * 0.94f,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    color = colors.accent.copy(alpha = 0.09f),
                    radius = radius * 0.78f,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            val mascotHeight = minOf(maxHeight, 264.dp, maxWidth * 356f / 342f)
            val mascotWidth = mascotHeight * 342f / 356f
            Image(
                painter = painterResource(R.drawable.mascot_wave),
                contentDescription = null,
                modifier = Modifier
                    .size(mascotWidth, mascotHeight)
                    .graphicsLayer { translationY = floatOffset.dp.toPx() },
                contentScale = ContentScale.Fit,
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
    var languageSheetOpen by remember { mutableStateOf(false) }
    val avatar by rememberAvatarBitmap("onboarding:$avatarVersion") { loadAvatar() }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.Xxl)
            .testTag("onboarding_step_account"),
    ) {
        Spacer(Modifier.height(DdSpacing.Sm))
        StepHeading(
            title = stringResource(R.string.onboarding_account_title),
            subtitle = stringResource(R.string.onboarding_account_subtitle),
        )
        Spacer(Modifier.height(28.dp))
        FieldLabel(text = stringResource(R.string.onboarding_nickname_label))
        Spacer(Modifier.height(DdSpacing.Xxs))
        UnderlineField(
            value = nickname,
            onValueChange = { onNicknameChange(it.take(PersonalizationStore.MAX_NICKNAME_LENGTH)) },
            placeholder = stringResource(R.string.onboarding_nickname_hint),
            testTag = "onboarding_nickname",
            trailing = { AvatarButton(avatar = avatar, onPick = onPickAvatar) },
        )
        Spacer(Modifier.height(28.dp))
        FieldLabel(text = stringResource(R.string.onboarding_tone_label))
        Spacer(Modifier.height(10.dp))
        SegmentedControl(
            options = TonePresets.map { toneLabel(it) },
            selectedIndex = TonePresets.indexOf(tone),
            onSelect = { onToneChange(TonePresets[it]) },
            testTags = TonePresets.map { "onboarding_tone_${it.name.lowercase(Locale.ROOT)}" },
        )
        Spacer(Modifier.height(28.dp))
        FieldLabel(text = stringResource(R.string.onboarding_language_label))
        Spacer(Modifier.height(DdSpacing.Xxs))
        SettingRow(
            text = languageLabel(language),
            onClick = { languageSheetOpen = true },
            testTag = "onboarding_language_row",
        )
        Spacer(Modifier.height(DdSpacing.Xl))
        Text(
            text = stringResource(R.string.onboarding_account_note),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.height(DdSpacing.Xxl))
    }
    if (languageSheetOpen) {
        DdSheet(onDismiss = { languageSheetOpen = false }) {
            DdSheetHeader(
                title = stringResource(R.string.onboarding_language_label),
                onClose = { languageSheetOpen = false },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Md).padding(bottom = DdSpacing.Xxl)) {
                AppLanguage.entries.forEachIndexed { index, option ->
                    if (index > 0) DdHairline(Modifier.padding(horizontal = DdSpacing.Sm))
                    DdListRow(
                        title = languageLabel(option),
                        onClick = {
                            languageSheetOpen = false
                            onLanguageChange(option)
                        },
                        showChevron = false,
                        modifier = Modifier.testTag("onboarding_language_${option.tag}"),
                        trailing = {
                            if (language == option) {
                                DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 16.dp)
                            }
                        },
                    )
                }
            }
        }
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
    var keyVisible by remember { mutableStateOf(false) }
    val deepSeekSelected = provider == null || provider.id == ProviderCatalog.DEFAULT_PROVIDER_ID
    val otherProvider = provider?.takeUnless { it.id == ProviderCatalog.DEFAULT_PROVIDER_ID }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.Xxl)
            .testTag("onboarding_step_provider"),
    ) {
        Spacer(Modifier.height(DdSpacing.Sm))
        StepHeading(
            title = stringResource(R.string.onboarding_provider_title),
            subtitle = stringResource(R.string.onboarding_provider_subtitle),
        )
        Spacer(Modifier.height(DdSpacing.Xxl))
        FieldLabel(text = stringResource(R.string.onboarding_provider_section))
        Spacer(Modifier.height(DdSpacing.Sm))
        OnboardingCard {
            ProviderOption(
                providerId = ProviderCatalog.DEFAULT_PROVIDER_ID,
                name = deepseek?.name ?: "DeepSeek",
                badge = stringResource(R.string.onboarding_provider_recommended),
                subtitle = stringResource(R.string.onboarding_provider_deepseek_body),
                selected = deepSeekSelected,
                onClick = onPickDeepSeek,
                testTag = "onboarding_provider_deepseek",
            )
            if (otherProvider != null) {
                DdHairline()
                ProviderOption(
                    providerId = otherProvider.id,
                    name = otherProvider.name,
                    badge = null,
                    subtitle = stringResource(R.string.onboarding_provider_models, otherProvider.modelCount),
                    selected = true,
                    onClick = {},
                    testTag = "onboarding_provider_selected",
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        ProviderLink(
            text = stringResource(R.string.onboarding_provider_choose_other),
            onClick = { sheetOpen = true },
            testTag = "onboarding_provider_other",
        )
        if (provider != null) {
            Spacer(Modifier.height(DdSpacing.Xxl))
            if (provider.hasOAuth) {
                FieldLabel(text = stringResource(R.string.onboarding_provider_oauth_label))
                Spacer(Modifier.height(DdSpacing.Sm))
                OnboardingCard {
                    OAuthOptionRow(
                        signedIn = oauthSignedIn,
                        busy = oauthBusy,
                        onLogin = onStartOAuth,
                    )
                    if (oauthSignedIn) {
                        DdHairline()
                        Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Sm)) {
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
                                .padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Md)
                                .testTag("onboarding_provider_oauth_error"),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
                Spacer(Modifier.height(DdSpacing.Xxl))
            }
            if (provider.supportsApiKey) {
                FieldLabel(text = stringResource(R.string.onboarding_provider_api_key_label))
                Spacer(Modifier.height(DdSpacing.Xxs))
                UnderlineField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    placeholder = stringResource(R.string.onboarding_provider_api_key_hint),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    visualTransformation = if (keyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    testTag = "onboarding_provider_key",
                    trailing = {
                        CircleActionButton(
                            icon = DdIcons.Eye,
                            contentDescription = stringResource(
                                if (keyVisible) {
                                    R.string.onboarding_provider_key_hide
                                } else {
                                    R.string.onboarding_provider_key_show
                                },
                            ),
                            onClick = { keyVisible = !keyVisible },
                        )
                    },
                )
                when (keyState) {
                    KEY_STATE_SAVING -> {
                        Spacer(Modifier.height(DdSpacing.Sm))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DdSpinner(size = 14.dp, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(DdSpacing.Sm))
                            Text(
                                text = stringResource(R.string.onboarding_provider_key_saving),
                                style = DdType.bodySmall,
                                color = colors.inkSecondary,
                            )
                        }
                    }

                    KEY_STATE_SAVED -> {
                        Spacer(Modifier.height(DdSpacing.Sm))
                        Text(
                            text = stringResource(R.string.onboarding_provider_key_saved),
                            style = DdType.bodySmall,
                            color = colors.accent,
                        )
                    }

                    KEY_STATE_FAILED -> {
                        Spacer(Modifier.height(DdSpacing.Sm))
                        Text(
                            text = stringResource(R.string.onboarding_provider_key_failed),
                            style = DdType.bodySmall,
                            color = colors.danger,
                        )
                    }
                }
            } else if (!provider.hasOAuth) {
                Text(
                    text = stringResource(R.string.onboarding_provider_extra_config),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
        }
        Spacer(Modifier.height(DdSpacing.Xxl))
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
internal fun PermissionsStep(
    onSharedStorageGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(notificationsEnabled(context)) }
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var assistant by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    var shortcut by remember { mutableStateOf(assistantShortcutPinned(context)) }
    var storage by remember { mutableStateOf(storageAccessGranted(context)) }
    var calendar by remember { mutableStateOf(calendarAccessGranted(context)) }
    var camera by remember { mutableStateOf(cameraAccessGranted(context)) }
    var microphone by remember { mutableStateOf(microphoneAccessGranted(context)) }
    var battery by remember { mutableStateOf(batteryOptimizationIgnored(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifications = granted }
    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        storage = granted
        if (granted) onSharedStorageGranted()
    }
    val calendarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { calendar = calendarAccessGranted(context) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> camera = granted }
    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> microphone = granted }
    LifecycleResumeEffect(Unit) {
        val hadStorage = storage
        notifications = notificationsEnabled(context)
        overlay = Settings.canDrawOverlays(context)
        assistant = AssistantRole.isHeld(context)
        shortcut = assistantShortcutPinned(context)
        storage = storageAccessGranted(context)
        if (!hadStorage && storage) onSharedStorageGranted()
        calendar = calendarAccessGranted(context)
        camera = cameraAccessGranted(context)
        microphone = microphoneAccessGranted(context)
        battery = batteryOptimizationIgnored(context)
        onPauseOrDispose {}
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.Xxl)
            .testTag("onboarding_step_permissions"),
    ) {
        Spacer(Modifier.height(DdSpacing.Sm))
        StepHeading(
            title = stringResource(R.string.onboarding_permissions_title),
            subtitle = stringResource(R.string.onboarding_permissions_subtitle),
        )
        Spacer(Modifier.height(DdSpacing.Xxl))
        OnboardingCard {
            PermissionRow(
                icon = DdIcons.Notifications,
                title = stringResource(R.string.onboarding_permission_notification),
                subtitle = stringResource(R.string.onboarding_permission_notification_hint),
                granted = notifications,
                testTag = "onboarding_permission_notification",
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && !notifications) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.FloatingWindow,
                title = stringResource(R.string.onboarding_permission_overlay),
                subtitle = stringResource(R.string.onboarding_permission_overlay_hint),
                granted = overlay,
                testTag = "onboarding_permission_overlay",
                onClick = { openOverlaySettings(context) },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Sparkles,
                title = stringResource(R.string.onboarding_permission_assistant),
                subtitle = stringResource(R.string.onboarding_permission_assistant_hint),
                granted = assistant,
                testTag = "onboarding_permission_assistant",
                onClick = { openAssistantSettings(context) },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Apps,
                title = stringResource(R.string.onboarding_permission_shortcut),
                subtitle = stringResource(R.string.onboarding_permission_shortcut_hint),
                granted = shortcut,
                testTag = "onboarding_permission_shortcut",
                onClick = { shortcut = pinAssistantShortcut(context) },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Folder,
                title = stringResource(R.string.onboarding_permission_storage),
                subtitle = stringResource(R.string.onboarding_permission_storage_hint),
                granted = storage,
                testTag = "onboarding_permission_storage",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        openAllFilesSettings(context)
                    } else {
                        storageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Sun,
                title = stringResource(R.string.onboarding_permission_calendar),
                subtitle = stringResource(R.string.onboarding_permission_calendar_hint),
                granted = calendar,
                testTag = "onboarding_permission_calendar",
                onClick = {
                    calendarLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR,
                        ),
                    )
                },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Camera,
                title = stringResource(R.string.onboarding_permission_camera),
                subtitle = stringResource(R.string.onboarding_permission_camera_hint),
                granted = camera,
                testTag = "onboarding_permission_camera",
                onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Mic,
                title = stringResource(R.string.onboarding_permission_microphone),
                subtitle = stringResource(R.string.onboarding_permission_microphone_hint),
                granted = microphone,
                testTag = "onboarding_permission_microphone",
                onClick = { microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
            DdHairline()
            PermissionRow(
                icon = DdIcons.Shield,
                title = stringResource(R.string.onboarding_permission_battery),
                subtitle = stringResource(R.string.onboarding_permission_battery_hint),
                granted = battery,
                testTag = "onboarding_permission_battery",
                onClick = { openBatteryOptimizationSettings(context) },
            )
        }
        Spacer(Modifier.height(DdSpacing.Huge))
    }
}

@Composable
internal fun EnvironmentStep(
    bundles: List<DevBundle>,
    installedIds: Set<String>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DdSpacing.Xxl)
            .testTag("onboarding_step_environment"),
    ) {
        Spacer(Modifier.height(DdSpacing.Sm))
        StepHeading(
            title = stringResource(R.string.onboarding_environment_title),
            subtitle = stringResource(R.string.onboarding_environment_subtitle),
        )
        Spacer(Modifier.height(DdSpacing.Xxl))
        OnboardingCard {
            bundles.forEachIndexed { index, bundle ->
                if (index > 0) DdHairline()
                EnvironmentRow(
                    bundle = bundle,
                    installed = bundle.id in installedIds,
                    selected = bundle.id in selectedIds,
                    onToggle = { onToggle(bundle.id) },
                )
            }
        }
        Spacer(Modifier.height(DdSpacing.Xxl))
    }
}

@Composable
private fun EnvironmentRow(
    bundle: DevBundle,
    installed: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onToggle,
        enabled = !installed,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_environment_${bundle.id}")
            .semantics { this.selected = selected && !installed },
        shape = DdShapes.Card,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = DdSpacing.Lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(bundle.titleRes),
                        style = DdType.body,
                        color = colors.ink,
                    )
                    Spacer(Modifier.width(DdSpacing.Sm))
                    Text(
                        text = bundle.sizeLabel,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                bundle.bodyRes?.let { body ->
                    Text(
                        text = stringResource(body),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.width(DdSpacing.Md))
            if (installed) {
                Text(
                    text = stringResource(R.string.dev_installed),
                    style = DdType.bodySmall,
                    color = colors.success,
                )
            } else {
                SelectionMark(selected = selected)
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    val colors = deepDeepColors()
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (selected) colors.accent else colors.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                DdIcon(icon = DdIcons.Check, tint = colors.onAccent, size = 13.dp)
            }
        }
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
            .padding(horizontal = DdSpacing.Xxl)
            .testTag("onboarding_step_privilege"),
    ) {
        Spacer(Modifier.height(DdSpacing.Sm))
        StepHeading(
            title = stringResource(R.string.onboarding_privilege_title),
            subtitle = stringResource(R.string.onboarding_privilege_subtitle),
        )
        Spacer(Modifier.height(DdSpacing.Xxl))
        OnboardingCard {
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
        Spacer(Modifier.height(DdSpacing.Xxl))
    }
}

@Composable
private fun AvatarButton(avatar: ImageBitmap?, onPick: () -> Unit) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onPick,
        modifier = Modifier.size(44.dp).testTag("onboarding_avatar"),
        shape = CircleShape,
        contentDescription = stringResource(R.string.onboarding_avatar_pick),
    ) {
        if (avatar != null) {
            Image(
                bitmap = avatar,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(colors.wash)
                    .border(1.dp, colors.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                DdIcon(icon = DdIcons.Image, tint = colors.inkTertiary, size = 20.dp)
            }
        }
    }
}

@Composable
private fun CircleActionButton(
    icon: DdIcon,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        background = colors.wash,
        contentDescription = contentDescription,
    ) {
        DdIcon(icon = icon, tint = colors.inkSecondary, size = 20.dp)
    }
}

@Composable
private fun ProviderOption(
    providerId: String,
    name: String,
    badge: String?,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = DdShapes.Card,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(providerId, name, size = 40.dp, iconSize = 24.dp)
            Spacer(Modifier.width(DdSpacing.Md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = name, style = DdType.body, color = colors.ink)
                    if (badge != null) {
                        Spacer(Modifier.width(DdSpacing.Sm))
                        Text(text = badge, style = DdType.bodySmall, color = colors.accent)
                    }
                }
                if (subtitle != null) {
                    Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
                }
            }
            SelectionCheck(visible = selected)
        }
    }
}

@Composable
private fun ProviderLink(
    text: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag(testTag),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = DdType.label, color = colors.accent)
            Spacer(Modifier.width(6.dp))
            DdIcon(
                icon = DdIcons.ArrowLeft,
                tint = colors.accent,
                modifier = Modifier.graphicsLayer { rotationZ = 180f },
                size = 14.dp,
            )
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
    subtitle: String,
    granted: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    DdListRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        subtitleMaxLines = 1,
        onClick = onClick,
        showChevron = true,
        modifier = Modifier.testTag(testTag),
        trailing = { GrantStatus(granted = granted) },
    )
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
