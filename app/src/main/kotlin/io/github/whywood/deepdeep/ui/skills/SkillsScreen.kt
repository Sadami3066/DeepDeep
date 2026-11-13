package io.github.whywood.deepdeep.ui.skills

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.ui.common.rememberHapticFeedback
import io.github.whywood.deepdeep.data.InstalledSkill
import io.github.whywood.deepdeep.data.SkillArchiveResult
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdRadius
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSegmented
import io.github.whywood.deepdeep.design.DdShapes
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdSwitch
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.LocalDdRowHorizontalPadding
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.market.MarketEntryType
import io.github.whywood.deepdeep.market.MarketRepository
import io.github.whywood.deepdeep.ui.common.market.MARKET_TAB_MARKET
import io.github.whywood.deepdeep.ui.common.DelayedLoadingBlock
import io.github.whywood.deepdeep.ui.common.market.MarketRefreshButton
import io.github.whywood.deepdeep.ui.common.market.MarketTab
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024

private sealed interface ArchiveRead {
    data class Success(val bytes: ByteArray, val fallbackName: String) : ArchiveRead
    data class Failure(@StringRes val messageRes: Int) : ArchiveRead
}

@Composable
fun SkillsScreen(
    repository: SkillRepository,
    marketRepository: MarketRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val installSuccessTemplate = stringResource(R.string.skills_install_success)
    val holder = remember(repository) { SkillsStateHolder(repository, scope) }
    val state by holder.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MARKET_TAB_MARKET) }
    var marketQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(holder) {
        holder.start()
    }
    LifecycleResumeEffect(holder) {
        holder.resume()
        onPauseOrDispose { }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val read = readArchive(context, uri)) {
                    is ArchiveRead.Failure -> {
                        haptic(HapticFeedbackType.Reject)
                        holder.reportInstallError(
                            R.string.skills_install_failed_title,
                            read.messageRes,
                        )
                    }

                    is ArchiveRead.Success -> {
                        val result = holder.install(read.bytes, read.fallbackName)
                        if (result is SkillArchiveResult.Success) {
                            haptic(HapticFeedbackType.Confirm)
                            Toast.makeText(
                                context,
                                String.format(installSuccessTemplate, result.skill.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    SkillsContent(
        state = state,
        onBack = onBack,
        tab = tab,
        onTabSelect = { tab = it },
        marketQuery = marketQuery,
        onMarketQueryChange = { marketQuery = it },
        onQueryChange = holder::setQuery,
        onRetry = holder::retry,
        onToggle = holder::toggle,
        onDelete = holder::remove,
        onInstall = { picker.launch(arrayOf("*/*")) },
        onDismissInstallError = holder::dismissInstallError,
        readContent = holder::readContent,
        marketRepository = marketRepository,
        onMarketInstalled = { holder.refresh() },
        modifier = modifier,
    )
}

@Composable
private fun SkillsContent(
    state: SkillsUiState,
    onBack: () -> Unit,
    tab: Int,
    onTabSelect: (Int) -> Unit,
    marketQuery: String,
    onMarketQueryChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onToggle: (InstalledSkill, Boolean) -> Unit,
    onDelete: (InstalledSkill) -> Unit,
    onInstall: () -> Unit,
    onDismissInstallError: () -> Unit,
    readContent: suspend (String) -> String?,
    marketRepository: MarketRepository,
    onMarketInstalled: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val haptic = rememberHapticFeedback()
    var selected by remember { mutableStateOf<InstalledSkill?>(null) }
    var deleteTarget by remember { mutableStateOf<InstalledSkill?>(null) }
    val showMarket = tab == MARKET_TAB_MARKET

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("skills_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.skills_back),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(DdSpacing.Xs))
            Text(
                text = stringResource(R.string.skills_title),
                modifier = Modifier.weight(1f),
                style = DdType.heading,
                color = colors.ink,
            )
            if (!showMarket) {
                if (state.installing) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        DdSpinner(size = 20.dp)
                    }
                } else {
                    DdIconButton(
                        icon = DdIcons.Plus,
                        onClick = onInstall,
                        contentDescription = stringResource(R.string.skills_install),
                        modifier = Modifier.testTag("skills_install"),
                        tint = colors.ink,
                        iconSize = 21.dp,
                    )
                }
            } else {
                MarketRefreshButton(repository = marketRepository)
            }
        }
        DdSegmented(
            options = listOf(
                stringResource(R.string.market_tab_market),
                stringResource(R.string.market_tab_mine),
            ),
            selectedIndex = tab,
            onSelect = { index ->
                haptic(HapticFeedbackType.SegmentTick)
                onTabSelect(index)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DdSpacing.Lg, end = DdSpacing.Lg, top = DdSpacing.Xs),
            testTags = listOf("skills_tab_market", "skills_tab_mine"),
        )
        DdSearchField(
            query = if (showMarket) marketQuery else state.searchQuery,
            onQueryChange = if (showMarket) onMarketQueryChange else onQueryChange,
            placeholder = stringResource(R.string.skills_search_placeholder),
            modifier = Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Lg, vertical = DdSpacing.Sm),
            clearContentDescription = stringResource(R.string.search_clear),
            fieldTestTag = "skills_search_field",
            clearTestTag = "skills_search_clear",
        )
        if (showMarket) {
            MarketTab(
                repository = marketRepository,
                type = MarketEntryType.Skill,
                query = marketQuery,
                testTagPrefix = "skills",
                modifier = Modifier.fillMaxWidth().weight(1f),
                onInstalled = onMarketInstalled,
                showIcons = false,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = DdSpacing.Lg, end = DdSpacing.Lg, bottom = DdSpacing.Xxxl),
            ) {
                when {
                    state.loading && state.items.isEmpty() -> item {
                        DelayedLoadingBlock(
                            loading = true,
                            modifier = Modifier.padding(top = 72.dp),
                        )
                    }

                    state.items.isEmpty() && state.error != null -> item {
                        Box(
                            Modifier.fillMaxWidth().padding(top = 72.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            SkillsErrorRow(error = state.error, onRetry = onRetry)
                        }
                    }

                    state.items.isEmpty() -> item { EmptySkills(onInstall = onInstall) }

                    state.visibleItems.isEmpty() -> item {
                        DdEmptyState(
                            text = stringResource(R.string.search_no_match),
                            icon = DdIcons.Search,
                            modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
                        )
                    }

                    else -> {
                        if (state.error != null) {
                            item {
                                SkillsErrorRow(
                                    error = state.error,
                                    onRetry = onRetry,
                                    modifier = Modifier.padding(top = DdSpacing.Sm),
                                )
                            }
                        }
                        item { Spacer(Modifier.height(DdSpacing.Sm)) }
                        itemsIndexed(state.visibleItems, key = { _, skill -> skill.id }) { index, skill ->
                            SkillCardRow(
                                skill = skill,
                                first = index == 0,
                                last = index == state.visibleItems.lastIndex,
                                togglingIds = state.togglingIds,
                                onOpen = { selected = skill },
                                onToggle = { enabled -> onToggle(skill, enabled) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item { Spacer(Modifier.height(DdSpacing.Lg)) }
                    }
                }
            }
        }
    }

    selected?.let { skill ->
        SkillDetailSheet(
            skill = skill,
            readContent = readContent,
            deleteEnabled = skill.id !in state.togglingIds,
            onDismiss = { selected = null },
            onDelete = { deleteTarget = skill },
        )
    }

    deleteTarget?.let { skill ->
        DdDialog(
            onDismissRequest = { deleteTarget = null },
            modifier = Modifier.testTag("skill_delete_dialog"),
            title = stringResource(R.string.skills_delete_title),
            content = {
                Text(
                    text = stringResource(R.string.skills_delete_message, skill.name),
                    style = DdType.bodySmall,
                    color = deepDeepColors().inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.skills_cancel),
                    onClick = { deleteTarget = null },
                    color = deepDeepColors().inkSecondary,
                )
                DdTextAction(
                    text = stringResource(R.string.skills_delete_confirm),
                    onClick = {
                        haptic(HapticFeedbackType.Reject)
                        deleteTarget = null
                        if (selected?.id == skill.id) selected = null
                        onDelete(skill)
                    },
                    modifier = Modifier.testTag("skill_delete_confirm"),
                    color = deepDeepColors().danger,
                )
            },
        )
    }

    state.installError?.let { current ->
        DdDialog(
            onDismissRequest = onDismissInstallError,
            modifier = Modifier.testTag("skills_error_dialog"),
            title = stringResource(current.titleRes),
            content = {
                Text(
                    text = stringResource(current.messageRes),
                    style = DdType.bodySmall,
                    color = deepDeepColors().inkSecondary,
                )
            },
            actions = {
                DdTextAction(
                    text = stringResource(R.string.skills_close),
                    onClick = onDismissInstallError,
                    modifier = Modifier.testTag("skills_error_close"),
                )
            },
        )
    }
}

@Composable
private fun SkillsErrorRow(
    error: SkillsError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stringResource(error.titleRes)} · ${stringResource(error.messageRes)}",
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = colors.danger,
        )
        if (error.retryable) {
            DdTextAction(
                text = stringResource(R.string.skills_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("skills_list_retry"),
            )
        }
    }
}

