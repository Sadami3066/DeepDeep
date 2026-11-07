package io.github.whywood.deepdeep.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdMotion
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors

@Composable
internal fun MemoryFeatureStep(modifier: Modifier = Modifier) {
    FeatureStep(
        title = stringResource(R.string.onboarding_feature_memory_title),
        body = stringResource(R.string.onboarding_feature_memory_body),
        icon = DdIcons.Brain,
        accents = listOf(DdIcons.Book, DdIcons.Clock),
        testTag = "onboarding_step_memory",
        modifier = modifier,
    )
}

@Composable
internal fun ConnectorFeatureStep(modifier: Modifier = Modifier) {
    FeatureStep(
        title = stringResource(R.string.onboarding_feature_connector_title),
        body = stringResource(R.string.onboarding_feature_connector_body),
        icon = DdIcons.Plug,
        accents = listOf(DdIcons.Cloud, DdIcons.Globe),
        testTag = "onboarding_step_connector",
        modifier = modifier,
    )
}

@Composable
internal fun SkillsFeatureStep(modifier: Modifier = Modifier) {
    FeatureStep(
        title = stringResource(R.string.onboarding_feature_skills_title),
        body = stringResource(R.string.onboarding_feature_skills_body),
        icon = DdIcons.Puzzle,
        accents = listOf(DdIcons.Book, DdIcons.Package),
        testTag = "onboarding_step_skills",
        modifier = modifier,
    )
}

@Composable
internal fun TimersFeatureStep(modifier: Modifier = Modifier) {
    FeatureStep(
        title = stringResource(R.string.onboarding_feature_timers_title),
        body = stringResource(R.string.onboarding_feature_timers_body),
        icon = DdIcons.Clock,
        accents = listOf(DdIcons.Message, DdIcons.Refresh),
        testTag = "onboarding_step_timers",
        modifier = modifier,
    )
}

@Composable
internal fun AskFeatureStep(modifier: Modifier = Modifier) {
    FeatureStep(
        title = stringResource(R.string.onboarding_feature_ask_title),
        body = stringResource(R.string.onboarding_feature_ask_body),
        icon = DdIcons.Message,
        accents = listOf(DdIcons.Check, DdIcons.Image),
        testTag = "onboarding_step_ask",
        modifier = modifier,
    )
}

@Composable
internal fun PrivacyFeatureStep(modifier: Modifier = Modifier) {
    FeatureStep(
        title = stringResource(R.string.onboarding_feature_privacy_title),
        body = stringResource(R.string.onboarding_feature_privacy_body),
        icon = DdIcons.Shield,
        accents = listOf(DdIcons.Folder, DdIcons.Trash),
        testTag = "onboarding_step_privacy",
        modifier = modifier,
    )
}

@Composable
private fun FeatureStep(
    title: String,
    body: String,
    icon: DdIcon,
    accents: List<DdIcon>,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    Box(
        modifier.fillMaxSize().testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FeatureArt(icon = icon, accents = accents)
            Spacer(Modifier.height(28.dp))
            Text(
                text = title,
                style = DdType.pageTitle,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = DdType.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FeatureArt(icon: DdIcon, accents: List<DdIcon>) {
    val colors = deepDeepColors()
    val transition = rememberInfiniteTransition(label = "onboarding-feature-float")
    val drift by transition.animateFloat(
        initialValue = 3f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = DdMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onboarding-feature-drift",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(colors.accentSoft, colors.wash))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val middle = center
            val minSide = size.minDimension
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.accent.copy(alpha = 0.10f), Color.Transparent),
                    center = middle,
                    radius = minSide * 0.5f,
                ),
                radius = minSide * 0.5f,
                center = middle,
            )
            drawCircle(
                color = colors.accent.copy(alpha = 0.14f),
                radius = 100.dp.toPx(),
                center = middle,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = colors.accent.copy(alpha = 0.07f),
                radius = 114.dp.toPx(),
                center = middle,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = colors.accent.copy(alpha = 0.35f),
                radius = 2.dp.toPx(),
                center = Offset(middle.x - 116.dp.toPx(), middle.y - 62.dp.toPx()),
            )
            drawCircle(
                color = colors.accent.copy(alpha = 0.22f),
                radius = 1.5.dp.toPx(),
                center = Offset(middle.x + 122.dp.toPx(), middle.y + 46.dp.toPx()),
            )
        }
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            DdIcon(icon = icon, tint = colors.onAccent, size = 38.dp)
        }
        accents.getOrNull(0)?.let { accent ->
            FeatureBubble(
                icon = accent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 34.dp, y = 30.dp)
                    .graphicsLayer { translationY = drift.dp.toPx() },
            )
        }
        accents.getOrNull(1)?.let { accent ->
            FeatureBubble(
                icon = accent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-36).dp, y = (-34).dp)
                    .graphicsLayer { translationY = -drift.dp.toPx() },
            )
        }
    }
}

@Composable
private fun FeatureBubble(icon: DdIcon, modifier: Modifier = Modifier) {
    val colors = deepDeepColors()
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.hairline.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DdIcon(icon = icon, tint = colors.inkSecondary, size = 20.dp)
    }
}
