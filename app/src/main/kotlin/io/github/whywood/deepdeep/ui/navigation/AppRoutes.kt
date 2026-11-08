package io.github.whywood.deepdeep.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.whywood.deepdeep.ui.assistant.AssistantSettingsScreen
import io.github.whywood.deepdeep.ui.mcp.McpScreen
import io.github.whywood.deepdeep.ui.navigation.routes.ArchivedRoute
import io.github.whywood.deepdeep.ui.navigation.routes.AgentSettingsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.WebSearchRoute
import io.github.whywood.deepdeep.ui.navigation.routes.ChatSettingsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.FilesRoute
import io.github.whywood.deepdeep.ui.navigation.routes.FontsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.PermissionRoute
import io.github.whywood.deepdeep.ui.navigation.routes.PersonalizationRoute
import io.github.whywood.deepdeep.ui.navigation.routes.PluginPageRoute
import io.github.whywood.deepdeep.ui.navigation.routes.PluginsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.ProviderDetailRoute
import io.github.whywood.deepdeep.ui.navigation.routes.ProvidersRoute
import io.github.whywood.deepdeep.ui.navigation.routes.RemoteRoute
import io.github.whywood.deepdeep.ui.navigation.routes.RuntimeSettingsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.ScanJsonRoute
import io.github.whywood.deepdeep.ui.navigation.routes.ScanSettingsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.SearchRoute
import io.github.whywood.deepdeep.ui.navigation.routes.SettingsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.TimersRoute
import io.github.whywood.deepdeep.ui.navigation.routes.UsageHabitsRoute
import io.github.whywood.deepdeep.ui.navigation.routes.MiniappDetailRoute
import io.github.whywood.deepdeep.ui.navigation.routes.MemoryRoute
import io.github.whywood.deepdeep.ui.navigation.routes.MiniappRoute
import io.github.whywood.deepdeep.ui.navigation.routes.WorkspaceSettingsRoute
import io.github.whywood.deepdeep.ui.settings.AndroidSettingsScreen
import io.github.whywood.deepdeep.ui.settings.PrivacyScreen
import io.github.whywood.deepdeep.ui.skills.SkillsScreen

data class RouteSpec(
    val routeKey: String,
    val depth: Int,
    val parentRouteKey: String?,
)

private const val PLUGIN_DETAIL_PREFIX = "plugins/"
private const val PLUGIN_PAGE_MARKER = "/page/"
private const val MINIAPP_DETAIL_PREFIX = "miniapp/"

fun parentRouteKey(routeKey: String): String? = when {
    routeKey.startsWith(PLUGIN_DETAIL_PREFIX) -> "plugins"
    routeKey.startsWith(MINIAPP_DETAIL_PREFIX) -> "miniapp"
    else -> null
}

fun routeSpec(destination: AppDestination): RouteSpec =
    RouteSpec(destination.routeKey, destination.depth, parentRouteKey(destination.routeKey))

fun routeSpec(routeKey: String): RouteSpec? = AppDestination.fromRouteKey(routeKey)?.let(::routeSpec)

fun deepLinkStack(destination: AppDestination): List<AppDestination> {
    val parent = parentRouteKey(destination.routeKey)?.let(AppDestination::fromRouteKey)
    return if (parent == null) listOf(destination) else listOf(parent, destination)
}

fun deepLinkStack(routeKey: String): List<AppDestination> =
    AppDestination.fromRouteKey(routeKey)?.let(::deepLinkStack).orEmpty()

