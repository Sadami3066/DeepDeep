package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevPackageCatalog
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
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
    onInstallPackage: (String) -> Unit,
    onUninstallPackage: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val busyLabel = state.operation?.label

    Column(modifier.fillMaxWidth()) {
        DdSectionLabel(
            stringResource(R.string.dev_bundles_title),
            Modifier.padding(start = 4.dp),
        )
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
                val installedCount = bundle.packageIds.count { it in state.installed }
                val installing = busyLabel == title
                BundleRow(
                    bundle = bundle,
                    title = title,
                    installedCount = installedCount,
                    installed = installedCount == bundle.packageIds.size,
                    installing = installing,
                    stage = state.operation?.stage,
                    enabled = linuxInstalled && (!state.busy || installing),
                    onInstall = { onInstallBundle(bundle) },
                )
                if (index != DevPackageCatalog.bundles.lastIndex) DdHairline()
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DdSectionLabel(
                stringResource(R.string.dev_individual_title),
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
            DevPackageCatalog.packages.forEachIndexed { index, packageInfo ->
                val installed = packageInfo.id in state.installed
                val installing = state.operation?.label == packageInfo.displayName
                DdListRow(
                    title = packageInfo.displayName,
                    subtitle = packageInfo.sizeLabel,
                    trailing = {
                        when {
                            installing -> {
                                DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.dev_installing),
                                    style = DdType.bodySmall,
                                    color = colors.inkTertiary,
                                )
                            }

                            installed -> {
                                DdTextAction(
                                    text = stringResource(R.string.dev_uninstall),
                                    onClick = { onUninstallPackage(packageInfo.id) },
                                    enabled = linuxInstalled && !state.busy,
                                    color = colors.danger,
                                )
                            }

                            else -> {
                                DdTextAction(
                                    text = stringResource(R.string.dev_install),
                                    onClick = { onInstallPackage(packageInfo.id) },
                                    enabled = linuxInstalled && !state.busy,
                                )
                            }
                        }
                    },
                )
                if (index != DevPackageCatalog.packages.lastIndex) DdHairline()
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
    val subtitle = stringResource(
        if (installed) R.string.dev_bundle_installed_count
        else R.string.dev_bundle_package_count,
        installedCount,
        bundle.packageIds.size,
    )
    DdListRow(
        title = title,
        subtitle = "$subtitle · ${stringResource(bundle.subtitleRes)}",
        value = if (installed) stringResource(R.string.dev_installed) else bundle.sizeLabel,
        trailing = {
            when {
                installing -> {
                    DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                    stage?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = it,
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                }

                !installed -> {
                    DdTextAction(
                        text = stringResource(R.string.dev_install),
                        onClick = onInstall,
                        enabled = enabled,
                    )
                }
            }
        },
    )
}
