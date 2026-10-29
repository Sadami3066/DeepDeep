package io.github.whywood.deepdeep.ui.workshop

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.WorkshopCreation
import io.github.whywood.deepdeep.runtime.WorkshopInvalid
import io.github.whywood.deepdeep.runtime.WorkshopStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ICON_FALLBACK = "✦"

@Composable
fun WorkshopScreen(
    store: WorkshopStore,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    val loadingLabel = stringResource(R.string.workshop_loading)
    var creations by remember { mutableStateOf<List<WorkshopCreation>>(emptyList()) }
    var invalid by remember { mutableStateOf<List<WorkshopInvalid>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var invalidTarget by remember { mutableStateOf<WorkshopInvalid?>(null) }

    LaunchedEffect(refreshKey) {
        loading = true
        val scan = withContext(Dispatchers.IO) { runCatching { store.scan() }.getOrNull() }
        if (scan != null) {
            creations = scan.creations
            invalid = scan.invalid
        }
        loading = false
    }

    fun delete(target: WorkshopInvalid) {
        invalidTarget = null
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.delete(target.id) } }
            refreshKey++
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("workshop_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.workshop_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.workshop_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (loading) {
                Box(Modifier.size(44.dp).testTag("workshop_refresh_spinner"), contentAlignment = Alignment.Center) {
                    DdSpinner(size = 18.dp, strokeWidth = 2.dp)
                }
            } else {
                DdIconButton(
                    icon = DdIcons.Refresh,
                    onClick = { refreshKey++ },
                    contentDescription = stringResource(R.string.workshop_refresh),
                    modifier = Modifier.testTag("workshop_refresh"),
                    tint = colors.ink,
                    iconSize = 21.dp,
                )
            }
            DdIconButton(
                icon = DdIcons.Plus,
                onClick = onCreate,
                contentDescription = stringResource(R.string.workshop_new),
                modifier = Modifier.testTag("workshop_new"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when {
                loading && creations.isEmpty() && invalid.isEmpty() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 120.dp)
                        .semantics { contentDescription = loadingLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    DdSpinner(size = 22.dp)
                }

                creations.isEmpty() && invalid.isEmpty() -> WorkshopEmpty()

                else -> {
                    Spacer(Modifier.height(8.dp))
                    var first = true
                    creations.forEach { creation ->
                        if (!first) DdHairline()
                        first = false
                        WorkshopAppRow(
                            creation = creation,
                            onClick = { onOpen(creation.manifest.id) },
                        )
                    }
                    invalid.forEach { item ->
                        if (!first) DdHairline()
                        first = false
                        WorkshopRow(
                            title = item.id,
                            subtitle = invalidSubtitle(item),
                            icon = null,
                            showChevron = false,
                            onClick = { invalidTarget = item },
                            modifier = Modifier.testTag("workshop_invalid_${item.id}"),
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    invalidTarget?.let { target ->
        WorkshopInvalidDialog(
            id = target.id,
            reasonText = stringResource(invalidReasonLabelRes(target.reason)),
            onDismiss = { invalidTarget = null },
            onDelete = { delete(target) },
        )
    }
}

@Composable
private fun WorkshopEmpty() {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Apps, tint = colors.inkTertiary, size = 28.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.workshop_empty_title),
            style = DdType.label,
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.workshop_empty_hint),
            style = DdType.bodySmall,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WorkshopAppRow(
    creation: WorkshopCreation,
    onClick: () -> Unit,
) {
    val manifest = creation.manifest
    WorkshopRow(
        title = manifest.name,
        subtitle = manifest.description.trim().ifBlank { null },
        icon = manifest.icon.ifBlank { ICON_FALLBACK },
        showChevron = true,
        onClick = onClick,
        modifier = Modifier.testTag("workshop_row_${manifest.id}"),
    )
}

@Composable
private fun WorkshopRow(
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
        shape = RoundedCornerShape(20.dp),
        contentDescription = title,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(vertical = 12.dp),
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
private fun invalidSubtitle(item: WorkshopInvalid): String =
    "${stringResource(R.string.workshop_invalid)} · ${stringResource(invalidReasonLabelRes(item.reason))}"
