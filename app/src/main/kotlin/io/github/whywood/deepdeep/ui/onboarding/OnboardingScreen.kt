package io.github.whywood.deepdeep.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.data.OnboardingPreferences
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.ProfileAvatarStore
import io.github.whywood.deepdeep.data.TonePreset
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdWordmark
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.privilege.PrivilegeLevel
import io.github.whywood.deepdeep.shell.LinuxInstallState
import io.github.whywood.deepdeep.runtime.InteractionStyle
import io.github.whywood.deepdeep.runtime.ModelConfig
import io.github.whywood.deepdeep.runtime.ProviderAuthKind
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.ui.common.OAuthLoginSession
import io.github.whywood.deepdeep.ui.common.OAuthLoginSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STEP_WELCOME = 0
private const val STEP_ACCOUNT = 1
private const val STEP_PROVIDER = 2
private const val STEP_FEATURE_MEMORY = 3
private const val STEP_FEATURE_CONNECTOR = 4
private const val STEP_FEATURE_SKILLS = 5
private const val STEP_FEATURE_TIMERS = 6
private const val STEP_FEATURE_ASK = 7
private const val STEP_FEATURE_PRIVACY = 8
private const val STEP_PERMISSIONS = 9
private const val STEP_LINUX = 10
private const val STEP_PRIVILEGE = 11
private const val LAST_STEP = STEP_PRIVILEGE