@Composable
internal fun AppRouteContent(destination: AppDestination, host: AppRouteHost) {
    val dependencies = host.dependencies
    val onBack = host::back
    when (destination) {
        AppDestination.Chat -> Box(Modifier.fillMaxSize())

        is AppDestination.Files -> FilesRoute(
            rootPath = destination.rootPath,
            chat = host.chat,
            onOpenFile = host.onOpenFile,
            onBack = onBack,
        )

        AppDestination.Settings -> SettingsRoute(
            dependencies = dependencies,
            navigator = host.navigator,
            scope = host.scope,
            ui = host.ui,
            archivedCount = host.chat.archivedCount,
            onBack = onBack,
        )

        AppDestination.Providers -> ProvidersRoute(
            dependencies = dependencies,
            navigator = host.navigator,
            configuredProviderIds = host.ui.configuredProviderIds,
            onBack = onBack,
        )

        AppDestination.RuntimeSettings -> RuntimeSettingsRoute(dependencies, host.scope, onBack)

        AppDestination.Permission -> PermissionRoute(dependencies, host.scope, onBack)

        AppDestination.AndroidSettings -> AndroidSettingsScreen(onBack = onBack)

        AppDestination.AgentSettings -> AgentSettingsRoute(
            dependencies = dependencies,
            navigator = host.navigator,
            configuredProviderIds = host.ui.configuredProviderIds,
            onBack = onBack,
        )


        AppDestination.WebSearch -> WebSearchRoute(dependencies, onBack)

        AppDestination.Personalization -> PersonalizationRoute(dependencies, host.scope, onBack)

        AppDestination.Privacy -> PrivacyScreen(onBack = onBack)

        AppDestination.AssistantSettings -> AssistantSettingsScreen(onBack = onBack)

        AppDestination.Fonts -> FontsRoute(host.ui, onBack)

        AppDestination.ChatSettings -> ChatSettingsRoute(dependencies, host.scope, onBack)

        AppDestination.UsageHabits -> UsageHabitsRoute(dependencies, host.scope, onBack)

        AppDestination.Scan -> ScanSettingsRoute(
            onBack = onBack,
            onOpenJsonEditor = { host.navigator.push(AppDestination.ScanJson) },
        )

        AppDestination.ScanJson -> ScanJsonRoute(onBack)

        is AppDestination.ProviderDetail -> ProviderDetailRoute(
            dependencies = dependencies,
            navigator = host.navigator,
            providerId = destination.id,
            configuredProviderIds = host.ui.configuredProviderIds,
            onBack = onBack,
        )

        AppDestination.Mcp -> McpScreen(
            store = dependencies.mcpConfigStore,
            probe = dependencies.mcpServerProbe,
            oauth = dependencies.mcpOAuthService,
            marketRepository = dependencies.marketRepository,
            onBack = onBack,
            onReloadSessions = { dependencies.host.reloadMcp() },
            onReconnect = { server -> dependencies.host.reconnectMcp(server) },
        )

        AppDestination.Plugins -> PluginsRoute(dependencies, host.navigator, onBack)

        is AppDestination.PluginPage -> PluginPageRoute(
            destination = destination,
            manager = dependencies.pluginManager,
            onBack = onBack,
        )

        AppDestination.Archived -> ArchivedRoute(
            chatViewModel = host.chatViewModel,
            archived = host.chat.archived,
            onOpenChat = host.onOpenChat,
            onBack = onBack,
        )

        AppDestination.Search -> SearchRoute(host.chatViewModel, host.navigator, onBack)

        AppDestination.Skills -> SkillsScreen(
            repository = dependencies.skillRepository,
            marketRepository = dependencies.marketRepository,
            onBack = onBack,
        )

        AppDestination.Timers -> TimersRoute(
            dependencies = dependencies,
            chatViewModel = host.chatViewModel,
            conversationId = host.chat.conversationId,
            navigator = host.navigator,
            onBack = onBack,
        )

        AppDestination.Memory -> MemoryRoute(
            dependencies = dependencies,
            conversationId = host.chat.conversationId,
            onBack = onBack,
        )

        AppDestination.Remote -> RemoteRoute(onBack = onBack)

        is AppDestination.WorkspaceSettings -> WorkspaceSettingsRoute(
            dependencies = dependencies,
            navigator = host.navigator,
            workspaces = host.chat.workspaces,
            workspaceId = destination.workspaceId,
            onBack = onBack,
        )

        AppDestination.Miniapp -> MiniappRoute(
            dependencies = dependencies,
            navigator = host.navigator,
            onOpenMiniappCreate = host.onOpenMiniappCreate,
            onBack = onBack,
        )

        is AppDestination.MiniappDetail -> MiniappDetailRoute(
            dependencies = dependencies,
            creationId = destination.id,
            onBack = onBack,
        )
    }
}
