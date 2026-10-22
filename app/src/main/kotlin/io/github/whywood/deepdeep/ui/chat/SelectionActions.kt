package io.github.whywood.deepdeep.ui.chat

import android.content.ClipboardManager
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.whywood.deepdeep.R
import androidx.compose.material3.Text
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred

internal class SelectionMenuController : TextContextMenuProvider {
    var state by mutableStateOf<SelectionMenuRequest?>(null)
        private set

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val dismissed = CompletableDeferred<Unit>()
        val session = object : TextContextMenuSession {
            override fun close() {
                dismissed.complete(Unit)
            }
        }
        state = SelectionMenuRequest(dataProvider, session)
        try {
            dismissed.await()
        } finally {
            state = null
        }
    }

    fun dismiss() {
        state?.session?.close()
    }
}

internal data class SelectionMenuRequest(
    val dataProvider: TextContextMenuDataProvider,
    val session: TextContextMenuSession,
)

@Composable
internal fun SelectableMessageText(
    messageId: Long,
    onQuote: (String) -> Unit,
    onSpeak: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val coordinator = LocalSelectionCoordinator.current
    val resetToken = coordinator?.tokenFor(messageId) ?: 0
    key(resetToken) {
        SelectableMessageTextImpl(
            messageId = messageId,
            coordinator = coordinator,
            onQuote = onQuote,
            onSpeak = onSpeak,
            content = content,
        )
    }
}

@Composable
private fun SelectableMessageTextImpl(
    messageId: Long,
    coordinator: SelectionCoordinator?,
    onQuote: (String) -> Unit,
    onSpeak: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val controller = remember { SelectionMenuController() }
    var containerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var selectionBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(controller.state) {
        if (controller.state != null) {
            coordinator?.activate(messageId, selectionBounds)
            coordinator?.updateMenuDismisser(messageId) { controller.dismiss() }
        } else {
            coordinator?.deactivate(messageId)
        }
    }

    DisposableEffect(messageId) {
        onDispose { coordinator?.deactivate(messageId) }
    }

    Box(
        Modifier.onGloballyPositioned { coordinates ->
            containerCoordinates = coordinates
            selectionBounds = coordinates.boundsInWindow()
            coordinator?.updateBounds(messageId, selectionBounds)
        },
    ) {
        CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides controller) {
            SelectionContainer {
                content()
            }
        }
        SelectionActionsMenu(
            controller = controller,
            containerCoordinates = containerCoordinates,
            onQuote = onQuote,
            onSpeak = onSpeak,
        )
    }
}

@Composable
private fun SelectionActionsMenu(
    controller: SelectionMenuController,
    containerCoordinates: LayoutCoordinates?,
    onQuote: (String) -> Unit,
    onSpeak: (String) -> Unit,
) {
    val request = controller.state ?: return
    val coordinates = containerCoordinates ?: return
    val context = LocalContext.current
    val colors = deepDeepColors()
    val density = LocalDensity.current
    val session = request.session
    val components = request.dataProvider.data().components
    val copyItem = components.filterIsInstance<TextContextMenuItem>()
        .firstOrNull { it.key == TextContextMenuKeys.CopyKey }
    val selectAllItem = components.filterIsInstance<TextContextMenuItem>()
        .firstOrNull { it.key == TextContextMenuKeys.SelectAllKey }
    val anchor = runCatching { request.dataProvider.contentBounds(coordinates) }.getOrDefault(Rect.Zero)
    val containerWidth = coordinates.size.width.toFloat()
    val menuWidth = 236.dp
    val widthPx = with(density) { menuWidth.toPx() }
    val heightPx = with(density) { 48.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val desiredX = anchor.left - widthPx / 2f
    val x = desiredX.coerceIn(marginPx, (containerWidth - widthPx - marginPx).coerceAtLeast(marginPx))
    val above = anchor.top - heightPx - marginPx
    val y = if (above > 0f) above else anchor.bottom + marginPx

    fun close() {
        session.close()
    }

    fun copySelection(): String? {
        copyItem?.onClick?.invoke(session)
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }

    BackHandler { close() }

    Popup(
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        properties = PopupProperties(focusable = false),
        onDismissRequest = { close() },
    ) {
        val shape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .clip(shape)
                .background(colors.raised)
                .border(1.dp, colors.hairline.copy(alpha = 0.65f), shape)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionTextAction(
                text = stringResource(R.string.assistant_action_copy),
                onClick = {
                    copySelection()
                    close()
                },
                testTag = "selection_action_copy",
            )
            SelectionTextAction(
                text = stringResource(R.string.assistant_action_select_all),
                onClick = {
                    selectAllItem?.onClick?.invoke(session)
                },
                testTag = "selection_action_select_all",
            )
            SelectionTextAction(
                text = stringResource(R.string.assistant_quote),
                onClick = {
                    val text = copySelection()
                    close()
                    if (text != null) onQuote(text)
                },
                testTag = "selection_action_quote",
            )
            SelectionTextAction(
                text = stringResource(R.string.assistant_action_speak),
                onClick = {
                    val text = copySelection()
                    close()
                    if (text != null) onSpeak(text)
                },
                testTag = "selection_action_speak",
            )
        }
    }
}

@Composable
private fun SelectionTextAction(
    text: String,
    onClick: () -> Unit,
    testTag: String,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.height(44.dp).testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        contentDescription = text,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = DdType.label,
            color = colors.inkSecondary,
            maxLines = 1,
        )
    }
}

internal object ReadAloud {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        val active = engine
        if (active != null && ready) {
            active.speak(text, TextToSpeech.QUEUE_FLUSH, null, "deepdeep-selection")
            return
        }
        if (active == null) {
            pending = text
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    pending?.let { queued ->
                        engine?.speak(queued, TextToSpeech.QUEUE_FLUSH, null, "deepdeep-selection")
                    }
                }
                pending = null
            }
        } else {
            pending = text
        }
    }

    fun stop() {
        engine?.stop()
        pending = null
    }
}
