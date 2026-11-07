package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.core.SessionTitleModels
import io.github.whywood.deepdeep.data.SessionNamingMode
import io.github.whywood.deepdeep.data.UiPreferences
import io.github.whywood.deepdeep.ui.navigation.AppDestination
import io.github.whywood.deepdeep.ui.navigation.AppNavigator
import io.github.whywood.deepdeep.ui.settings.AgentSettingsScreen
import kotlinx.coroutines.launch

@Composable
internal fun AgentSettingsRoute(
    dependencies: AppDependencies,
    navigator: AppNavigator,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val privilegeState by dependencies.privilege.state.collectAsStateWithLifecycle()
    val screenOps by dependencies.capabilitySettingsStore.screenOps.collectAsStateWithLifecycle(
        initialValue = false,
    )
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
        privilege = privilegeState.effective,
        screenOps = screenOps,
        onScreenOpsChange = { enabled ->
            scope.launch { dependencies.capabilitySettingsStore.setScreenOps(enabled) }
        },
        onOpenPermission = { navigator.push(AppDestination.Permission) },
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
