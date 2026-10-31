package io.github.whywood.deepdeep.vscreen

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.DeepDeepApplication
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.coroutines.launch

class VirtualScreenActivity : ComponentActivity() {
    private val graph by lazy { (application as DeepDeepApplication).graph }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguagePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val fontStyle = graph.fontPreferences.read()
        setContent {
            val theme by graph.themePreferences.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            DeepDeepTheme(theme, fontStyle) {
                VirtualScreenViewer(onClose = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
    }

    override fun onStop() {
        AppVisibility.activityStopped()
        super.onStop()
    }
}

@Composable
private fun VirtualScreenViewer(onClose: () -> Unit) {
    val colors = deepDeepColors()
    val engine = remember { VirtualScreenRuntime.engine() }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("virtual_screen_viewer"),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onClose,
                contentDescription = stringResource(R.string.virtual_screen_viewer_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.virtual_screen_viewer_title),
                style = DdType.heading,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            if (engine != null) {
                val state by engine.state.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                if (state is VirtualScreenState.Online) {
                    DdTextAction(
                        text = stringResource(R.string.virtual_screen_viewer_stop),
                        onClick = { scope.launch { engine.stop() } },
                        color = colors.danger,
                    )
                }
            }
        }
        if (engine == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.virtual_screen_viewer_unavailable),
                    style = DdType.body,
                    color = colors.inkTertiary,
                )
            }
            return@Column
        }
        val state by engine.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val online = state as? VirtualScreenState.Online
            val frame = online?.frame
            if (frame != null) {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = stringResource(R.string.virtual_screen_viewer_title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                EmptyState(
                    state = state,
                    onStart = { scope.launch { engine.start() } },
                )
            }
        }
        ViewerFooter(state)
    }
}

@Composable
private fun EmptyState(state: VirtualScreenState, onStart: () -> Unit) {
    val colors = deepDeepColors()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        val message = when (state) {
            VirtualScreenState.Offline -> stringResource(R.string.virtual_screen_viewer_offline)
            VirtualScreenState.Starting -> stringResource(R.string.virtual_screen_viewer_starting)
            is VirtualScreenState.Failed -> state.message
            is VirtualScreenState.Online -> stringResource(R.string.virtual_screen_viewer_waiting)
        }
        Text(
            text = message,
            style = DdType.body,
            color = colors.inkSecondary,
        )
        if (state !is VirtualScreenState.Starting) {
            Spacer(Modifier.height(20.dp))
            DdPrimaryButton(
                text = stringResource(R.string.virtual_screen_viewer_start),
                onClick = onStart,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@Composable
private fun ViewerFooter(state: VirtualScreenState) {
    val colors = deepDeepColors()
    val online = state as? VirtualScreenState.Online
    val backend = online?.backend?.let { backend ->
        when (backend) {
            PrivilegedLauncher.BACKEND_ROOT -> stringResource(R.string.plugin_virtual_screen_backend_root)
            PrivilegedLauncher.BACKEND_ADB -> stringResource(R.string.plugin_virtual_screen_backend_adb)
            else -> backend
        }
    } ?: stringResource(R.string.plugin_virtual_screen_value_offline)
    val uid = VirtualScreenRuntime.engine()?.helperUid
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (val current = state) {
                is VirtualScreenState.Online -> "${current.width} × ${current.height} · #${current.displayId}"
                VirtualScreenState.Starting -> stringResource(R.string.plugin_virtual_screen_status_starting)
                is VirtualScreenState.Failed -> stringResource(R.string.plugin_virtual_screen_status_failed)
                VirtualScreenState.Offline -> stringResource(R.string.plugin_virtual_screen_status_offline)
            },
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (uid != null) "$backend · uid $uid" else backend,
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}
