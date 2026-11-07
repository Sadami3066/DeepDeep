package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.data.WebSearchSettingsStore
import io.github.whywood.deepdeep.ui.settings.WebSearchSettingsScreen
import kotlinx.coroutines.launch

@Composable
internal fun WebSearchRoute(
    dependencies: AppDependencies,
    onBack: () -> Unit,
) {
    val store = dependencies.webSearchSettings
    val credentials = dependencies.credentials
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    LaunchedEffect(store, credentials) {
        configured = store.readAll().keys.associateWith { true }
        WebSearchSettingsStore.providers.forEach { id ->
            launch {
                store.hasKey(id).collect { present ->
                    configured = configured + (id to present)
                }
            }
        }
        credentials.preload(WebSearchSettingsStore.DEEPSEEK)
        launch {
            credentials.revision.collect {
                configured = configured + (
                    WebSearchSettingsStore.DEEPSEEK to
                        (credentials.cachedApiKey(WebSearchSettingsStore.DEEPSEEK) != null)
                    )
            }
        }
    }
    WebSearchSettingsScreen(
        configured = configured,
        onSave = { id, key ->
            scope.launch {
                if (id == WebSearchSettingsStore.DEEPSEEK) {
                    credentials.writeApiKey(id, key, null)
                    credentials.awaitCommit(id)
                } else {
                    store.save(id, key)
                }
                configured = configured + (id to true)
            }
        },
        onClear = { id ->
            scope.launch {
                if (id == WebSearchSettingsStore.DEEPSEEK) {
                    credentials.delete(id)
                    credentials.awaitCommit(id)
                } else {
                    store.save(id, null)
                }
                configured = configured + (id to false)
            }
        },
        onBack = onBack,
    )
}
