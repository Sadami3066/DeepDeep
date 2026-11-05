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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevPackageCatalog
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
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
    var detailBundle by remember { mutableStateOf<DevBundle?>(null) }

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
                    stage = operation?.takeIf { installing }?.stage,
                    enabled = linuxInstalled && (!state.busy || installing),
                    onOpen = { detailBundle = bundle },
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
    }

    val bundle = detailBundle ?: return
    val title = stringResource(bundle.titleRes)
    val installedCount = bundle.packageIds.count { it in state.installed }
    val installing = operation?.label == title
    DevBundleDetailDialog(
        bundle = bundle,
        installedCount = installedCount,
        installing = installing,
        stage = operation?.takeIf { installing }?.stage,
        enabled = linuxInstalled && (!state.busy || installing),
        onInstall = {
            detailBundle = null
            onInstallBundle(bundle)
        },
        onDismiss = { detailBundle = null },
    )
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
    onOpen: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentDescription = title,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = DdType.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                                maxLines = 1,
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
                                "$installedCount/${bundle.packageIds.size} · ${bundle.sizeLabel}"
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
}

@Composable
private fun DevBundleDetailDialog(
    bundle: DevBundle,
    installedCount: Int,
    installing: Boolean,
    stage: String?,
    enabled: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = deepDeepColors()
    val installed = installedCount == bundle.packageIds.size
    DdDialog(
        onDismissRequest = onDismiss,
        title = stringResource(bundle.titleRes),
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                DevPackageCatalog.packages
                    .filter { it.id in bundle.packageIds }
                    .forEach { packageInfo ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = packageInfo.displayNameRes?.let { stringResource(it) } ?: packageInfo.displayName,
                                    style = DdType.body,
                                    color = colors.ink,
                                    maxLines = 1,
                                )
                                Text(
                                    text = stringResource(packageInfo.descriptionRes),
                                    style = DdType.bodySmall,
                                    color = colors.inkTertiary,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = packageInfo.sizeLabel,
                                style = DdType.bodySmall,
                                color = colors.inkTertiary,
                                maxLines = 1,
                            )
                        }
                    }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.dev_bundle_total, bundle.sizeLabel),
                style = DdType.bodySmall,
                color = colors.inkSecondary,
            )
            when {
                installing -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stage ?: stringResource(R.string.dev_stage_installing),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }

                !installed -> {
                    Spacer(Modifier.height(16.dp))
                    DdPrimaryButton(
                        text = stringResource(if (installedCount > 0) R.string.dev_continue else R.string.dev_install),
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                    )
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.dev_close),
                onClick = onDismiss,
                color = colors.inkSecondary,
            )
        },
    )
}
