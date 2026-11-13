package io.github.whywood.deepdeep.plugin.host.extensions.data

import android.content.Context
import io.github.whywood.deepdeep.arrange.AndroidArrangeCalendarGateway
import io.github.whywood.deepdeep.arrange.ArrangeCalendarGateway
import io.github.whywood.deepdeep.data.AppLanguage
import io.github.whywood.deepdeep.data.CustomProviderStore
import io.github.whywood.deepdeep.data.HostFiles
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.plugin.api.AccessibilityServicePlugin
import io.github.whywood.deepdeep.plugin.api.CalendarService
import io.github.whywood.deepdeep.plugin.api.ContactsService
import io.github.whywood.deepdeep.plugin.api.CredentialService
import io.github.whywood.deepdeep.plugin.api.LocationService
import io.github.whywood.deepdeep.plugin.api.MediaService
import io.github.whywood.deepdeep.plugin.api.PluginSettingsService
import io.github.whywood.deepdeep.plugin.api.SmsService
import io.github.whywood.deepdeep.plugin.api.SpeechService
import io.github.whywood.deepdeep.plugin.api.WebService
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteraction
import io.github.whywood.deepdeep.runtime.McpConfigStore
import io.github.whywood.deepdeep.runtime.ProviderCatalog
import io.github.whywood.deepdeep.runtime.SdkCredentialStore
import io.github.whywood.deepdeep.sdk.mcp.oauth.FileMcpTokenStore
import io.github.whywood.deepdeep.web.DeepSeekWebProvider
import io.github.whywood.deepdeep.web.WebProvider
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal data class DataPluginServices(
    val settings: PluginSettingsService,
    val location: LocationService,
    val contacts: ContactsService,
    val sms: SmsService,
    val calendar: CalendarService,
    val media: MediaService,
    val speech: SpeechService,
    val web: WebService,
    val credentials: CredentialService,
    val accessibility: AccessibilityServicePlugin,
)

internal class DataServiceDeps(
    val context: Context,
    val appContext: Context,
    val scope: CoroutineScope,
    val pluginId: String,
    val pluginDataDir: File,
    val pluginCacheDir: File,
    val pluginRootDir: File,
    val interaction: PluginInteraction,
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val language: () -> AppLanguage = { LanguagePreferences(appContext).read() },
    val hostFiles: HostFiles? = null,
    val arrangeCalendar: ArrangeCalendarGateway? = null,
    val webProvider: WebProvider? = null,
    val webApiKey: (suspend () -> String?)? = null,
    val webAvailable: () -> Boolean = { webProvider != null || webApiKey != null },
    val credentialStore: SdkCredentialStore? = null,
    val customProviders: CustomProviderStore? = null,
    val providerCatalog: ProviderCatalog? = null,
    val mcpConfig: McpConfigStore? = null,
    val mcpTokenStore: FileMcpTokenStore? = null,
    val installationId: suspend () -> String = { "" },
    val adbToken: () -> String? = { null },
    val accessibilityHub: AccessibilityEventHub = AccessibilityEventHub,
)

internal fun buildDataPluginServices(deps: DataServiceDeps): DataPluginServices {
    deps.pluginDataDir.mkdirs()
    deps.pluginCacheDir.mkdirs()
    val calendarGateway = deps.arrangeCalendar ?: AndroidArrangeCalendarGateway(deps.appContext)
    val resolvedWebProvider = deps.webProvider ?: deps.webApiKey?.let { provider -> DeepSeekWebProvider(provider) }
    return DataPluginServices(
        settings = PluginSettingsServiceImpl(
            store = PluginSettingsStore(
                file = File(File(deps.pluginDataDir, "host"), "settings.json"),
                ioDispatcher = deps.ioDispatcher,
            ),
        ),
        location = LocationPluginServiceImpl(
            context = deps.appContext,
            ioDispatcher = deps.ioDispatcher,
        ),
        contacts = ContactsPluginServiceImpl(
            context = deps.appContext,
            ioDispatcher = deps.ioDispatcher,
        ),
        sms = SmsPluginServiceImpl(
            context = deps.appContext,
            ioDispatcher = deps.ioDispatcher,
        ),
        calendar = CalendarPluginServiceImpl(
            gateway = calendarGateway,
            interaction = deps.interaction,
            context = deps.appContext,
            ioDispatcher = deps.ioDispatcher,
        ),
        media = MediaPluginServiceImpl(
            context = deps.appContext,
            pluginDataDir = deps.pluginDataDir,
            hostFiles = deps.hostFiles,
            ioDispatcher = deps.ioDispatcher,
        ),
        speech = SpeechPluginServiceImpl(
            context = deps.appContext,
            scope = deps.scope,
            language = deps.language,
            interaction = deps.interaction,
        ),
        web = WebPluginServiceImpl(
            provider = resolvedWebProvider,
            availableCheck = deps.webAvailable,
        ),
        credentials = CredentialPluginServiceImpl(
            credentialStore = deps.credentialStore,
            customProviders = deps.customProviders,
            providerCatalog = deps.providerCatalog,
            mcpConfig = deps.mcpConfig,
            mcpTokenStore = deps.mcpTokenStore,
            installationId = deps.installationId,
            adbToken = deps.adbToken,
            scope = deps.scope,
            ioDispatcher = deps.ioDispatcher,
        ),
        accessibility = AccessibilityPluginServiceImpl(deps.accessibilityHub),
    )
}
