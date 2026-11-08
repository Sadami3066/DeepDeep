package io.github.whywood.deepdeep.ui.miniapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.MiniappCreation
import io.github.whywood.deepdeep.runtime.MiniappInvalid
import io.github.whywood.deepdeep.runtime.MiniappStore
import kotlinx.coroutines.launch

private const val ICON_FALLBACK = "✦"

@Composable
fun MiniappScreen(
    store: MiniappStore,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val holder = remember(store) { MiniappStateHolder(store, scope) }
    LaunchedEffect(holder) { holder.load() }
    val state by holder.state.collectAsStateWithLifecycle()

    MiniappContent(
        state = state,
        onBack = onBack,
        onOpen = onOpen,
        onCreate = onCreate,
        onQueryChange = holder::setQuery,
        onRefresh = { scope.launch { holder.refresh() } },
        onRetry = holder::retry,
        onDelete = { id -> scope.launch { holder.delete(id) } },
        modifier = modifier,
    )
}

@Composable
internal fun MiniappContent(
    state: MiniappUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    var invalidTarget by remember { mutableStateOf<MiniappInvalid?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("miniapp_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.miniapp_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(DdSpacing.Xs))
            Text(
                text = stringResource(R.string.miniapp_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            DdIconButton(
                icon = DdIcons.Refresh,
                onClick = onRefresh,
                contentDescription = stringResource(R.string.miniapp_refresh),
                modifier = Modifier.testTag("miniapp_refresh"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            DdIconButton(
                icon = DdIcons.Plus,
                onClick = onCreate,
                contentDescription = stringResource(R.string.miniapp_new),
                modifier = Modifier.testTag("miniapp_new"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        DdSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.miniapp_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Sm),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "miniapp_search_field",
            clearTestTag = "miniapp_search_clear",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DdSpacing.Lg),
        ) {
            state.error?.let { error ->
                MiniappErrorRow(
                    text = stringResource(error.messageRes),
                    onRetry = if (error.retryable) onRetry else null,
                )
            }
            when {
                state.loading && state.isEmpty -> Unit

                state.error != null && state.isEmpty -> Unit

                state.isEmpty -> MiniappEmpty()

                state.filtered.creations.isEmpty() && state.filtered.invalid.isEmpty() -> Box(
                    Modifier.fillMaxWidth().padding(top = 72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_no_match),
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                    )
                }

                else -> {
                    Spacer(Modifier.height(DdSpacing.Sm))
                    var first = true
                    state.filtered.creations.forEach { creation ->
                        if (!first) DdHairline()
                        first = false
                        MiniappAppRow(
                            creation = creation,
                            onClick = { onOpen(creation.manifest.id) },
                        )
                    }
                    state.filtered.invalid.forEach { item ->
                        if (!first) DdHairline()
                        first = false
                        MiniappRow(
                            title = item.id,
                            subtitle = invalidSubtitle(item),
                            icon = null,
                            showChevron = false,
                            onClick = { invalidTarget = item },
                            modifier = Modifier.testTag("miniapp_invalid_${item.id}"),
                        )
                    }
                    Spacer(Modifier.height(DdSpacing.Xxxl))
                }
            }
            Spacer(Modifier.height(DdSpacing.Xxl))
        }
    }

    invalidTarget?.let { target ->
        MiniappInvalidDialog(
            id = target.id,
            reasonText = stringResource(invalidReasonLabelRes(target.reason)),
            onDismiss = { invalidTarget = null },
            onDelete = {
                invalidTarget = null
                onDelete(target.id)
            },
        )
    }
}

@Composable
private fun MiniappErrorRow(
    text: String,
    onRetry: (() -> Unit)? = null,
) {
    val colors = deepDeepColors()
    Row(
        Modifier.fillMaxWidth().padding(top = DdSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.danger,
        )
        if (onRetry != null) {
            DdTextAction(
                text = stringResource(R.string.miniapp_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("miniapp_retry"),
            )
        }
    }
}

@Composable
private fun MiniappEmpty() {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Apps, tint = colors.inkTertiary, size = 28.dp)
        Spacer(Modifier.height(DdSpacing.Lg))
        Text(
            text = stringResource(R.string.miniapp_empty_title),
            style = DdType.label,
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.miniapp_empty_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MiniappAppRow(
    creation: MiniappCreation,
    onClick: () -> Unit,
) {
    val manifest = creation.manifest
    MiniappRow(
        title = manifest.name,
        subtitle = manifest.description.trim().ifBlank { null },
        icon = manifest.icon.ifBlank { ICON_FALLBACK },
        showChevron = true,
        onClick = onClick,
        modifier = Modifier.testTag("miniapp_row_${manifest.id}"),
    )
}

@Composable
private fun MiniappRow(
    title: String,
    subtitle: String?,
    icon: String?,
    showChevron: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = DdShapes.Row,
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(vertical = DdSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Text(text = icon, fontSize = 20.sp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary, size = 16.dp)
            }
        }
    }
}

@Composable
private fun invalidSubtitle(item: MiniappInvalid): String =
    "${stringResource(R.string.miniapp_invalid)} · ${stringResource(invalidReasonLabelRes(item.reason))}"

internal data class MiniappFilterResult(
    val creations: List<MiniappCreation>,
    val invalid: List<MiniappInvalid>,
)

internal fun filterMiniapp(
    creations: List<MiniappCreation>,
    invalid: List<MiniappInvalid>,
    query: String,
): MiniappFilterResult {
    val normalized = query.trim()
    if (normalized.isEmpty()) return MiniappFilterResult(creations, invalid)
    return MiniappFilterResult(
        creations = creations.filter { creation ->
            val manifest = creation.manifest
            manifest.name.contains(normalized, ignoreCase = true) ||
                manifest.id.contains(normalized, ignoreCase = true) ||
                manifest.description.contains(normalized, ignoreCase = true)
        },
        invalid = invalid.filter { it.id.contains(normalized, ignoreCase = true) },
    )
}