@Composable
fun OnboardingScreen(
    graph: AppDependencies,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onboardingStore = remember(context) { OnboardingPreferences(context) }
    val avatarStore = remember(context) { ProfileAvatarStore(context) }
    val languagePreferences = remember(context) { LanguagePreferences(context) }

    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var toneName by rememberSaveable { mutableStateOf(TonePreset.CUSTOM.name) }
    var languageTag by rememberSaveable { mutableStateOf(languagePreferences.read().tag) }
    var avatarVersion by remember { mutableIntStateOf(0) }
    var providerId by rememberSaveable { mutableStateOf(ProviderCatalog.DEFAULT_PROVIDER_ID) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var keyState by rememberSaveable { mutableIntStateOf(KEY_STATE_IDLE) }
    var privilegeName by rememberSaveable { mutableStateOf(PrivilegeLevel.NORMAL.name) }

    val tone = TonePreset.entries.firstOrNull { it.name == toneName } ?: TonePreset.CUSTOM
    val language = AppLanguage.fromTag(languageTag)
    val privilege = PrivilegeLevel.entries.firstOrNull { it.name == privilegeName } ?: PrivilegeLevel.NORMAL
    val providers = remember(graph.providerCatalog) { graph.providerCatalog.summaries }
    val provider = remember(providers, providerId) { providers.firstOrNull { it.id == providerId } }
    val deepseek = remember(providers) { providers.firstOrNull { it.id == ProviderCatalog.DEFAULT_PROVIDER_ID } }
    val linuxState by graph.linuxEnvironment.state.collectAsStateWithLifecycle()
    val authKinds by graph.credentials.authKinds.collectAsStateWithLifecycle()
    val oauthSignedIn = provider?.let { authKinds[it.id] == ProviderAuthKind.OAUTH } == true
    var oauthSession by remember(providerId) { mutableStateOf<OAuthLoginSession?>(null) }
    var oauthBusy by remember(providerId) { mutableStateOf(false) }
    var oauthError by remember(providerId) { mutableStateOf<String?>(null) }
    val oauthFailedText = stringResource(R.string.onboarding_provider_oauth_failed)
    val avatarFailedText = stringResource(R.string.onboarding_avatar_failed)

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { avatarStore.save(uri) }
            if (saved) {
                avatarVersion += 1
            } else {
                Toast.makeText(context, avatarFailedText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(step) {
        if (step == STEP_LINUX) graph.linuxEnvironment.refreshState()
    }

    LaunchedEffect(Unit) {
        if (keyState == KEY_STATE_SAVING) keyState = KEY_STATE_IDLE
    }

    fun back() {
        if (step > STEP_WELCOME) step -= 1
    }

    fun goNext() {
        if (step < LAST_STEP) step += 1
    }

    fun skipFeatureTour() {
        step = STEP_PERMISSIONS
    }

    fun saveProfile() {
        val instruction = tonePresetRes(tone).takeIf { it != 0 }?.let(context::getString).orEmpty()
        val value = PersonalizationUi(
            nickname = nickname.trim(),
            interaction = InteractionStyle.BALANCED,
            tone = tone,
            instruction = instruction,
        )
        scope.launch { graph.personalizationStore.save(value) }
    }

    fun finish() {
        saveProfile()
        onboardingStore.markCompleted()
        graph.pluginManager.refresh()
        onFinished()
    }

    fun changeLanguage(next: AppLanguage) {
        if (next.tag == languageTag) return
        languageTag = next.tag
        languagePreferences.save(next)
        graph.pluginManager.refresh()
        Handler(Looper.getMainLooper()).post { context.findActivity()?.recreate() }
    }

    fun selectPrivilege(level: PrivilegeLevel) {
        privilegeName = level.name
        scope.launch { graph.privilege.select(level) }
    }

    fun saveKeyThenAdvance() {
        val key = apiKey.trim()
        if (key.isEmpty() || provider?.supportsApiKey != true) {
            goNext()
            return
        }
        keyState = KEY_STATE_SAVING
        scope.launch {
            val result = runCatching {
                graph.credentials.writeApiKey(providerId, key, null)
                graph.credentials.awaitCommit(providerId)
            }
            keyState = if (result.isSuccess) KEY_STATE_SAVED else KEY_STATE_FAILED
            if (result.isSuccess) {
                apiKey = ""
                if (providerId != ProviderCatalog.DEFAULT_PROVIDER_ID) {
                    graph.modelConfigStore.save(
                        graph.providerCatalog.resolveConfig(ModelConfig(providerId = providerId, modelId = "")),
                    )
                }
                if (step == STEP_PROVIDER) goNext()
            }
        }
    }

    fun beginOAuthLogin() {
        val target = provider ?: return
        if (oauthSession != null) return
        oauthError = null
        val session = OAuthLoginSession(
            providerName = target.name,
            onOpenUrl = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            },
        )
        oauthSession = session
        oauthBusy = true
        scope.launch {
            val result = graph.providerOAuthService.login(target.id, session)
            oauthBusy = false
            if (oauthSession !== session) return@launch
            if (result.isSuccess) {
                session.succeed()
                oauthSession = null
            } else {
                val detail = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                val message = if (detail == null) oauthFailedText else "$oauthFailedText: $detail"
                session.fail(message)
                oauthError = message
            }
        }
    }

    fun signOutOAuth() {
        val target = provider ?: return
        oauthError = null
        scope.launch {
            runCatching { graph.providerOAuthService.logout(target.id) }
        }
    }

    fun primaryAction() {
        when (step) {
            STEP_WELCOME -> goNext()
            STEP_ACCOUNT -> {
                saveProfile()
                goNext()
            }

            STEP_PROVIDER -> saveKeyThenAdvance()
            STEP_PRIVILEGE -> finish()
            else -> goNext()
        }
    }

    val primaryLabel = when (step) {
        STEP_WELCOME -> stringResource(R.string.onboarding_primary_start)
        STEP_PRIVILEGE -> stringResource(R.string.onboarding_primary_done)
        else -> stringResource(R.string.onboarding_primary_continue)
    }
    val inFeatureTour = step in STEP_FEATURE_MEMORY..STEP_FEATURE_PRIVACY
    val secondaryLabel = when {
        step == STEP_PROVIDER -> stringResource(R.string.onboarding_skip)
        inFeatureTour && step != STEP_FEATURE_PRIVACY -> stringResource(R.string.onboarding_skip)
        step == STEP_PERMISSIONS || step == STEP_LINUX -> stringResource(R.string.onboarding_later)
        else -> null
    }
    val secondaryAction: () -> Unit = if (inFeatureTour) ::skipFeatureTour else ::goNext
    val savingKey = step == STEP_PROVIDER && keyState == KEY_STATE_SAVING
    val primaryEnabled = !savingKey
    val secondaryEnabled = !savingKey

    BackHandler(onBack = ::back)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("onboarding_root"),
    ) {
        OnboardingHeader(step = step, onBack = ::back)
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                val direction = if (forward) 1 else -1
                val enter = slideInHorizontally(
                    animationSpec = tween(DdMotion.SCREEN, easing = DdMotion.EaseOut),
                ) { width -> direction * width / 8 } +
                    fadeIn(tween(DdMotion.SCREEN, easing = DdMotion.EaseOut))
                val exit = slideOutHorizontally(
                    animationSpec = tween(DdMotion.SCREEN, easing = DdMotion.EaseInOut),
                ) { width -> -direction * width / 8 } +
                    fadeOut(tween(DdMotion.FAST, easing = DdMotion.EaseOut))
                enter togetherWith exit
            },
            label = "onboarding-step",
        ) { target ->
            when (target) {
                STEP_WELCOME -> WelcomeStep()
                STEP_ACCOUNT -> AccountStep(
                    nickname = nickname,
                    onNicknameChange = { nickname = it },
                    tone = tone,
                    onToneChange = { toneName = it.name },
                    language = language,
                    onLanguageChange = ::changeLanguage,
                    avatarVersion = avatarVersion,
                    loadAvatar = avatarStore::load,
                    onPickAvatar = {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )

                STEP_PROVIDER -> ProviderStep(
                    provider = provider,
                    deepseek = deepseek,
                    providers = providers,
                    apiKey = apiKey,
                    onApiKeyChange = { value ->
                        apiKey = value
                        if (keyState != KEY_STATE_SAVING) keyState = KEY_STATE_IDLE
                    },
                    keyState = keyState,
                    oauthSignedIn = oauthSignedIn,
                    oauthBusy = oauthBusy,
                    oauthError = oauthError,
                    onPickDeepSeek = {
                        providerId = ProviderCatalog.DEFAULT_PROVIDER_ID
                        apiKey = ""
                        keyState = KEY_STATE_IDLE
                    },
                    onSelectProvider = { picked ->
                        providerId = picked.id
                        apiKey = ""
                        keyState = KEY_STATE_IDLE
                    },
                    onStartOAuth = ::beginOAuthLogin,
                    onSignOutOAuth = ::signOutOAuth,
                )

                STEP_FEATURE_MEMORY -> MemoryFeatureStep()
                STEP_FEATURE_CONNECTOR -> ConnectorFeatureStep()
                STEP_FEATURE_SKILLS -> SkillsFeatureStep()
                STEP_FEATURE_TIMERS -> TimersFeatureStep()
                STEP_FEATURE_ASK -> AskFeatureStep()
                STEP_FEATURE_PRIVACY -> PrivacyFeatureStep()
                STEP_PERMISSIONS -> PermissionsStep()
                STEP_LINUX -> LinuxStep(
                    state = linuxState,
                    supported = graph.linuxEnvironment.isSupported,
                    onInstall = {
                        scope.launch {
                            val result = graph.linuxEnvironment.install()
                            if (result is LinuxInstallState.Installed) graph.linuxSettings.setEnabled(true)
                        }
                    },
                )

                else -> PrivilegeStep(selected = privilege, onSelect = ::selectPrivilege)
            }
        }
        OnboardingFooter(
            primaryLabel = primaryLabel,
            primaryEnabled = primaryEnabled,
            primaryTestTag = if (step == STEP_PRIVILEGE) "onboarding_privilege_done" else "onboarding_primary",
            onPrimary = ::primaryAction,
            secondaryLabel = secondaryLabel,
            secondaryEnabled = secondaryEnabled,
            onSecondary = secondaryAction,
        )
    }

    oauthSession?.let { session ->
        OAuthLoginSheet(
            providerName = provider?.name.orEmpty(),
            session = session,
            onDismiss = {
                session.cancel()
                oauthBusy = false
                if (oauthSession === session) oauthSession = null
            },
        )
    }
}

@Composable
private fun OnboardingFooter(
    primaryLabel: String,
    primaryEnabled: Boolean,
    primaryTestTag: String,
    onPrimary: () -> Unit,
    secondaryLabel: String?,
    secondaryEnabled: Boolean,
    onSecondary: () -> Unit,
) {
    val colors = deepDeepColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        DdPrimaryButton(
            text = primaryLabel,
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth().testTag(primaryTestTag),
        )
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            if (secondaryLabel != null) {
                DdTextAction(
                    text = secondaryLabel,
                    onClick = onSecondary,
                    color = colors.inkTertiary,
                    enabled = secondaryEnabled,
                )
            }
        }
    }
}

@Composable
fun OnboardingSplash(modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(
        modifier.fillMaxSize().background(colors.paper),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(DdMotion.ENTER, easing = DdMotion.EaseOut)),
        ) {
            DdWordmark()
        }
    }
}

private fun tonePresetRes(tone: TonePreset): Int = when (tone) {
    TonePreset.WARM -> R.string.personalization_preset_warm
    TonePreset.CUTE -> R.string.personalization_preset_cute
    TonePreset.CALM -> R.string.personalization_preset_calm
    TonePreset.CUSTOM -> 0
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
