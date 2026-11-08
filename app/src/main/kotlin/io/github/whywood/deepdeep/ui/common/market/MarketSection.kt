package io.github.whywood.deepdeep.ui.common.market

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdLoadingBlock
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.market.MarketEntry
import io.github.whywood.deepdeep.market.MarketEntryType
import io.github.whywood.deepdeep.market.MarketInstallState
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.market.MarketSource
import io.github.whywood.deepdeep.market.MarketState
import kotlinx.coroutines.launch

internal const val MARKET_TAB_MARKET = 0

@Composable
internal fun MarketTab(
    repository: MarketRepository,
    type: MarketEntryType,
    query: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    onInstalled: suspend () -> Unit = {},
) {
    val colors = deepDeepColors()
    val state by repository.state.collectAsStateWithLifecycle()
    val installStates by repository.installStates.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val iconLoader = rememberMarketIconLoader()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }

    val current = state
    val entries = if (current is MarketState.Ready) repository.entries(type) else emptyList()
    val visible = filterMarketEntries(entries, query)
    val selected = entries.firstOrNull { it.id == selectedId }
    val confirm = entries.firstOrNull { it.id == confirmId }

    fun install(entry: MarketEntry) {
        scope.launch {
            repository.install(entry).onSuccess { onInstalled() }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().testTag("${testTagPrefix}_market"),
    ) {
        when (current) {
            MarketState.Loading -> DdLoadingBlock(
                modifier = Modifier.padding(top = 48.dp),
                text = stringResource(R.string.market_loading),
            )

            is MarketState.Failed -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = current.message,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    DdTextAction(
                        text = stringResource(R.string.market_retry),
                        onClick = { scope.launch { repository.refresh(force = true) } },
                        modifier = Modifier.testTag("${testTagPrefix}_market_retry"),
                    )
                }
            }

            is MarketState.Ready -> {
                if (current.source != MarketSource.Network) {
                    Text(
                        text = stringResource(R.string.market_offline_notice),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("${testTagPrefix}_market_offline"),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
                when {
                    entries.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.market_empty),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }

                    visible.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_match),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }

                    else -> visible.forEachIndexed { index, entry ->
                        if (index > 0) DdHairline()
                        val installState = installStates[entry.id]
                        MarketRow(
                            entry = entry,
                            status = marketEntryStatus(entry, installState, repository.installedVersion(entry)),
                            progress = (installState as? MarketInstallState.Installing)?.progress,
                            iconLoader = iconLoader,
                            onClick = { selectedId = entry.id },
                            modifier = Modifier.testTag("${testTagPrefix}_market_row_${entry.id}"),
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (selected != null) {
        MarketDetailSheet(
            entry = selected,
            installState = installStates[selected.id],
            installedVersion = repository.installedVersion(selected),
            iconLoader = iconLoader,
            onDismiss = { selectedId = null },
            onInstall = {
                if (selected.type == MarketEntryType.Mcp) {
                    confirmId = selected.id
                } else {
                    install(selected)
                }
            },
            testTagPrefix = testTagPrefix,
        )
    }

    if (confirm != null) {
        DdDialog(
            onDismissRequest = { confirmId = null },
            title = stringResource(R.string.market_mcp_confirm_title),
            content = {
                Text(
                    text = stringResource(R.string.market_mcp_confirm_message, confirm.name),
                    style = DdType.body,
                    color = deepDeepColors().inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.market_cancel),
                    onClick = { confirmId = null },
                    color = deepDeepColors().inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.market_confirm),
                    onClick = {
                        confirmId = null
                        install(confirm)
                    },
                )
            },
        )
    }
}

