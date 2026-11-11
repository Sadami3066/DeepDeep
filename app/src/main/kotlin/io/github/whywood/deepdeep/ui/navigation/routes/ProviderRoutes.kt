package io.github.whywood.deepdeep.ui.navigation.routes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.settings.CustomProviderEditorScreen
import io.github.whywood.deepdeep.ui.settings.ProviderDetailScreen
import io.github.whywood.deepdeep.ui.settings.ProviderListScreen
import kotlinx.coroutines.launch

@Composable
internal fun ProvidersRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
) {
    val customProviders by dependencies.customProviderStore.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val customProviderIds = remember(customProviders) { customProviders.map { it.id }.toSet() }
    ProviderListScreen(
        providers = dependencies.providerCatalog.summaries,
        configuredProviderIds = configuredProviderIds,
        onBack = onBack,
        onAddCustomProvider = { navigator.push(AppDestination.CustomProviderEditor()) },
        onProviderClick = { id ->
            if (id in customProviderIds) {
                navigator.push(AppDestination.CustomProviderEditor(id))
            } else {
                navigator.push(AppDestination.ProviderDetail(id))
            }
        },
    )
}

@Composable
internal fun CustomProviderEditorRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    providerId: String?,
    onBack: () -> Unit,
) {
    val entries by dependencies.customProviderStore.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val entry = remember(entries, providerId) { entries.firstOrNull { it.id == providerId } }
    if (providerId != null && entry == null) {
        LaunchedEffect(providerId) { navigator.pop() }
        return
    }
    val scope = rememberCoroutineScope()
    CustomProviderEditorScreen(
        entry = entry,
        onBack = onBack,
        onSave = { updated ->
            scope.launch {
                dependencies.customProviderStore.saveEntry(updated)
                dependencies.reloadCustomProviders()
                navigator.pop()
            }
        },
        onDelete = entry?.let { existing ->
            {
                scope.launch {
                    dependencies.customProviderStore.delete(existing.id)
                    dependencies.reloadCustomProviders()
                    navigator.pop()
                }
            }
        },
    )
}

@Composable
internal fun ProviderDetailRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    providerId: String,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
) {
    val provider = dependencies.providerCatalog.summary(providerId)
    if (provider == null) {
        LaunchedEffect(providerId) { navigator.pop() }
        return
    }
    val authKinds by dependencies.credentials.authKinds.collectAsStateWithLifecycle(initialValue = emptyMap())
    val credentialRevision by dependencies.credentials.revision.collectAsStateWithLifecycle(initialValue = 0L)
    val savedApiKey = remember(providerId, credentialRevision) {
        dependencies.credentials.cachedApiKey(providerId)
    }
    ProviderDetailScreen(
        provider = provider,
        configured = providerId in configuredProviderIds,
        savedApiKey = savedApiKey,
        authKind = authKinds[providerId],
        oauthService = dependencies.providerOAuthService,
        onAuthChanged = {},
        onBack = onBack,
        onSaveApiKey = { key ->
            runCatching {
                dependencies.credentials.writeApiKey(providerId, key, null)
                dependencies.credentials.awaitCommit(providerId)
            }
        },
        onClearApiKey = {
            runCatching {
                dependencies.credentials.delete(providerId)
                dependencies.credentials.awaitCommit(providerId)
            }
        },
    )
}
