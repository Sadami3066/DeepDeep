package io.github.whywood.deepdeep.ui.navigation.routes
import androidx.compose.runtime.getValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.settings.ProviderDetailScreen
import io.github.whywood.deepdeep.ui.settings.ProviderListScreen

@Composable
internal fun ProvidersRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
) {
    ProviderListScreen(
        providers = dependencies.providerCatalog.summaries,
        configuredProviderIds = configuredProviderIds,
        onBack = onBack,
        onProviderClick = { id -> navigator.push(AppDestination.ProviderDetail(id)) },
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
