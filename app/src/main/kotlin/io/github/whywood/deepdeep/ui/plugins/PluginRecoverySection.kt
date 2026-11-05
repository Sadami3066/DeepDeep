package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.PluginSource
import io.github.whywood.deepdeep.plugin.manager.PluginPhaseUi

internal fun showsRecoverySection(phase: PluginPhaseUi, lastError: String?): Boolean =
    lastError != null && (phase == PluginPhaseUi.FAILED || phase == PluginPhaseUi.DEGRADED)

internal fun rollbackTargetVersion(source: PluginSource, previousVersion: String?): String? =
    previousVersion?.takeIf { source == PluginSource.EXTERNAL }

internal fun supportsActivationRecovery(source: PluginSource): Boolean = source == PluginSource.EXTERNAL

@Composable
internal fun PluginRecoverySection(
    lastError: String,
    previousVersion: String?,
    canRetryActivation: Boolean,
    busy: Boolean,
    error: String?,
    onRetryActivation: () -> Unit,
    onRollback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Column(modifier.padding(horizontal = 24.dp)) {
        DdSectionLabel(
            text = stringResource(R.string.plugins_recovery_section),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        PluginGroup {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = lastError,
                    style = DdType.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.testTag("plugin_recovery_last_error"),
                )
            }
        }
        if (canRetryActivation) {
            Spacer(Modifier.height(12.dp))
            DdPrimaryButton(
                text = stringResource(R.string.plugins_recovery_retry),
                onClick = onRetryActivation,
                modifier = Modifier.fillMaxWidth().testTag("plugin_recovery_retry"),
                enabled = !busy,
            )
        }
        if (previousVersion != null) {
            Spacer(Modifier.height(8.dp))
            DdTextAction(
                text = stringResource(R.string.plugins_recovery_rollback, previousVersion),
                onClick = onRollback,
                enabled = !busy,
                modifier = Modifier.testTag("plugin_recovery_rollback"),
                color = colors.inkSecondary,
            )
        }
        error?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = DdType.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = 4.dp).testTag("plugin_recovery_error"),
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
