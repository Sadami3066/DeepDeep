package io.github.whywood.deepdeep.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

private val DdListPageTopBarHeight = DdSpacing.Huge + DdSpacing.Xxl

@Composable
fun DdListPageScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    tabs: (@Composable () -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    DdListPageChrome(
        title = title,
        onBack = onBack,
        modifier = modifier,
        subtitle = subtitle,
        actions = actions,
        tabs = tabs,
        search = search,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            content = content,
        )
    }
}

@Composable
fun DdListPageScaffoldColumn(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    tabs: (@Composable () -> Unit)? = null,
    search: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DdListPageChrome(
        title = title,
        onBack = onBack,
        modifier = modifier,
        subtitle = subtitle,
        actions = actions,
        tabs = tabs,
        search = search,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            content = content,
        )
    }
}

@Composable
private fun DdListPageChrome(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier,
    subtitle: String?,
    actions: (@Composable RowScope.() -> Unit)?,
    tabs: (@Composable () -> Unit)?,
    search: (@Composable () -> Unit)?,
    body: @Composable ColumnScope.() -> Unit,
) {
    val colors = deepDeepColors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DdListPageTopBarHeight)
                .padding(horizontal = DdSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                DdIconButton(
                    icon = DdIcons.ArrowLeft,
                    onClick = onBack,
                    contentDescription = title,
                    iconSize = DdSpacing.Xxl,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = DdSpacing.Sm),
            ) {
                DdText(
                    text = title,
                    style = DdType.heading,
                    color = colors.ink,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    DdText(
                        text = subtitle,
                        style = DdType.bodySmall,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
            if (actions != null) {
                actions()
            }
        }
        if (tabs != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DdSpacing.Lg),
            ) {
                tabs()
            }
        }
        if (search != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = DdSpacing.Lg,
                        vertical = DdSpacing.Sm,
                    ),
            ) {
                search()
            }
        }
        body()
    }
}
