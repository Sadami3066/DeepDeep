package io.github.whywood.deepdeep.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSectionLabel
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.chat.SessionSearchHit
import io.github.whywood.deepdeep.ui.common.rememberRelativeTime
import kotlinx.coroutines.delay

private const val SearchDebounceMillis = 130L
private const val SearchResultLimit = 100

@Composable
fun SessionSearchScreen(
    onBack: () -> Unit,
    loadSessions: suspend () -> List<SessionSearchHit>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var loading by remember { mutableStateOf(true) }
    var sessions by remember { mutableStateOf<List<SessionSearchHit>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        sessions = runCatching { loadSessions() }.getOrElse { emptyList() }
        loading = false
    }

    LaunchedEffect(loading) {
        if (!loading) revealed = true
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(query) {
        if (query != debouncedQuery) {
            delay(SearchDebounceMillis)
            debouncedQuery = query
        }
    }

    val ordered = remember(sessions, debouncedQuery) {
        val sorted = sessions.sortedByDescending { hit -> hit.conversation.updatedAt }
        val matched = filterSessionSearch(sorted, debouncedQuery)
        if (debouncedQuery.isBlank()) matched.take(SearchResultLimit) else matched
    }
    val groups = remember(ordered) {
        ordered.groupBy { hit -> hit.workspaceName }.toList()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("session_search"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.search_back),
                modifier = Modifier.testTag("search_back"),
                tint = colors.ink,
                iconSize = 21.dp,
            )
            Spacer(Modifier.width(4.dp))
            DdSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.search_placeholder),
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                focusRequester = focusRequester,
                clearContentDescription = stringResource(R.string.search_clear),
                onEscape = onBack,
                fieldTestTag = "search_field",
                clearTestTag = "search_clear",
            )
        }
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DdSpinner(
                    modifier = Modifier.testTag("search_loading"),
                    color = colors.inkTertiary,
                    size = 18.dp,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            AnimatedVisibility(
                visible = revealed,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enter = fadeIn(tween(DdMotion.FAST)) +
                    slideInVertically(
                        animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                        initialOffsetY = { it / 24 },
                    ),
            ) {
                if (ordered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.search_empty),
                            modifier = Modifier.testTag("search_empty"),
                            style = DdType.bodySmall,
                            color = colors.inkTertiary,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 18.dp),
                    ) {
                        groups.forEach { (workspaceName, hits) ->
                            item(key = "workspace_$workspaceName") {
                                DdSectionLabel(
                                    text = workspaceName,
                                    modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                                )
                            }
                            items(hits, key = { hit -> hit.conversation.id }) { hit ->
                                SearchSessionRow(
                                    hit = hit,
                                    onClick = {
                                        keyboard?.hide()
                                        onOpen(hit.conversation.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSessionRow(
    hit: SessionSearchHit,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = if (pressed) snap() else tween(DdMotion.PRESS_OUT, easing = DdMotion.EaseOut),
        label = "search-row-wash",
    )
    val relativeTime = rememberRelativeTime(hit.conversation.updatedAt)
    val subtitle = when {
        hit.workspaceName.isBlank() -> relativeTime
        relativeTime.isBlank() -> hit.workspaceName
        else -> "${hit.workspaceName} · $relativeTime"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.ink.copy(alpha = wash))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .testTag("search_result_${hit.conversation.id}")
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = hit.conversation.title,
            style = DdType.body.copy(fontSize = 16.sp, lineHeight = 23.sp),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.inkTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
