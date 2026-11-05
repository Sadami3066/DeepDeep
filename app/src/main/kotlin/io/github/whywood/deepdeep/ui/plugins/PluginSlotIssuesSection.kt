package io.github.whywood.deepdeep.ui.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.plugin.api.SlotIds
import io.github.whywood.deepdeep.plugin.host.PluginFailureGuard
import io.github.whywood.deepdeep.plugin.host.PluginFailureMarker
import io.github.whywood.deepdeep.plugin.host.ui.SlotDisabledReason
import io.github.whywood.deepdeep.plugin.host.ui.SlotRenderEntry

internal data class PluginDetailSectionEntries(
    val slotId: String,
    val entries: List<SlotRenderEntry>,
)

internal fun detailSectionEntries(entries: List<SlotRenderEntry>): List<PluginDetailSectionEntries> =
    entries
        .filter { entry -> entry.slotId.startsWith(SlotIds.DETAIL_SECTION_PREFIX) }
        .groupBy { entry -> entry.slotId }
        .map { (slotId, values) -> PluginDetailSectionEntries(slotId, values) }

internal fun slotIdFromGuardKey(key: String): String = key.substringAfter(':')

internal fun crashMarkersForVersion(
    pluginId: String,
    entries: List<SlotRenderEntry>,
    markers: List<PluginFailureMarker>,
): List<PluginFailureMarker> {
    val prefixes = entries.map { it.manifestVersion }.distinct().map { version -> "$pluginId@$version:" }
    if (prefixes.isEmpty()) return markers.filter { marker -> marker.key.startsWith("$pluginId@") }
    return markers.filter { marker -> prefixes.any { prefix -> marker.key.startsWith(prefix) } }
}

@Composable
internal fun PluginSlotIssuesSection(
    pluginId: String,
    entries: List<SlotRenderEntry>,
    failureGuard: PluginFailureGuard,
    modifier: Modifier = Modifier,
) {
    var refreshToken by remember(pluginId) { mutableStateOf(0) }
    val crashes = remember(refreshToken, pluginId, entries) {
        crashMarkersForVersion(pluginId, entries, failureGuard.detectFor(pluginId))
    }
    val disabled = remember(entries) { entries.filter { entry -> entry.disabledReason != null } }
    if (crashes.isEmpty() && disabled.isEmpty()) return
    Spacer(Modifier.height(18.dp))
    Column(modifier.padding(horizontal = 24.dp)) {
        DdSectionLabel(
            text = stringResource(R.string.plugins_slot_issues_section),
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        PluginGroup {
            crashes.forEachIndexed { index, marker ->
                if (index > 0) DdHairline()
                DdListRow(
                    title = slotIdFromGuardKey(marker.key),
                    subtitle = stringResource(R.string.plugins_slot_crash_reason),
                    trailing = {
                        DdTextAction(
                            text = stringResource(R.string.plugins_slot_recover),
                            onClick = {
                                failureGuard.clear(marker.key)
                                refreshToken += 1
                            },
                        )
                    },
                    modifier = Modifier.testTag("plugin_slot_recover"),
                )
            }
            if (crashes.isNotEmpty() && disabled.isNotEmpty()) DdHairline()
            disabled.forEachIndexed { index, entry ->
                if (index > 0) DdHairline()
                DdListRow(
                    title = entry.slotId,
                    subtitle = slotDisabledReasonText(entry.disabledReason),
                )
            }
        }
    }
}

@Composable
internal fun slotDisabledReasonText(reason: SlotDisabledReason?): String = when (reason) {
    null -> ""
    is SlotDisabledReason.UiApiLevelUnsupported -> stringResource(
        R.string.plugins_slot_ui_api_unsupported,
        reason.declared,
        reason.host,
    )
    is SlotDisabledReason.ComposeApiLevelUnsupported -> stringResource(
        R.string.plugins_slot_compose_api_unsupported,
        reason.declared,
        reason.host,
    )
    is SlotDisabledReason.KotlinVersionMismatch -> stringResource(
        R.string.plugins_slot_kotlin_mismatch,
        reason.declared,
        reason.host,
    )
    is SlotDisabledReason.ComposeVersionMismatch -> stringResource(
        R.string.plugins_slot_compose_mismatch,
        reason.declared,
        reason.host,
    )
    is SlotDisabledReason.ComposeVersionUnavailable -> reason.compatibilityId?.let { compatibilityId ->
        stringResource(R.string.plugins_slot_compose_unavailable_id, compatibilityId)
    } ?: stringResource(R.string.plugins_slot_compose_unavailable)
}
