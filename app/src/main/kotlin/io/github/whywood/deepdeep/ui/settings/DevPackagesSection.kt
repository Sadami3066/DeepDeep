package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevPackageCatalog
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun DevPackagesSection(
    state: DevPackagesState,
    linuxInstalled: Boolean,
    onInstallBundle: (DevBundle) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val operation = state.operation

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DdSectionLabel(
                stringResource(R.string.dev_bundles_title),
                Modifier.weight(1f).padding(start = 4.dp),
            )
            DdTextAction(
                text = stringResource(R.string.dev_refresh),
                onClick = onRefresh,
                enabled = linuxInstalled && !state.busy && !state.checking,
            )
        }
        Spacer(Modifier.height(10.dp))

        SettingsGroup {
            if (!linuxInstalled) {
                Text(
                    text = stringResource(R.string.dev_linux_required),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                DdHairline()
            }
            DevPackageCatalog.bundles.forEachIndexed { index, bundle ->
                val title = stringResource(bundle.titleRes)
                val installing = operation?.label == title
                val installedCount = bundle.packageIds.count { it in state.installed }
                BundleRow(
                    bundle = bundle,
                    title = title,
                    installedCount = installedCount,
                    installed = installedCount == bundle.packageIds.size,
                    installing = installing,
                    stage = operation?.stage,
                    enabled = linuxInstalled && (!state.busy || installing),
                    onInstall = { onInstallBundle(bundle) },
                )
                if (index != DevPackageCatalog.bundles.lastIndex) DdHairline()
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.dev_error, state.error),
                modifier = Modifier.padding(horizontal = 4.dp),
                style = DdType.bodySmall,
                color = colors.danger,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.dev_self_install_hint),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
}

@Composable
private fun BundleRow(
    bundle: DevBundle,
    title: String,
    installedCount: Int,
    installed: Boolean,
    installing: Boolean,
    stage: String?,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    val colors = deepDeepColors()
    val includes = bundle.packageIds.joinToString("、")

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = DdType.body,
                color = colors.ink,
                maxLines = 1,
            )
            Text(
                text = stringResource(bundle.subtitleRes),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
            Text(
                text = stringResource(R.string.dev_includes, includes),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        when {
            installing -> {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                    stage?.let {
                        Text(
                            text = it,
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                }
            }

            installed -> {
                Text(
                    text = stringResource(R.string.dev_installed),
                    style = DdType.bodySmall,
                    color = colors.accent,
                )
            }

            else -> {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (installedCount > 0) {
                            "${installedCount}/${bundle.packageIds.size} · ${bundle.sizeLabel}"
                        } else {
                            bundle.sizeLabel
                        },
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                    DdTextAction(
                        text = stringResource(if (installedCount > 0) R.string.dev_continue else R.string.dev_install),
                        onClick = onInstall,
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
