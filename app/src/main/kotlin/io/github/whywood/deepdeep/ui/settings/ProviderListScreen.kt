package io.github.whywood.deepdeep.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdPageScaffold
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.ui.common.ProviderIcon

@Composable
fun ProviderListScreen(
    providers: List<ProviderSummary>,
    customProviderIds: Set<String>,
    configuredProviderIds: Set<String>,
    onBack: () -> Unit,
    onAddCustomProvider: () -> Unit,
    onProviderClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(providers, query) {
        val keyword = query.trim()
        providers
            .filter {
                keyword.isEmpty() ||
                    it.name.contains(keyword, ignoreCase = true) ||
                    it.id.contains(keyword, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase() }
    }
    val customProviders = filtered.filter { it.id in customProviderIds }
    val builtinProviders = filtered.filterNot { it.id in customProviderIds }
    val countLabel = stringResource(
        if (query.isBlank()) {
            R.string.settings_provider_all_count
        } else {
            R.string.settings_provider_result_count
        },
        filtered.size,
    )

    DdPageScaffold(
        topBar = {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        },
        modifier = modifier,
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.ScreenHorizontal),
        ) {
            Text(
                text = stringResource(R.string.settings_provider_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(DdSpacing.Lg))
            DdSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.settings_provider_search),
                clearContentDescription = stringResource(R.string.search_clear),
                fieldTestTag = "provider_search_field",
            )
            Spacer(Modifier.height(DdSpacing.Lg))
            DdSectionLabel(
                stringResource(R.string.settings_provider_custom_section),
                Modifier.padding(start = DdSpacing.Xs),
            )
            Spacer(Modifier.height(10.dp))
            DdGroupCard {
                DdListRow(
                    title = stringResource(R.string.settings_provider_add_custom),
                    icon = DdIcons.Plus,
                    onClick = onAddCustomProvider,
                    showChevron = true,
                )
                if (customProviders.isNotEmpty()) {
                    DdHairline()
                    customProviders.forEachIndexed { index, provider ->
                        ProviderRow(
                            provider = provider,
                            configured = provider.id in configuredProviderIds,
                            onClick = { onProviderClick(provider.id) },
                        )
                        if (index != customProviders.lastIndex) DdHairline()
                    }
                }
            }
            if (builtinProviders.isNotEmpty() || filtered.isEmpty()) {
                Spacer(Modifier.height(DdSpacing.Lg))
                DdSectionLabel(countLabel, Modifier.padding(start = DdSpacing.Xs))
                Spacer(Modifier.height(10.dp))
                DdGroupCard {
                    if (builtinProviders.isEmpty()) {
                        DdEmptyState(
                            text = stringResource(R.string.settings_provider_no_result),
                            modifier = Modifier.padding(vertical = DdSpacing.Xl),
                        )
                    } else {
                        builtinProviders.forEachIndexed { index, provider ->
                            ProviderRow(
                                provider = provider,
                                configured = provider.id in configuredProviderIds,
                                onClick = { onProviderClick(provider.id) },
                            )
                            if (index != builtinProviders.lastIndex) DdHairline()
                        }
                    }
                }
            }
            Spacer(Modifier.height(DdSpacing.Huge))
        }
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderSummary,
    configured: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DdShapes.Row,
        contentDescription = provider.name,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(provider.id, provider.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.settings_provider_models_count, provider.modelCount),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
            when {
                configured -> StatusPill(stringResource(R.string.settings_status_configured), active = false)
            }
            Spacer(Modifier.width(6.dp))
            DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 16.dp)
        }
    }
}
