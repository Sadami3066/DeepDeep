package io.github.whywood.superpi.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.superpi.core.AppDependencies
import io.github.whywood.superpi.core.SessionTitleModels
import io.github.whywood.superpi.data.SessionNamingMode
import io.github.whywood.superpi.data.UiPreferences
import io.github.whywood.superpi.ui.navigation.AppDestination
import io.github.whywood.superpi.ui.navigation.AppNavigator
import io.github.whywood.superpi.ui.settings.AgentSettingsScreen
import kotlinx.coroutines.launch

@Composable
internal fun AgentSettingsRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val preferences by dependencies.appPreferences.preferences.collectAsStateWithLifecycle(
        initialValue = UiPreferences(),
    )
    val models = remember(configuredProviderIds) {
        configuredProviderIds.sorted()
            .flatMap { providerId -> dependencies.providerCatalog.models(providerId) }
            .let(SessionTitleModels::rank)
    }
    val selectedModel = models.firstOrNull {
        it.providerId == preferences.metadataProviderId && it.id == preferences.metadataModelId
    }
    AgentSettingsScreen(
        onOpenWebSearch = { navigator.push(AppDestination.WebSearch) },
        metadataMode = preferences.metadataMode,
        metadataModelLabel = selectedModel?.displayName,
        metadataModels = models,
        onMetadataModeChange = { mode ->
            scope.launch {
                dependencies.appPreferences.setMetadataMode(mode)
                if (mode == SessionNamingMode.MANUAL && selectedModel == null) {
                    (SessionTitleModels.preferred(models) ?: models.firstOrNull())?.let { model ->
                        dependencies.appPreferences.setMetadataModel(model.providerId, model.id)
                    }
                }
            }
        },
        onMetadataModelChange = { model ->
            scope.launch {
                dependencies.appPreferences.setMetadataModel(model.providerId, model.id)
                dependencies.appPreferences.setMetadataMode(SessionNamingMode.MANUAL)
            }
        },
        onBack = onBack,
    )
}
