package io.github.whywood.deepdeep.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSearchField
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.ProviderSummary
import io.github.whywood.deepdeep.ui.common.ProviderIcon

internal const val ONBOARDING_STEP_COUNT = 12

internal const val KEY_STATE_IDLE = 0
internal const val KEY_STATE_SAVING = 1
internal const val KEY_STATE_SAVED = 2
internal const val KEY_STATE_FAILED = 3

@Composable
internal fun OnboardingHeader(step: Int, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
        AnimatedVisibility(
            visible = step > 0,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(DdMotion.FAST, easing = DdMotion.EaseOut)),
            exit = fadeOut(tween(120)),
        ) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.onboarding_back),
                iconSize = 21.dp,
            )
        }
        AnimatedVisibility(
            visible = step > 0,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(DdMotion.BASE, easing = DdMotion.EaseOut)),
            exit = fadeOut(tween(120)),
        ) {
            StepProgress(current = step)
        }
    }
}

@Composable
private fun StepProgress(current: Int) {
    val colors = deepDeepColors()
    val description = stringResource(
        R.string.onboarding_progress_description,
        current + 1,
        ONBOARDING_STEP_COUNT,
    )
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in 0 until ONBOARDING_STEP_COUNT) {
            val color by animateColorAsState(
                targetValue = when {
                    index == current -> colors.accent
                    index < current -> colors.accent.copy(alpha = 0.35f)
                    else -> colors.hairline
                },
                animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                label = "onboarding-progress-color-$index",
            )
            Box(Modifier.width(8.dp).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
internal fun StepHeading(title: String, subtitle: String? = null, icon: DdIcon? = null) {
    val colors = deepDeepColors()
    if (icon == null) {
        Text(text = title, style = DdType.heading, color = colors.ink)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(colors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                DdIcon(icon = icon, tint = colors.accent, size = 22.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = DdType.heading, color = colors.ink)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
                }
            }
        }
    }
}

@Composable
internal fun SelectionCheck(visible: Boolean) {
    val colors = deepDeepColors()
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 520f)) +
            fadeIn(tween(DdMotion.FAST, easing = DdMotion.EaseOut)),
        exit = scaleOut(tween(120)) + fadeOut(tween(120)),
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            DdIcon(icon = DdIcons.Check, tint = colors.onAccent, size = 13.dp)
        }
    }
}

@Composable
internal fun GrantStatus(granted: Boolean) {
    val colors = deepDeepColors()
    val color by animateColorAsState(
        targetValue = if (granted) colors.success else colors.inkTertiary,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "grant-status-color",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        AnimatedVisibility(
            visible = granted,
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 520f)) +
                fadeIn(tween(DdMotion.FAST, easing = DdMotion.EaseOut)),
            exit = scaleOut(tween(120)) + fadeOut(tween(120)),
        ) {
            DdIcon(icon = DdIcons.Check, tint = colors.success, size = 15.dp)
        }
        Text(
            text = stringResource(
                if (granted) R.string.onboarding_permission_granted else R.string.onboarding_permission_missing,
            ),
            style = DdType.bodySmall,
            color = color,
        )
    }
}

@Composable
internal fun RadioMark(checked: Boolean) {
    val colors = deepDeepColors()
    val borderColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.hairline,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "radio-border",
    )
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 620f),
        label = "radio-dot",
    )
    Box(
        Modifier.size(20.dp).clip(CircleShape).border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(colors.accent),
        )
    }
}

@Composable
internal fun IndeterminateBar(modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    val transition = rememberInfiniteTransition(label = "onboarding-indeterminate")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = DdMotion.EaseInOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "onboarding-indeterminate-progress",
    )
    BoxWithConstraints(
        modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(colors.wash),
    ) {
        val barWidth = maxWidth * 0.38f
        Box(
            Modifier
                .offset { IntOffset(((maxWidth - barWidth) * progress).roundToPx(), 0) }
                .width(barWidth)
                .fillMaxHeight()
                .background(colors.accent.copy(alpha = 0.85f)),
        )
    }
}

@Composable
internal fun ProviderPickerSheet(
    providers: List<ProviderSummary>,
    selectedId: String,
    onSelect: (ProviderSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(providers, query) {
        val text = query.trim()
        if (text.isEmpty()) {
            providers
        } else {
            providers.filter { provider ->
                provider.name.contains(text, ignoreCase = true) || provider.id.contains(text, ignoreCase = true)
            }
        }
    }
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(title = stringResource(R.string.onboarding_provider_sheet_title), onClose = onDismiss)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            DdSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.onboarding_provider_search),
                clearContentDescription = stringResource(R.string.onboarding_search_clear),
                fieldTestTag = "onboarding_provider_search",
            )
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                DdEmptyState(
                    text = stringResource(R.string.onboarding_provider_no_result),
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it.id }) { provider ->
                        ProviderPickerRow(
                            provider = provider,
                            selected = provider.id == selectedId,
                            onClick = { onSelect(provider) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProviderPickerRow(
    provider: ProviderSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_provider_${provider.id}"),
        shape = RoundedCornerShape(20.dp),
        contentDescription = provider.name,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(provider.id, provider.name, size = 34.dp, iconSize = 21.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = DdType.body,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.onboarding_provider_models, provider.modelCount),
                    style = DdType.bodySmall,
                    color = colors.inkTertiary,
                    maxLines = 1,
                )
            }
            if (selected) {
                Spacer(Modifier.width(12.dp))
                DdIcon(icon = DdIcons.Check, tint = colors.accent, size = 16.dp)
            }
        }
    }
}