@Composable
private fun EmptySkills(onInstall: () -> Unit) {
    DdEmptyState(
        text = stringResource(R.string.skills_empty_title),
        icon = DdIcons.Sparkles,
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        action = {
            DdTextAction(
                text = stringResource(R.string.skills_install),
                onClick = onInstall,
                modifier = Modifier.testTag("skills_empty_install"),
            )
        },
    )
}

@Composable
private fun SkillCardRow(
    skill: InstalledSkill,
    first: Boolean,
    last: Boolean,
    togglingIds: Set<String>,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val shape = when {
        first && last -> DdShapes.Row
        first -> RoundedCornerShape(topStart = DdRadius.Row, topEnd = DdRadius.Row)
        last -> RoundedCornerShape(bottomStart = DdRadius.Row, bottomEnd = DdRadius.Row)
        else -> RoundedCornerShape(0.dp)
    }
    Column(modifier) {
        if (!first) DdHairline()
        CompositionLocalProvider(LocalDdRowHorizontalPadding provides 16.dp) {
            DdListRow(
                title = skill.name,
                subtitle = skill.description.takeIf { it.isNotBlank() },
                subtitleMaxLines = 1,
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface)
                    .heightIn(min = 64.dp)
                    .testTag("skill_${skill.id}"),
                trailing = {
                    DdSwitch(
                        checked = skill.globalEnabled,
                        onCheckedChange = onToggle,
                        enabled = skill.id !in togglingIds,
                        stateDescription = stringResource(
                            if (skill.globalEnabled) {
                                R.string.skills_global_enabled
                            } else {
                                R.string.skills_global_disabled
                            },
                        ),
                        modifier = Modifier.testTag("skill_global_${skill.id}"),
                    )
                },
            )
        }
    }
}

