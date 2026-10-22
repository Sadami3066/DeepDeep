package io.github.whywood.deepdeep.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ProviderSummary

@Composable
fun ProviderListScreen(
    providers: List<ProviderSummary>,
    configuredProviderIds: Set<String>,
    activeProviderId: String?,
    onBack: () -> Unit,
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
    val countLabel = stringResource(
        if (query.isBlank()) {
            R.string.settings_provider_all_count
        } else {
            R.string.settings_provider_result_count
        },
        filtered.size,
    )

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_provider_title),
                style = DdType.pageTitle,
                color = colors.ink,
            )
            Spacer(Modifier.height(16.dp))
            SettingsTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.settings_provider_search),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(16.dp))
            DdSectionLabel(countLabel, Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(10.dp))
            SettingsGroup {
                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_provider_no_result),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                } else {
                    filtered.forEachIndexed { index, provider ->
                        ProviderRow(
                            provider = provider,
                            configured = provider.id in configuredProviderIds,
                            active = provider.id == activeProviderId,
                            onClick = { onProviderClick(provider.id) },
                        )
                        if (index != filtered.lastIndex) DdHairline()
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ProviderRow(
    provider: ProviderSummary,
    configured: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = tween(DdMotion.PRESS, easing = DdMotion.EaseOut),
        label = "provider-row-wash",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.ink.copy(alpha = wash))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialBadge(provider.name)
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
            active -> StatusPill(stringResource(R.string.settings_provider_current), active = true)
            configured -> StatusPill(stringResource(R.string.settings_status_configured), active = false)
        }
        Spacer(Modifier.width(6.dp))
        DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 16.dp)
    }
}
