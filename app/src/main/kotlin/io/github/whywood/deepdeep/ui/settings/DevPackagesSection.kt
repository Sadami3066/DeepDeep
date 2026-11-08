package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.data.ApkMirrors
import io.github.whywood.deepdeep.data.DevBundle
import io.github.whywood.deepdeep.data.DevBundleCustomization
import io.github.whywood.deepdeep.data.DevPackageCatalog
import io.github.whywood.deepdeep.data.DevPackagesState
import io.github.whywood.deepdeep.data.label
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class DevSheetMode { DETAIL, PACKAGES, MIRRORS }

@Composable
internal fun DevPackagesSection(
    state: DevPackagesState,
    linuxInstalled: Boolean,
    onInstallBundle: (DevBundle) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DevBundleCustomizationStore(context) }
    var customization by remember { mutableStateOf(store.load()) }
    var openedBundle by remember { mutableStateOf<DevBundle?>(null) }
    var sheetMode by remember { mutableStateOf(DevSheetMode.DETAIL) }
    var mirrorError by remember { mutableStateOf(false) }
    val operation = state.operation

    fun persist(next: DevBundleCustomization) {
        customization = next
        scope.launch(Dispatchers.IO) { store.save(next) }
    }

    fun toggleExtra(bundleId: String, packageId: String) {
        val current = customization.extrasFor(bundleId).toMutableList()
        if (!current.remove(packageId)) current.add(packageId)
        persist(customization.copy(extras = customization.extras + (bundleId to current)))
    }

    fun toggleOverlay(bundleId: String, overlayId: String) {
        val current = customization.overlaysFor(bundleId).toMutableList()
        if (!current.remove(overlayId)) current.add(overlayId)
        persist(customization.copy(overlays = customization.overlays + (bundleId to current)))
    }

    fun toggleMirror(mirrorId: String) {
        val current = customization.mirrorIds().toMutableList()
        if (mirrorId in current) {
            if (current.size == 1) {
                mirrorError = true
                return
            }
            current.remove(mirrorId)
        } else {
            current.add(mirrorId)
        }
        mirrorError = false
        persist(customization.copy(mirrors = current))
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DdSectionLabel(
                text = stringResource(R.string.dev_bundles_title),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
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
                val composed = composeBundle(bundle, customization)
                val title = stringResource(bundle.titleRes)
                val installing = operation?.label == title
                val installedCount = composed.packageIds.count { it in state.installed }
                val installed = composed.packageIds.isNotEmpty() && installedCount == composed.packageIds.size
                DdListRow(
                    icon = DdIcons.Package,
                    title = title,
                    subtitle = bundle.bodyRes?.let { stringResource(it) },
                    subtitleMaxLines = 2,
                    onClick = {
                        mirrorError = false
                        sheetMode = DevSheetMode.DETAIL
                        openedBundle = bundle
                    },
                    showChevron = true,
                    modifier = Modifier.testTag("dev_bundle_${bundle.id}"),
                    trailing = {
                        when {
                            installing -> {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    DdSpinner(size = 16.dp, strokeWidth = 2.dp)
                                    operation?.stage?.let {
                                        Text(
                                            text = it,
                                            style = DdType.bodySmall,
                                            color = colors.inkTertiary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }

                            installed -> Text(
                                text = stringResource(R.string.dev_installed),
                                style = DdType.bodySmall,
                                color = colors.accent,
                            )

                            installedCount > 0 -> Text(
                                text = "$installedCount/${composed.packageIds.size} · ${composed.sizeLabel}",
                                style = DdType.bodySmall,
                                color = colors.inkTertiary,
                                maxLines = 1,
                            )

                            else -> Text(
                                text = composed.sizeLabel,
                                style = DdType.bodySmall,
                                color = colors.inkTertiary,
                                maxLines = 1,
                            )
                        }
                    },
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

    val bundle = openedBundle ?: return
    val composed = composeBundle(bundle, customization)
    val title = stringResource(bundle.titleRes)
    val installing = operation?.label == title
    DdSheet(onDismiss = { openedBundle = null }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (sheetMode != DevSheetMode.DETAIL) {
                    DdIconButton(
                        icon = DdIcons.ArrowLeft,
                        onClick = { sheetMode = DevSheetMode.DETAIL },
                        contentDescription = stringResource(R.string.settings_back),
                        tint = colors.inkSecondary,
                        iconSize = 20.dp,
                    )
                }
                Spacer(Modifier.weight(1f))
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = { openedBundle = null },
                    contentDescription = stringResource(R.string.dev_close),
                    tint = colors.inkSecondary,
                    iconSize = 20.dp,
                )
            }
            when (sheetMode) {
                DevSheetMode.DETAIL -> BundleForm(
                    bundle = bundle,
                    composed = composed,
                    extras = customization.extrasFor(bundle.id),
                    overlays = customization.overlaysFor(bundle.id),
                    mirrorIds = customization.mirrorIds(),
                    installedCount = composed.packageIds.count { it in state.installed },
                    installing = installing,
                    busy = state.busy,
                    linuxInstalled = linuxInstalled,
                    stage = operation?.takeIf { installing }?.stage,
                    onAddExtras = { sheetMode = DevSheetMode.PACKAGES },
                    onRemoveExtra = { toggleExtra(bundle.id, it) },
                    onToggleOverlay = { toggleOverlay(bundle.id, it) },
                    onOpenMirrors = {
                        mirrorError = false
                        sheetMode = DevSheetMode.MIRRORS
                    },
                    onInstall = {
                        openedBundle = null
                        onInstallBundle(composed)
                    },
                )

                DevSheetMode.PACKAGES -> PackagePicker(
                    bundle = bundle,
                    extras = customization.extrasFor(bundle.id),
                    overlayIds = customization.overlaysFor(bundle.id),
                    onToggle = { toggleExtra(bundle.id, it) },
                    onDone = { sheetMode = DevSheetMode.DETAIL },
                )

                DevSheetMode.MIRRORS -> MirrorPicker(
                    mirrors = customization.mirrorIds(),
                    error = mirrorError,
                    linuxInstalled = linuxInstalled,
                    busy = state.busy,
                    onToggle = { toggleMirror(it) },
                    onApply = {
                        val mirrorIds = customization.mirrorIds()
                        openedBundle = null
                        onInstallBundle(DevPackageCatalog.mirrorApplyBundle(mirrorIds))
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun composeBundle(bundle: DevBundle, customization: DevBundleCustomization): DevBundle =
    DevPackageCatalog.composeBundle(
        bundle = bundle,
        extraPackageIds = customization.extrasFor(bundle.id),
        overlayBundleIds = customization.overlaysFor(bundle.id),
        mirrorIds = customization.mirrorIds(),
    )

@Composable
private fun BundleForm(
    bundle: DevBundle,
    composed: DevBundle,
    extras: List<String>,
    overlays: List<String>,
    mirrorIds: List<String>,
    installedCount: Int,
    installing: Boolean,
    busy: Boolean,
    linuxInstalled: Boolean,
    stage: String?,
    onAddExtras: () -> Unit,
    onRemoveExtra: (String) -> Unit,
    onToggleOverlay: (String) -> Unit,
    onOpenMirrors: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val installed = composed.packageIds.isNotEmpty() && installedCount == composed.packageIds.size
    Text(
        text = stringResource(bundle.titleRes),
        style = DdType.heading,
        color = colors.ink,
    )
    bundle.bodyRes?.let {
        Text(
            text = stringResource(it),
            modifier = Modifier.padding(top = 4.dp),
            style = DdType.bodySmall,
            color = colors.inkSecondary,
        )
    }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.dev_bundle_form_packages),
            modifier = Modifier.weight(1f),
            style = DdType.label,
            color = colors.ink,
        )
        Text(
            text = stringResource(
                R.string.dev_bundle_form_package_count,
                composed.packageIds.size,
                DevPackageCatalog.estimatedSizeLabel(composed.packageIds),
            ),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = composed.packageIds
            .mapNotNull { DevPackageCatalog.packageById(it)?.label(context) }
            .joinToString(" · "),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.wash)
            .padding(14.dp),
        style = DdType.bodySmall,
        color = colors.inkSecondary,
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.dev_bundle_form_extras),
            modifier = Modifier.weight(1f),
            style = DdType.label,
            color = colors.ink,
        )
        DdTextAction(
            text = stringResource(R.string.dev_bundle_form_extras_add),
            onClick = onAddExtras,
        )
    }
    if (extras.isEmpty()) {
        Text(
            text = stringResource(R.string.dev_bundle_form_extras_empty),
            modifier = Modifier.padding(vertical = 4.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    } else {
        extras.forEach { id ->
            DevPackageCatalog.packageById(id)?.let { packageInfo ->
                DdListRow(
                    title = packageInfo.label(context),
                    value = packageInfo.sizeLabel,
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        DdTextAction(
                            text = stringResource(R.string.dev_remove),
                            onClick = { onRemoveExtra(id) },
                            color = colors.inkSecondary,
                        )
                    },
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.dev_bundle_form_overlays),
        modifier = Modifier.padding(top = 20.dp),
        style = DdType.label,
        color = colors.ink,
    )
    Text(
        text = stringResource(R.string.dev_bundle_form_overlays_hint),
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        style = DdType.bodySmall,
        color = colors.inkTertiary,
    )
    DevPackageCatalog.bundles.filter { it.id != bundle.id }.forEach { other ->
        val checked = other.id in overlays
        DdListRow(
            title = stringResource(other.titleRes),
            subtitle = other.sizeLabel,
            onClick = { onToggleOverlay(other.id) },
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                DdSwitch(
                    checked = checked,
                    onCheckedChange = { onToggleOverlay(other.id) },
                    stateDescription = stringResource(
                        if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
                    ),
                )
            },
        )
    }
    Text(
        text = stringResource(R.string.dev_bundle_form_source),
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        style = DdType.label,
        color = colors.ink,
    )
    DdListRow(
        icon = DdIcons.Download,
        title = stringResource(R.string.dev_mirror_sheet_title),
        subtitle = apkMirrorSubtitle(mirrorIds),
        subtitleMaxLines = 2,
        value = stringResource(R.string.dev_bundle_form_source_multi, mirrorIds.size),
        onClick = onOpenMirrors,
        showChevron = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (composed.downloads.isNotEmpty()) {
        Text(
            text = stringResource(R.string.dev_bundle_form_downloads),
            modifier = Modifier.padding(top = 20.dp),
            style = DdType.label,
            color = colors.ink,
        )
        Text(
            text = stringResource(R.string.dev_bundle_form_downloads_body),
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        composed.downloads.forEach { download ->
            DdListRow(
                title = stringResource(download.titleRes),
                value = download.sizeLabel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    if (installing && stage != null) {
        Text(
            text = stage,
            modifier = Modifier.padding(bottom = 8.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    }
    when {
        !linuxInstalled -> Text(
            text = stringResource(R.string.dev_linux_required),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )

        installed -> DdPrimaryButton(
            text = stringResource(R.string.dev_bundle_form_installed),
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        )

        else -> DdPrimaryButton(
            text = stringResource(
                if (installedCount > 0) R.string.dev_continue else R.string.dev_bundle_form_install,
            ),
            onClick = onInstall,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
    }
}

@Composable
private fun PackagePicker(
    bundle: DevBundle,
    extras: List<String>,
    overlayIds: List<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val included = bundle.packageIds.toSet() +
        overlayIds.mapNotNull { DevPackageCatalog.bundleById(it)?.packageIds }.flatten()
    Text(
        text = stringResource(R.string.dev_package_picker_title),
        style = DdType.heading,
        color = colors.ink,
    )
    Text(
        text = stringResource(R.string.dev_package_picker_subtitle),
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        style = DdType.bodySmall,
        color = colors.inkSecondary,
    )
    DevPackageCatalog.packages.forEach { packageInfo ->
        val inBundle = packageInfo.id in included
        val selected = inBundle || packageInfo.id in extras
        DdListRow(
            title = packageInfo.label(context),
            subtitle = stringResource(packageInfo.descriptionRes),
            subtitleMaxLines = 1,
            value = packageInfo.sizeLabel,
            enabled = !inBundle,
            onClick = if (inBundle) null else ({ onToggle(packageInfo.id) }),
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                DdSwitch(
                    checked = selected,
                    onCheckedChange = { if (!inBundle) onToggle(packageInfo.id) },
                    enabled = !inBundle,
                    stateDescription = stringResource(
                        if (selected) R.string.settings_switch_on else R.string.settings_switch_off,
                    ),
                )
            },
        )
    }
    Spacer(Modifier.height(16.dp))
    DdPrimaryButton(
        text = stringResource(R.string.dev_done),
        onClick = onDone,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun apkMirrorSubtitle(mirrorIds: List<String>): String {
    val labels = ArrayList<String>(mirrorIds.size)
    for (mirror in ApkMirrors.resolve(mirrorIds)) {
        labels += stringResource(mirror.nameRes)
    }
    return labels.joinToString(" / ")
}

@Composable
private fun MirrorPicker(
    mirrors: List<String>,
    error: Boolean,
    linuxInstalled: Boolean,
    busy: Boolean,
    onToggle: (String) -> Unit,
    onApply: () -> Unit,
) {
    val colors = deepDeepColors()
    Text(
        text = stringResource(R.string.dev_mirror_sheet_title),
        style = DdType.heading,
        color = colors.ink,
    )
    Text(
        text = stringResource(R.string.dev_mirror_sheet_subtitle),
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        style = DdType.bodySmall,
        color = colors.inkSecondary,
    )
    for (mirror in ApkMirrors.all) {
        val checked = mirror.id in mirrors
        DdListRow(
            title = stringResource(mirror.nameRes),
            subtitle = mirror.baseUrl,
            subtitleMaxLines = 1,
            onClick = { onToggle(mirror.id) },
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                DdSwitch(
                    checked = checked,
                    onCheckedChange = { onToggle(mirror.id) },
                    stateDescription = stringResource(
                        if (checked) R.string.settings_switch_on else R.string.settings_switch_off,
                    ),
                )
            },
        )
    }
    if (error) {
        Text(
            text = stringResource(R.string.dev_mirror_required),
            modifier = Modifier.padding(top = 8.dp),
            style = DdType.bodySmall,
            color = colors.danger,
        )
    }
    Spacer(Modifier.height(16.dp))
    if (!linuxInstalled) {
        Text(
            text = stringResource(R.string.dev_linux_required),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
    } else {
        DdPrimaryButton(
            text = stringResource(R.string.dev_mirror_apply),
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
            enabled = mirrors.isNotEmpty() && !busy,
        )
    }
}
