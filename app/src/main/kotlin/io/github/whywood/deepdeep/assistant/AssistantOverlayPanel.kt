package io.github.whywood.deepdeep.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.AssistantStep
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.ui.chat.ChatTurn
import io.github.whywood.deepdeep.ui.chat.rememberChatScrollController
import io.github.whywood.deepdeep.ui.questions.AskQuestionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AssistantOverlayPanel(
    state: AssistantOverlayState,
    pendingQuestions: List<PendingQuestion>,
    config: AssistantOverlayConfig,
    actions: AssistantOverlayActions,
    onQuote: (Long, String) -> Unit,
    onOpenInApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = deepDeepColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val chatDensity = remember(density, config.chatFontScale) {
        Density(density = density.density, fontScale = density.fontScale * config.chatFontScale)
    }
    val listState = rememberLazyListState()
    val scroll = rememberChatScrollController(listState)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(28.dp)
    val copiedMessage = stringResource(R.string.assistant_overlay_copied)
    val pullThresholdPx = with(density) { 96.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val messages = state.conversation.messages

    fun settle() {
        scope.launch {
            animate(
                initialValue = dragOffset,
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> dragOffset = value }
        }
    }

    val dragModifier = Modifier.pointerInput(pullThresholdPx) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dragOffset > pullThresholdPx) onOpenInApp() else settle()
            },
            onDragCancel = { settle() },
        ) { _, dragAmount ->
            dragOffset = (dragOffset + dragAmount).coerceIn(0f, pullThresholdPx * 2f)
        }
    }

    LaunchedEffect(state.sessionId, state.conversation.loaded) {
        if (state.conversation.loaded) scroll.jumpToBottom(animate = false)
    }
    LaunchedEffect(messages) { scroll.followContent() }
    LaunchedEffect(pendingQuestions.map { it.requestId }) {
        if (pendingQuestions.isNotEmpty()) {
            delay(60)
            scroll.jumpToBottom(animate = true)
        }
    }

    val copyOutput: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("deepdeep-assistant", text))
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier
            .graphicsLayer { translationY = dragOffset }
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF0B1220),
                spotColor = Color(0x3D0B1220),
            )
            .clip(shape)
            .background(colors.surface)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(
            modifier = dragModifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.sessionTitle.ifBlank { stringResource(R.string.assistant_overlay_title) },
                modifier = Modifier.weight(1f).testTag("assistant_overlay_panel_title"),
                style = DdType.label.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DdIconButton(
                icon = DdIcons.External,
                onClick = onOpenInApp,
                contentDescription = stringResource(R.string.assistant_overlay_fullscreen),
                modifier = Modifier.testTag("assistant_overlay_panel_fullscreen"),
                size = 36.dp,
                iconSize = 17.dp,
                tint = colors.inkTertiary,
            )
        }
        if (!state.conversation.loaded) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DdSpinner(size = 18.dp, strokeWidth = 2.dp, color = colors.inkTertiary)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalDensity provides chatDensity) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("assistant_overlay_transcript"),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatTurn(
                                message = message,
                                onToggleStep = { stepId -> actions.onToggleStep(message.id, stepId) },
                                onCopyOutput = copyOutput,
                                onQuote = onQuote,
                                onOpenAttachment = actions.onOpenPreview,
                                onOpenFile = actions.onOpenFile,
                                onRetry = actions.onRetry,
                                attachmentRoot = config.attachmentRoot,
                                autoCollapseProcess = config.autoCollapseProcess,
                                pendingQuestions = pendingQuestions,
                                onAnswerQuestion = actions.onAnswerQuestion,
                                onOpenPresentFile = actions.onOpenFile,
                                onSharePresentFile = actions.onShareFile,
                                onOpenWorkshop = actions.onOpenWorkshop,
                            )
                        }
                        pendingQuestions
                            .firstOrNull { pending ->
                                messages.none { message ->
                                    message.steps.any { step ->
                                        step is AssistantStep.Tool && step.trace.toolCallId == pending.toolCallId
                                    }
                                }
                            }
                            ?.let { pending ->
                                item(key = "pending-question-${pending.requestId}") {
                                    AskQuestionCard(
                                        questions = pending.questions,
                                        answers = null,
                                        submitted = false,
                                        onAnswer = { answers -> actions.onAnswerQuestion(pending, answers) },
                                    )
                                }
                            }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(colors.surface, colors.surface.copy(alpha = 0f)),
                            ),
                        ),
                )
            }
        }
        Box(
            modifier = dragModifier.fillMaxWidth().height(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.inkTertiary.copy(alpha = 0.4f)),
            )
        }
    }
}

@Composable
internal fun AssistantOverlayListening(transcript: String) {
    val colors = deepDeepColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DdIcon(icon = DdIcons.AudioLines, tint = colors.accent, size = 16.dp)
        Box(Modifier.size(width = 8.dp, height = 1.dp))
        Text(
            text = transcript.ifEmpty { stringResource(R.string.assistant_overlay_listening) },
            modifier = Modifier.weight(1f),
            style = DdType.bodySmall,
            color = if (transcript.isEmpty()) colors.inkTertiary else colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
