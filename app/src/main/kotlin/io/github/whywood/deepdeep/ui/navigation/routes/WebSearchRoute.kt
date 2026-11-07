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
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    LaunchedEffect(store) {
        configured = store.readAll().keys.associateWith { true }
        WebSearchSettingsStore.providers.forEach { id ->
            launch {
                store.hasKey(id).collect { present ->
                    configured = configured + (id to present)
                }
            }
        }
    }
    WebSearchSettingsScreen(
        configured = configured,
        onSave = { id, key ->
            scope.launch {
                store.save(id, key)
                configured = configured + (id to true)
            }
        },
        onClear = { id ->
            scope.launch {
                store.save(id, null)
                configured = configured + (id to false)
            }
        },
        onBack = onBack,
    )
}
