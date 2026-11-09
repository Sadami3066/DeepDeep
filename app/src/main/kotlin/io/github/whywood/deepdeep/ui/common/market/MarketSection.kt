package io.github.whywood.deepdeep.ui.common.market

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdLoadingBlock
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.market.MarketEntry
import io.github.whywood.deepdeep.market.MarketEntryType
import io.github.whywood.deepdeep.market.MarketInstallState
import io.github.whywood.deepdeep.market.MarketMcpAuthType
import io.github.whywood.deepdeep.market.MarketPluginMeta
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.market.MarketSource
import io.github.whywood.deepdeep.market.MarketState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val MARKET_TAB_MARKET = 0

internal const val MARKET_TAB_MINE = 1

private val marketInstallScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

@Composable
internal fun MarketTab(
    repository: MarketRepository,
    type: MarketEntryType,
    query: String,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    onInstalled: suspend () -> Unit = {},
    onAuthorizeMcp: (String) -> Unit = {},
    showIcons: Boolean = true,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val language = LocalConfiguration.current.locales[0].language
    val state by repository.state.collectAsStateWithLifecycle()
    val installStates by repository.installStates.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val iconLoader = rememberMarketIconLoader()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }
    var tokenId by rememberSaveable { mutableStateOf<String?>(null) }
    var retrying by rememberSaveable { mutableStateOf(false) }

    val current = state
    val entries = if (current is MarketState.Ready) repository.entries(type) else emptyList()
    val visible = filterMarketEntries(entries, query, language)
    val selected = entries.firstOrNull { it.id == selectedId }
    val confirm = entries.firstOrNull { it.id == confirmId }
    val tokenEntry = entries.firstOrNull { it.id == tokenId }

    fun install(entry: MarketEntry, apiKey: String? = null) {
        marketInstallScope.launch {
            repository.install(entry, apiKey).onSuccess {
                onInstalled()
                when {
                    apiKey != null -> tokenId = null
                    entry.mcp?.auth?.type == MarketMcpAuthType.OAuth -> {
                        selectedId = null
                        confirmId = null
                        Toast.makeText(context, R.string.market_oauth_hint, Toast.LENGTH_SHORT).show()
                        onAuthorizeMcp(entry.id)
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("${testTagPrefix}_market"),
        contentPadding = PaddingValues(bottom = DdSpacing.Xxl),
    ) {
        when (current) {
            MarketState.Loading -> item {
                DdLoadingBlock(
                    modifier = Modifier.padding(top = DdSpacing.Huge + DdSpacing.Sm),
                    text = stringResource(R.string.market_loading),
                )
            }

            is MarketState.Failed -> item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = DdSpacing.Huge + DdSpacing.Lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (retrying) {
                            DdLoadingBlock(text = stringResource(R.string.market_loading))
                        } else {
                            Text(
                                text = current.message,
                                style = DdType.bodySmall,
                                color = colors.inkSecondary,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(DdSpacing.Sm))
                            DdTextAction(
                                text = stringResource(R.string.market_retry),
                                onClick = {
                                    if (retrying) return@DdTextAction
                                    retrying = true
                                    scope.launch {
                                        try {
                                            repository.refresh(force = true)
                                        } finally {
                                            retrying = false
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("${testTagPrefix}_market_retry"),
                            )
                        }
                    }
                }
            }

            is MarketState.Ready -> {
                if (current.source != MarketSource.Network) {
                    item {
                        Text(
                            text = stringResource(R.string.market_offline_notice),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Sm)
                                .testTag("${testTagPrefix}_market_offline"),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                }
                when {
                    entries.isEmpty() -> item {
                        DdEmptyState(
                            text = stringResource(R.string.market_empty),
                            icon = marketPlaceholderIcon(type),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg),
                        )
                    }

                    visible.isEmpty() -> item {
                        DdEmptyState(
                            text = stringResource(R.string.search_no_match),
                            icon = DdIcons.Search,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg),
                        )
                    }

                    else -> itemsIndexed(visible, key = { _, entry -> entry.id }) { index, entry ->
                        Column(Modifier.padding(horizontal = DdSpacing.Lg)) {
                            if (index > 0) DdHairline(Modifier.padding(start = DdSpacing.Xl))
                            val installState = installStates[entry.id]
                            MarketRow(
                                entry = entry,
                                language = language,
                                status = marketEntryStatus(entry, installState, repository.installedVersion(entry)),
                                progress = (installState as? MarketInstallState.Installing)?.progress,
                                iconLoader = iconLoader,
                                showIcons = showIcons,
                                onClick = { selectedId = entry.id },
                                modifier = Modifier.testTag("${testTagPrefix}_market_row_${entry.id}"),
                            )
                        }
                    }
                }
            }
        }
    }

    if (selected != null) {
        MarketDetailSheet(
            entry = selected,
            language = language,
            installState = installStates[selected.id],
            installedVersion = repository.installedVersion(selected),
            iconLoader = iconLoader,
            onDismiss = { selectedId = null },
            onInstall = {
                when {
                    selected.type != MarketEntryType.Mcp -> install(selected)
                    selected.mcp?.auth?.type == MarketMcpAuthType.ApiKey -> {
                        confirmId = null
                        tokenId = selected.id
                    }
                    else -> confirmId = selected.id
                }
            },
            onAuthorize = {
                selectedId = null
                onAuthorizeMcp(selected.id)
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
                    text = stringResource(R.string.market_mcp_confirm_message, confirm.name(language)),
                    style = DdType.body,
                    color = colors.inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.market_cancel),
                    onClick = { confirmId = null },
                    color = colors.inkSecondary,
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

    if (tokenEntry != null) {
        val auth = tokenEntry.mcp?.auth
        val hint = tokenEntry.mcp?.authHint?.resolve(language)
        val failed = installStates[tokenEntry.id] as? MarketInstallState.Failed
        var token by rememberSaveable(tokenEntry.id) { mutableStateOf("") }
        DdDialog(
            onDismissRequest = { tokenId = null },
            title = stringResource(R.string.market_auth_token_title),
            content = {
                Text(
                    text = stringResource(R.string.market_mcp_confirm_message, tokenEntry.name(language)),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(12.dp))
                DdTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = auth?.placeholder?.resolve(language)?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.market_auth_token_placeholder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${testTagPrefix}_market_token_field"),
                )
                if (!hint.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = hint, style = DdType.bodySmall, color = colors.inkTertiary)
                }
                val helpUrl = auth?.helpUrl
                if (!helpUrl.isNullOrBlank()) {
                    MarketLinkAction(
                        text = stringResource(R.string.market_auth_help),
                        onClick = { openMarketUrl(context, helpUrl) },
                        modifier = Modifier.testTag("${testTagPrefix}_market_token_help"),
                    )
                }
                if (failed != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = failed.message,
                        style = DdType.bodySmall,
                        color = colors.danger,
                    )
                }
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.market_cancel),
                    onClick = { tokenId = null },
                    color = colors.inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.market_install),
                    onClick = { install(tokenEntry, token) },
                    enabled = token.isNotBlank(),
                )
            },
        )
    }
}

