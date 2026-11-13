package io.github.whywood.deepdeep.ui.navigation.routes
import androidx.compose.runtime.getValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.BuildConfig
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.adb.AdbBridgeRuntime
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.data.OverlayEntryMode
import io.github.whywood.deepdeep.data.PersonalizationUi
import io.github.whywood.deepdeep.data.StorageMount
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.data.update.releaseOrNull
import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.service.AgentService
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.navigation.ShellRouteUi
import io.github.whywood.deepdeep.ui.settings.ChatSettingsScreen
import io.github.whywood.deepdeep.ui.settings.FontScreen
import io.github.whywood.deepdeep.ui.settings.PersonalizationScreen
import io.github.whywood.deepdeep.ui.settings.SettingsScreen
import io.github.whywood.deepdeep.ui.settings.SystemAccessScreen
import io.github.whywood.deepdeep.ui.settings.UsageHabitsScreen
import io.github.whywood.deepdeep.ui.settings.ScanJsonScreen
import io.github.whywood.deepdeep.ui.settings.ScanSettingsScreen
import io.github.whywood.deepdeep.ui.settings.SoftwareUpdateScreen
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SettingsRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    scope: CoroutineScope,
    ui: ShellRouteUi,
    archivedCount: Int,
    onBack: () -> Unit,
) {
    val hostState by dependencies.hostAccess.state.collectAsStateWithLifecycle()
    val accessLabel = stringResource(
        when (hostState.effective) {
            HostAccess.LOCAL -> R.string.access_level_local
            HostAccess.ADB -> R.string.access_level_adb
            HostAccess.ROOT -> R.string.access_level_root
        },
    )
    val overlayEntryMode by dependencies.overlayEntryPreferences.mode.collectAsStateWithLifecycle(
        initialValue = OverlayEntryMode.VOICE,
    )
    val updateUi by dependencies.updateManager.ui.collectAsStateWithLifecycle()
    SettingsScreen(
        nickname = ui.nickname,
        avatar = ui.avatar,
        providerName = ui.providerName,
        modelName = ui.modelLabel,
        configured = ui.modelConfig.providerId in ui.configuredProviderIds,
        themePreset = ui.themePreset,
        versionName = BuildConfig.VERSION_NAME,
        fontLabel = if (ui.fontStyle == DdFontStyle.SYSTEM) {
            stringResource(R.string.settings_font_system)
        } else {
            ui.fontStyle.displayName
        },
        onBack = onBack,
        onThemePresetChange = ui.onThemePresetChange,
        onOpenChatSettings = { navigator.push(AppDestination.ChatSettings) },
        overlayEntryMode = overlayEntryMode,
        onOpenUsageHabits = { navigator.push(AppDestination.UsageHabits) },
        onOpenScan = { navigator.push(AppDestination.Scan) },
        onOpenProviders = { navigator.push(AppDestination.Providers) },
        onOpenAccess = { navigator.push(AppDestination.SystemAccess) },
        accessLabel = accessLabel,
        onOpenAgentSettings = { navigator.push(AppDestination.AgentSettings) },
        onOpenAndroidSettings = { navigator.push(AppDestination.AndroidSettings) },
        onOpenPersonalization = { navigator.push(AppDestination.Personalization) },
        onOpenFonts = { navigator.push(AppDestination.Fonts) },
        onOpenPrivacy = { navigator.push(AppDestination.Privacy) },
        archivedCount = archivedCount,
        onOpenArchived = { navigator.push(AppDestination.Archived) },
        onLanguageChange = { dependencies.pluginManager.refresh() },
        updateVersion = updateUi.phase.releaseOrNull?.versionName,
        onOpenUpdate = { navigator.push(AppDestination.Update) },
        crashLogs = dependencies.crashLogs,
    )
}

@Composable
internal fun SoftwareUpdateRoute(dependencies: AppDependencies, onBack: () -> Unit) {
    val ui by dependencies.updateManager.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { dependencies.updateManager.onPageOpened() }
    SoftwareUpdateScreen(
        ui = ui,
        currentVersion = BuildConfig.VERSION_NAME,
        onBack = onBack,
        onCheck = dependencies.updateManager::checkManually,
        onRetry = dependencies.updateManager::retry,
        onDownload = dependencies.updateManager::download,
        onInstall = dependencies.updateManager::install,
        onOpenInstallPermission = dependencies.updateManager::openInstallPermission,
        onResume = dependencies.updateManager::onScreenResumed,
    )
}

@Composable
internal fun ChatSettingsRoute(dependencies: AppDependencies, scope: CoroutineScope, onBack: () -> Unit) {
    val preferences by dependencies.appPreferences.preferences.collectAsStateWithLifecycle(
        initialValue = UiPreferences(),
    )
    val voiceAutoSpeak by dependencies.voicePreferences.autoSpeak.collectAsStateWithLifecycle(
        initialValue = false,
    )
    ChatSettingsScreen(
        preferences = preferences,
        sendWhileRunning = preferences.sendWhileRunning,
        processDetail = preferences.processDetail,
        onBack = onBack,
        onToggle = { toggle, enabled -> scope.launch { dependencies.appPreferences.set(toggle, enabled) } },
        voiceAutoSpeak = voiceAutoSpeak,
        onVoiceAutoSpeakChange = { enabled -> scope.launch { dependencies.voicePreferences.setAutoSpeak(enabled) } },
        onChatFontScaleChange = { value -> scope.launch { dependencies.appPreferences.setChatFontScale(value) } },
        onSendWhileRunningChange = { value ->
            scope.launch { dependencies.appPreferences.setSendWhileRunning(value) }
        },
        onProcessDetailChange = { value ->
            scope.launch { dependencies.appPreferences.setProcessDetail(value) }
        },
    )
}

