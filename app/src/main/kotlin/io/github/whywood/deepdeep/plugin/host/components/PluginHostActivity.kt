package io.github.whywood.deepdeep.plugin.host.components

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemePreset
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteractionHost
import io.github.whywood.deepdeep.plugin.host.interaction.PluginInteractionOverlay
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.ui.common.ProvideHostFiles
import io.github.whywood.deepdeep.ui.common.chat.LocalPluginManager
import io.github.whywood.deepdeep.ui.common.chat.ProvidePluginManager
import io.github.whywood.deepdeep.ui.plugins.PluginPageHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class PluginHostActivity : ComponentActivity() {
    private val graph by lazy { (application as DeepDeepApplication).graph }
    private var pluginId: String = ""
    private var pageId: String = ""
    private var arguments: JsonObject = JsonObject(emptyMap())

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID).orEmpty()
        pageId = intent.getStringExtra(EXTRA_PAGE_ID).orEmpty()
        arguments = intent.getStringExtra(EXTRA_ARGUMENTS)?.let { raw ->
            runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())
        if (pluginId.isBlank() || pageId.isBlank()) {
            finish()
            return
        }
        val initialFontStyle = graph.fontPreferences.read()
        setContent {
            val preset by graph.themePreferences.preset.collectAsStateWithLifecycle(
                initialValue = ThemePreset.SYSTEM,
            )
            DeepDeepTheme(preset = preset, fontStyle = initialFontStyle) {
                ProvideHostFiles(graph.hostFiles) {
                    ProvidePluginManager(graph.pluginManager) {
                        Box(Modifier.fillMaxSize()) {
                            PluginHostPageContent(
                                pluginId = pluginId,
                                pageId = pageId,
                                arguments = arguments,
                                onBack = { finish() },
                            )
                            PluginInteractionOverlay()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
        PluginInteractionHost.attachActivity(this)
    }

    override fun onStop() {
        PluginInteractionHost.detachActivity(this)
        AppVisibility.activityStopped()
        super.onStop()
    }

    fun finishWithResult(result: JsonObject?) {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_RESULT, result?.toString()),
        )
        finish()
    }

    companion object {
        private const val EXTRA_PLUGIN_ID = "io.github.whywood.deepdeep.extra.PLUGIN_PAGE_PLUGIN"
        private const val EXTRA_PAGE_ID = "io.github.whywood.deepdeep.extra.PLUGIN_PAGE_PAGE"
        private const val EXTRA_ARGUMENTS = "io.github.whywood.deepdeep.extra.PLUGIN_PAGE_ARGUMENTS"
        private const val EXTRA_RESULT = "io.github.whywood.deepdeep.extra.PLUGIN_PAGE_RESULT"

        fun launch(
            context: Context,
            pluginId: String,
            pageId: String,
            arguments: JsonObject = JsonObject(emptyMap()),
            newTask: Boolean = true,
        ) {
            if (pluginId.isBlank() || pageId.isBlank()) return
            val intent = Intent(context, PluginHostActivity::class.java)
                .putExtra(EXTRA_PLUGIN_ID, pluginId)
                .putExtra(EXTRA_PAGE_ID, pageId)
            if (arguments.isNotEmpty()) intent.putExtra(EXTRA_ARGUMENTS, arguments.toString())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

@Composable
private fun PluginHostPageContent(
    pluginId: String,
    pageId: String,
    arguments: JsonObject,
    onBack: () -> Unit,
) {
    val colors = deepDeepColors()
    val manager = LocalPluginManager.current
    val title = if (manager == null) "" else PluginPageTitle(manager, pluginId, pageId)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.plugins_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title.ifBlank { stringResource(R.string.plugins_page_title) },
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PluginPageHost(
            pluginId = pluginId,
            pageId = pageId,
            arguments = arguments,
            fallback = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.plugin_host_page_unavailable),
                        modifier = Modifier.padding(horizontal = 32.dp),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            },
        )
    }
}

@Composable
private fun PluginPageTitle(
    manager: PluginManagerFacade,
    pluginId: String,
    pageId: String,
): String {
    val uiState by manager.state.collectAsStateWithLifecycle()
    return remember(uiState, manager, pluginId, pageId) {
        manager.pagesOf(pluginId)
            .firstOrNull { it.id == pageId }
            ?.title
            ?.let(manager::resolveText)
            .orEmpty()
    }
}