@Composable
private fun MarketRow(
    entry: MarketEntry,
    language: String,
    status: MarketEntryStatus,
    progress: Int?,
    iconLoader: MarketIconLoader,
    showIcons: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val name = entry.name(language)
    val publisher = if (entry.type == MarketEntryType.Plugin) marketPublisherDisplay(entry.plugin) else null
    DdPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentDescription = name,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcons) {
                MarketEntryIcon(entry = entry, loader = iconLoader)
                Spacer(Modifier.width(DdSpacing.Md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = DdType.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(entry.summary(language).takeIf { it.isNotBlank() }, publisher).joinToString(" · "),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

private fun marketPublisherDisplay(plugin: MarketPluginMeta?): String? {
    plugin ?: return null
    return plugin.publisherName?.takeIf { it.isNotBlank() }
        ?: plugin.publisherKeyId?.takeIf { it.isNotBlank() }?.take(12)
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
    language: String,
    installState: MarketInstallState?,
    installedVersion: String?,
    iconLoader: MarketIconLoader,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onAuthorize: () -> Unit,
    testTagPrefix: String,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val maxBodyHeight = configuration.screenHeightDp.dp * 0.45f
    val status = marketEntryStatus(entry, installState, installedVersion)
    val progress = (installState as? MarketInstallState.Installing)?.progress
    val sizeText = entry.artifact?.let { formatMarketSize(it.size) }
    val mcp = entry.mcp
    val metaRows = mutableListOf<Pair<String, String>>()
    if (entry.type != MarketEntryType.Mcp) {
        if (entry.version.isNotBlank()) {
            metaRows += stringResource(R.string.market_detail_version) to entry.version
        }
        if (sizeText != null) {
            metaRows += stringResource(R.string.market_detail_size) to sizeText
        }
        val plugin = entry.plugin
        if (entry.type == MarketEntryType.Plugin && plugin != null) {
            val publisher = marketPublisherDisplay(plugin)
            if (publisher != null) {
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
    } else if (mcp != null) {
        metaRows += stringResource(R.string.market_detail_endpoint) to mcp.url
        if (mcp.headers.isNotEmpty()) {
            metaRows += stringResource(R.string.market_detail_headers) to
                mcp.headers.entries.joinToString("\n") { header -> "${header.key}: ${header.value}" }
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
            Spacer(Modifier.width(DdSpacing.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name(language),
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = if (entry.type == MarketEntryType.Mcp) {
                    stringResource(R.string.market_type_mcp)
                } else {
                    listOfNotNull(entry.version.takeIf { it.isNotBlank() }, sizeText).joinToString(" · ")
                }
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
            var previewViewerUrl by rememberSaveable { mutableStateOf<String?>(null) }
            DdSectionLabel(stringResource(R.string.market_detail_about))
            val summary = entry.summary(language)
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = DdType.body,
                    color = colors.inkSecondary,
                )
            }
            val description = entry.description(language)
            if (description.isNotBlank() && description != summary) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
                )
            }
            if (metaRows.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                DdSectionLabel(stringResource(R.string.market_detail_info))
                metaRows.forEach { (label, value) ->
                    MarketMetaRow(label = label, value = value)
                }
            }
            val previews = entry.plugin?.previewUrls.orEmpty()
            if (previews.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                DdSectionLabel(stringResource(R.string.market_detail_previews))
                Spacer(Modifier.height(DdSpacing.Sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DdSpacing.Sm),
                ) {
                    previews.forEach { url ->
                        MarketPreviewCard(
                            url = url,
                            modifier = Modifier
                                .width(150.dp)
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            onClick = { previewViewerUrl = url },
                        )
                    }
                }
            }
            previewViewerUrl?.let { url ->
                MarketPreviewViewer(url = url, onDismiss = { previewViewerUrl = null })
            }
            if (entry.type == MarketEntryType.Mcp && mcp != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.market_mcp_notice),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                )
                val auth = mcp.auth
                val authHint = mcp.authHint?.resolve(language)
                if ((auth != null && auth.type != MarketMcpAuthType.None) || !authHint.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    DdSectionLabel(stringResource(R.string.market_detail_auth))
                    when (auth?.type) {
                        MarketMcpAuthType.ApiKey -> Text(
                            text = stringResource(R.string.market_auth_type_apikey),
                            style = DdType.bodySmall,
                            color = colors.inkSecondary,
                        )

                        MarketMcpAuthType.OAuth -> Text(
                            text = stringResource(R.string.market_auth_type_oauth),
                            style = DdType.bodySmall,
                            color = colors.inkSecondary,
                        )

                        MarketMcpAuthType.None, null -> Unit
                    }
                    if (!authHint.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = authHint,
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                    val helpUrl = auth?.helpUrl
                    if (!helpUrl.isNullOrBlank()) {
                        MarketLinkAction(
                            text = stringResource(R.string.market_auth_help),
                            onClick = { openMarketUrl(context, helpUrl) },
                        )
                    }
                    if (auth?.type == MarketMcpAuthType.OAuth && status == MarketEntryStatus.Installed) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.market_oauth_hint),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                        MarketLinkAction(
                            text = stringResource(R.string.market_oauth_action),
                            onClick = onAuthorize,
                            modifier = Modifier.testTag("${testTagPrefix}_market_authorize"),
                        )
                    }
                }
            }
            val tags = entry.tags(language)
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                DdSectionLabel(stringResource(R.string.market_detail_tags))
                Text(
                    text = tags.joinToString(" · "),
                    style = DdType.bodySmall,
                    color = colors.inkSecondary,
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

@Composable
private fun MarketLinkAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = DdType.label, color = colors.accent)
            Spacer(Modifier.width(4.dp))
            DdIcon(icon = DdIcons.External, tint = colors.accent, size = 14.dp)
        }
    }
}

private fun openMarketUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
internal fun MarketRefreshButton(
    repository: MarketRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    if (refreshing) {
        Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
            DdSpinner()
        }
    } else {
        DdIconButton(
            icon = DdIcons.Refresh,
            onClick = {
                scope.launch {
                    refreshing = true
                    try {
                        repository.refresh(force = true)
                    } finally {
                        refreshing = false
                    }
                }
            },
            contentDescription = stringResource(R.string.market_refresh),
            modifier = modifier.testTag("market_refresh"),
            iconSize = 21.dp,
        )
    }
}