@Composable
internal fun UsageHabitsRoute(dependencies: AppDependencies, scope: CoroutineScope, onBack: () -> Unit) {
    val mode by dependencies.overlayEntryPreferences.mode.collectAsStateWithLifecycle(
        initialValue = OverlayEntryMode.VOICE,
    )
    UsageHabitsScreen(
        mode = mode,
        onBack = onBack,
        onModeChange = { value -> scope.launch { dependencies.overlayEntryPreferences.setMode(value) } },
    )
}

@Composable
internal fun FontsRoute(ui: ShellRouteUi, onBack: () -> Unit) {
    FontScreen(
        current = ui.fontStyle,
        onBack = onBack,
        onSelect = ui.onFontStyleChange,
    )
}

@Composable
internal fun PersonalizationRoute(dependencies: AppDependencies, scope: CoroutineScope, onBack: () -> Unit) {
    val value by dependencies.personalizationStore.personalization.collectAsStateWithLifecycle(
        initialValue = PersonalizationUi(),
    )
    PersonalizationScreen(
        value = value,
        onBack = onBack,
        onSave = { next -> scope.launch { dependencies.personalizationStore.save(next) } },
    )
}

@Composable
internal fun ScanSettingsRoute(onBack: () -> Unit, onOpenJsonEditor: () -> Unit) {
    ScanSettingsScreen(onBack = onBack, onOpenJsonEditor = onOpenJsonEditor)
}

@Composable
internal fun ScanJsonRoute(onBack: () -> Unit) {
    ScanJsonScreen(onBack = onBack)
}

@Composable
internal fun SystemAccessRoute(dependencies: AppDependencies, scope: CoroutineScope, onBack: () -> Unit) {
    val context = LocalContext.current
    val adbBridge = remember { AdbBridgeRuntime.get(context) }
    val hostState by dependencies.hostAccess.state.collectAsStateWithLifecycle()
    val adb by adbBridge.state.collectAsStateWithLifecycle()
    val rootfs by dependencies.rootfsBootstrap.status.collectAsStateWithLifecycle()
    val linuxState by dependencies.linuxEnvironment.state.collectAsStateWithLifecycle()
    val linuxEnabled by dependencies.linuxSettings.enabled.collectAsStateWithLifecycle(initialValue = false)
    val sharedStorageEnabled by dependencies.linuxSettings.sharedStorageEnabled.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val devPackages by dependencies.devPackageManager.state.collectAsStateWithLifecycle()
    val devPackagesAvailable = remember(hostState.effective, linuxState, rootfs.state) {
        dependencies.hostEnvironmentProvider.current().capabilities.bashAvailable
    }
    val mounts by dependencies.storageMounts.mounts.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(Unit) { dependencies.linuxEnvironment.refreshState() }
    LaunchedEffect(hostState.effective, linuxState, rootfs.state) { dependencies.devPackageManager.refresh() }
    SystemAccessScreen(
        state = hostState,
        adb = adb,
        rootfs = rootfs,
        linuxState = linuxState,
        linuxSupported = dependencies.linuxEnvironment.isSupported,
        linuxEnabled = linuxEnabled,
        sharedStorageEnabled = sharedStorageEnabled,
        mounts = mounts,
        devPackages = devPackages,
        devPackagesAvailable = devPackagesAvailable,
        onSelect = { access -> scope.launch { dependencies.hostAccess.select(access) } },
        onRefresh = { scope.launch { dependencies.hostAccess.refresh() } },
        onPair = { dependencies.hostAccess.startAdbPairing() },
        onPairingCode = { code -> AgentService.applyPairingCode(context, code) },
        onConnect = { scope.launch { dependencies.hostAccess.connectAdb() } },
        onOpenWirelessDebugging = { dependencies.hostAccess.openWirelessDebugging(context) },
        onRetryRootfs = { scope.launch { dependencies.rootfsBootstrap.ensureReady() } },
        onRebuildRootfs = { scope.launch { dependencies.rootfsBootstrap.rebuild() } },
        onLinuxInstall = { scope.launch { dependencies.linuxEnvironment.install() } },
        onLinuxReset = {
            scope.launch {
                dependencies.linuxSettings.setEnabled(false)
                dependencies.linuxEnvironment.reset()
                dependencies.host.reconfigure()
            }
        },
        onLinuxEnabledChange = { enabled ->
            scope.launch {
                dependencies.linuxSettings.setEnabled(enabled)
                dependencies.host.reconfigure()
            }
        },
        onSharedStorageChange = { enabled -> scope.launch { dependencies.linuxSettings.setSharedStorageEnabled(enabled) } },
        onSaveMount = { mount -> scope.launch { dependencies.storageMounts.save(mergeMount(mounts, mount)) } },
        onRemoveMount = { id -> scope.launch { dependencies.storageMounts.remove(id) } },
        onInstallBundle = dependencies.devPackageManager::installBundle,
        onRefreshPackages = dependencies.devPackageManager::refresh,
        onBack = onBack,
    )
}


private fun mergeMount(mounts: List<StorageMount>, mount: StorageMount): List<StorageMount> =
    mounts.filterNot { it.id == mount.id } + mount
