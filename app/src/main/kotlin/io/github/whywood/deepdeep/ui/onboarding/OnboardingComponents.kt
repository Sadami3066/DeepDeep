package io.github.whywood.deepdeep.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdEmptyState
import io.github.whywood.deepdeep.design.DdGroupCard
import io.github.whywood.deepdeep.design.DdHairline
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

internal const val ONBOARDING_STEP_COUNT = 6

internal const val KEY_STATE_IDLE = 0
internal const val KEY_STATE_SAVING = 1
internal const val KEY_STATE_SAVED = 2
internal const val KEY_STATE_FAILED = 3

@Composable
internal fun OnboardingHeader(step: Int, onBack: () -> Unit) {
    val description = stringResource(
        R.string.onboarding_progress_description,
        step + 1,
        ONBOARDING_STEP_COUNT,
    )
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)) {
        if (step > 0) {
            DdIconButton(
                icon = DdIcons.ArrowLeft,
                onClick = onBack,
                contentDescription = stringResource(R.string.onboarding_back),
                modifier = Modifier.align(Alignment.CenterStart),
                iconSize = 23.dp,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in 0 until ONBOARDING_STEP_COUNT) {
                val active = index == step
                val width by animateDpAsState(
                    targetValue = if (active) 24.dp else 6.dp,
                    animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                    label = "onboarding-progress-width-$index",
                )
                val color by animateColorAsState(
                    targetValue = when {
                        active -> deepDeepColors().accent
                        index < step -> deepDeepColors().accent.copy(alpha = 0.32f)
                        else -> deepDeepColors().hairline
                    },
                    animationSpec = tween(DdMotion.BASE, easing = DdMotion.EaseOut),
                    label = "onboarding-progress-color-$index",
                )
                Box(Modifier.width(width).height(6.dp).clip(CircleShape).background(color))
            }
        }
    }
}

@Composable
internal fun StepHeading(title: String, subtitle: String? = null) {
    val colors = deepDeepColors()
    Text(text = title, style = DdType.pageTitle, color = colors.ink)
    if (subtitle != null) {
        Spacer(Modifier.height(10.dp))
        Text(text = subtitle, style = DdType.bodySmall, color = colors.inkTertiary)
    }
}

@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier, style = DdType.label, color = deepDeepColors().inkTertiary)
}

@Composable
internal fun OnboardingCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxWidth().border(1.dp, deepDeepColors().hairline, RoundedCornerShape(18.dp))) {
        DdGroupCard(modifier = modifier, content = content)
    }
}

@Composable
internal fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    testTag: String? = null,
) {
    val colors = deepDeepColors()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val underline by animateColorAsState(
        targetValue = if (focused) colors.accent else colors.hairline,
        animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
        label = "onboarding-underline",
    )
    val textStyle = DdType.body.copy(fontSize = 20.sp, lineHeight = 28.sp)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                textStyle = textStyle.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                interactionSource = interaction,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = colors.inkTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(underline))
    }
}

@Composable
internal fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTags: List<String>? = null,
) {
    val colors = deepDeepColors()
    val segmentShape = RoundedCornerShape(11.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.wash),
    ) {
        val segmentWidth = maxWidth / options.size
        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.coerceIn(0, options.lastIndex).toFloat(),
            animationSpec = DdMotion.SegmentedSpring,
            label = "onboarding-segment-index",
        )
        val indicatorAlpha by animateFloatAsState(
            targetValue = if (selectedIndex in options.indices) 1f else 0f,
            animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
            label = "onboarding-segment-alpha",
        )
        Box(
            Modifier
                .offset(x = segmentWidth * animatedIndex)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(4.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = indicatorAlpha }
                    .shadow(2.dp, segmentShape)
                    .clip(segmentShape)
                    .background(colors.surface),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    targetValue = if (selected) colors.ink else colors.inkTertiary,
                    animationSpec = tween(DdMotion.FAST, easing = DdMotion.EaseOut),
                    label = "onboarding-segment-text-$index",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (testTags != null) Modifier.testTag(testTags[index]) else Modifier)
                        .selectable(
                            selected = selected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = label, style = DdType.label, color = textColor, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun SettingRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val colors = deepDeepColors()
    Column(modifier.fillMaxWidth()) {
        DdPressable(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = text, modifier = Modifier.weight(1f), style = DdType.body, color = colors.ink)
                Spacer(Modifier.width(8.dp))
                DdIcon(icon = DdIcons.ChevronRight, tint = colors.inkTertiary.copy(alpha = 0.7f), size = 16.dp)
            }
        }
        DdHairline()
    }
}

@Composable
internal fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = deepDeepColors()
    val background = if (enabled) colors.accent else colors.accent.copy(alpha = 0.32f)
    val contentColor = if (enabled) colors.onAccent else colors.onAccent.copy(alpha = 0.85f)
    DdPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(15.dp),
        background = background,
        enabled = enabled,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                style = DdType.button,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
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