@Composable
private fun ManualTag() {
    val colors = deepDeepColors()
    Text(
        text = stringResource(R.string.skills_manual),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.wash)
            .padding(horizontal = 6.dp, vertical = DdSpacing.Xxs),
        style = DdType.chip,
        color = colors.inkTertiary,
        maxLines = 1,
    )
}

@Composable
private fun SkillDetailSheet(
    skill: InstalledSkill,
    readContent: suspend (String) -> String?,
    deleteEnabled: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = deepDeepColors()
    var content by remember(skill.id) { mutableStateOf<String?>(null) }
    var loading by remember(skill.id) { mutableStateOf(true) }
    LaunchedEffect(skill.id) {
        content = readContent(skill.id)
        loading = false
    }
    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * 0.66f).dp
    DdSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().testTag("skill_detail")) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = DdSpacing.Xxl, end = DdSpacing.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = skill.name,
                    modifier = Modifier.weight(1f),
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DdIconButton(
                    icon = DdIcons.Close,
                    onClick = onDismiss,
                    contentDescription = stringResource(R.string.skills_close),
                    tint = colors.inkSecondary,
                    iconSize = 20.dp,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DdSpacing.Xxl)
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (skill.description.isNotBlank()) {
                    Text(
                        text = skill.description,
                        style = DdType.bodySmall,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(DdSpacing.Lg))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.skills_detail_status),
                        style = DdType.label,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            if (skill.globalEnabled) {
                                R.string.skills_detail_enabled
                            } else {
                                R.string.skills_detail_disabled
                            },
                        ),
                        style = DdType.bodySmall,
                        color = if (skill.globalEnabled) colors.accent else colors.inkTertiary,
                    )
                    if (skill.disableModelInvocation) {
                        Spacer(Modifier.width(10.dp))
                        ManualTag()
                    }
                }
                Spacer(Modifier.height(DdSpacing.Xl))
                Text(
                    text = stringResource(R.string.skills_detail_content),
                    style = DdType.micro,
                    color = colors.inkTertiary,
                )
                Spacer(Modifier.height(DdSpacing.Sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(DdShapes.Medium)
                        .background(colors.wash)
                        .padding(DdSpacing.Md),
                ) {
                    when {
                        loading -> DdSpinner(size = 18.dp)
                        content.isNullOrBlank() -> Text(
                            text = stringResource(R.string.skills_detail_empty),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )

                        else -> Text(
                            text = content.orEmpty(),
                            style = DdType.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            ),
                            color = colors.inkSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(DdSpacing.Xl))
                DdTextAction(
                    text = stringResource(R.string.skills_delete_confirm),
                    onClick = onDelete,
                    enabled = deleteEnabled,
                    modifier = Modifier.testTag("skill_delete"),
                    color = colors.danger,
                )
                Spacer(Modifier.height(DdSpacing.Xxl))
            }
        }
    }
}

private suspend fun readArchive(context: Context, uri: Uri): ArchiveRead = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var displayName: String? = null
    var size: Long? = null
    runCatching {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    val declaredSize = size
    if (declaredSize != null && declaredSize > MAX_ARCHIVE_BYTES) {
        return@withContext ArchiveRead.Failure(R.string.skills_install_too_large)
    }
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0
            while (total <= MAX_ARCHIVE_BYTES) {
                val read = stream.read(buffer, 0, minOf(buffer.size, MAX_ARCHIVE_BYTES + 1 - total))
                if (read < 0) break
                output.write(buffer, 0, read)
                total += read
            }
            output.toByteArray()
        }
    }.getOrNull() ?: return@withContext ArchiveRead.Failure(R.string.skills_install_unreadable)
    if (bytes.size > MAX_ARCHIVE_BYTES) {
        return@withContext ArchiveRead.Failure(R.string.skills_install_too_large)
    }
    val name = displayName
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        ?: "skill"
    ArchiveRead.Success(bytes, name)
}