@Composable
private fun MarketRow(
    entry: MarketEntry,
    status: MarketEntryStatus,
    progress: Int?,
    iconLoader: MarketIconLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentDescription = entry.name,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarketEntryIcon(entry = entry, loader = iconLoader)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.summary,
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.tags.isNotEmpty()) {
                    Text(
                        text = entry.tags.joinToString(" · "),
                        style = DdType.micro,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusLabel(status, progress),
                style = DdType.label,
                color = when (status) {
                    MarketEntryStatus.Install, MarketEntryStatus.Update -> colors.accent
                    MarketEntryStatus.Failed -> colors.danger
                    MarketEntryStatus.Installed, MarketEntryStatus.Installing -> colors.inkTertiary
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun statusLabel(status: MarketEntryStatus, progress: Int?): String = when (status) {
    MarketEntryStatus.Install -> stringResource(R.string.market_install)
    MarketEntryStatus.Installed -> stringResource(R.string.market_status_installed)
    MarketEntryStatus.Update -> stringResource(R.string.market_status_update)
    MarketEntryStatus.Installing -> if (progress != null) {
        stringResource(R.string.market_status_installing_progress, progress)
    } else {
        stringResource(R.string.market_status_installing)
    }

    MarketEntryStatus.Failed -> stringResource(R.string.market_status_failed)
}

@Composable
private fun MarketDetailSheet(
    entry: MarketEntry,
    installState: MarketInstallState?,
    installedVersion: String?,
    iconLoader: MarketIconLoader,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    testTagPrefix: String,
) {
    val colors = deepDeepColors()
    val configuration = LocalConfiguration.current
    val maxBodyHeight = configuration.screenHeightDp.dp * 0.45f
    val status = marketEntryStatus(entry, installState, installedVersion)
    val progress = (installState as? MarketInstallState.Installing)?.progress
    val sizeText = entry.artifact?.let { formatMarketSize(it.size) }
    val metaRows = mutableListOf<Pair<String, String>>()
    metaRows += stringResource(R.string.market_detail_version) to entry.version
    if (sizeText != null) {
        metaRows += stringResource(R.string.market_detail_size) to sizeText
    }
    val plugin = entry.plugin
    if (entry.type == MarketEntryType.Plugin && plugin != null) {
        val publisher = plugin.publisherKeyId
        if (!publisher.isNullOrBlank()) {
            metaRows += stringResource(R.string.market_detail_publisher) to publisher
        }
    }
    val skill = entry.skill
    if (entry.type == MarketEntryType.Skill && skill != null) {
        val license = skill.license
        if (!license.isNullOrBlank()) {
            metaRows += stringResource(R.string.market_detail_license) to license
        }
        val source = skill.source
        if (!source.isNullOrBlank()) {
            metaRows += stringResource(R.string.market_detail_source) to source
        }
    }
    val mcp = entry.mcp
    if (entry.type == MarketEntryType.Mcp && mcp != null) {
        metaRows += stringResource(R.string.market_detail_endpoint) to mcp.url
        if (mcp.headers.isNotEmpty()) {
            metaRows += stringResource(R.string.market_detail_headers) to
                mcp.headers.entries.joinToString("\n") { header -> "${header.key}: ${header.value}" }
        }
        val authHint = mcp.authHint
        if (!authHint.isNullOrBlank()) {
            metaRows += stringResource(R.string.market_detail_auth) to authHint
        }
    }

    DdSheet(onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarketEntryIcon(entry = entry, loader = iconLoader, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(entry.version.takeIf { it.isNotBlank() }, sizeText).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }
            }
            DdIconButton(
                icon = DdIcons.Close,
                onClick = onDismiss,
                contentDescription = stringResource(R.string.market_cancel),
                tint = colors.inkSecondary,
                iconSize = 18.dp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxBodyHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .testTag("${testTagPrefix}_market_detail"),
        ) {
            if (entry.summary.isNotBlank()) {
                Text(
                    text = entry.summary,
                    style = DdType.body,
                    color = colors.inkSecondary,
                )
            }
            if (entry.description.isNotBlank() && entry.description != entry.summary) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = entry.description,
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
            metaRows.forEach { (label, value) ->
                MarketMetaRow(label = label, value = value)
            }
            if (entry.type == MarketEntryType.Mcp) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.market_mcp_notice),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
            }
            if (entry.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MarketMetaRow(
                    label = stringResource(R.string.market_detail_tags),
                    value = entry.tags.joinToString(" · "),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            val failed = installState as? MarketInstallState.Failed
            if (failed != null) {
                Text(
                    text = failed.message,
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
            }
            DdPrimaryButton(
                text = statusLabel(status, progress),
                onClick = onInstall,
                modifier = Modifier.fillMaxWidth(),
                enabled = status == MarketEntryStatus.Install ||
                    status == MarketEntryStatus.Update ||
                    status == MarketEntryStatus.Failed,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MarketMetaRow(label: String, value: String) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.inkSecondary,
        )
    }
}
