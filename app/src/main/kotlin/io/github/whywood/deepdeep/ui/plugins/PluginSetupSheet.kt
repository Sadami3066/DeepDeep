package io.github.whywood.deepdeep.ui.plugins

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.PluginInteraction
import io.github.whywood.deepdeep.plugin.PluginManager

@Composable
fun PluginSetupSheet(
    manager: PluginManager,
    interaction: PluginInteraction,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    DdSheet(onDismiss = { manager.cancelInteraction() }) {
        Column(Modifier.fillMaxWidth().testTag("plugin_setup_sheet")) {
            DdSheetHeader(
                title = interaction.title,
                onClose = { manager.cancelInteraction() },
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = interaction.stageTitle,
                    style = DdType.label,
                    color = colors.ink,
                )
                interaction.stageMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                }
                if (interaction.confirmInstall) {
                    Spacer(Modifier.height(20.dp))
                    DdPrimaryButton(
                        text = stringResource(R.string.plugins_confirm_install_continue),
                        onClick = { manager.confirmInstall() },
                        modifier = Modifier.fillMaxWidth().testTag("plugin_setup_confirm"),
                    )
                    Spacer(Modifier.height(8.dp))
                    DdTextAction(
                        text = stringResource(R.string.plugins_cancel),
                        onClick = { manager.cancelInteraction() },
                        modifier = Modifier.testTag("plugin_setup_cancel"),
                        color = colors.inkSecondary,
                    )
                } else {
                    interaction.url?.let { url ->
                        Spacer(Modifier.height(12.dp))
                        DdTextAction(
                            text = stringResource(R.string.plugins_open_browser),
                            onClick = { openUrl(url) },
                            modifier = Modifier.testTag("plugin_setup_open_browser"),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = url,
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    interaction.progress?.let { progress ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.plugins_downloading_percent, progress),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (interaction.url != null || interaction.progress != null) {
                        DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (interaction.url != null) {
                                stringResource(R.string.plugins_waiting_browser)
                            } else {
                                interaction.stageTitle
                            },
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    DdTextAction(
                        text = stringResource(R.string.plugins_cancel),
                        onClick = { manager.cancelInteraction() },
                        modifier = Modifier.testTag("plugin_setup_cancel"),
                        color = colors.inkSecondary,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
