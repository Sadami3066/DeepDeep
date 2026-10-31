package io.github.whywood.deepdeep.floating

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.assistant.AssistantEntry
import io.github.whywood.deepdeep.data.FloatingWindowPreferences
import io.github.whywood.deepdeep.data.FloatingWindowSettings
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.service.ActiveRunView
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.vscreen.VirtualScreenController
import io.github.whywood.deepdeep.vscreen.VirtualScreenState
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FloatingWindowController(
    private val context: Context,
    private val runtime: ConversationQueries,
    private val activeRuns: StateFlow<Map<String, ActiveRunView>>,
    private val questionCenter: QuestionCenter,
    private val preferences: FloatingWindowPreferences,
    private val theme: Flow<ThemeMode>,
    private val fontStyle: DdFontStyle,
    private val virtualScreenProvider: () -> VirtualScreenController?,
    private val onOpenApp: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val feedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val feed = FloatingFeed(runtime, activeRuns, questionCenter.pending, feedScope)
    private val windowContext = LanguagePreferences.wrap(context.applicationContext)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val panelOpen = MutableStateFlow(false)
    private val appForeground = MutableStateFlow(false)
    private val settingsState = MutableStateFlow(FloatingWindowSettings())
    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val bubbleState = MutableStateFlow(FloatingBubbleState())
    private val surfaceUi = MutableStateFlow<FloatingSurfaceUi?>(null)
    private val virtualScreenState = MutableStateFlow<VirtualScreenState?>(null)
    private val renderTrigger = MutableStateFlow(0L)

    private var started = false
    private var enabled = false
    private var shown = false
    private var bubbleHost: FloatingWindowHost? = null
    private var bubbleView: FloatingTouchInterceptor? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleWindowPosition = FloatingBubblePosition(0, 0)
    private var surfaceHost: FloatingWindowHost? = null
    private var surfaceView: FloatingTouchInterceptor? = null
    private var surfaceParams: WindowManager.LayoutParams? = null
    private var ticker: Job? = null
    private var showJob: Job? = null
    private var virtualScreen: VirtualScreenController? = null
    private var virtualScreenJob: Job? = null
    private var longPressAction: Runnable? = null
    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var touchDownWindowX = 0
    private var touchDownWindowY = 0
    private var dragging = false
    private var longPressFired = false
    private var panelOpenAtDown = false
    private var lastPanelCloseAt = 0L

    fun start() {
        if (started) return
        started = true
        feed.start()
        scope.launch {
            preferences.settings.collect { settings ->
                settingsState.value = settings
                enabled = settings.enabled
                requestRender()
            }
        }
        scope.launch {
            AppVisibility.foregroundFlow.collect { foreground ->
                appForeground.value = foreground
                requestRender()
            }
        }
        scope.launch { feed.state.collect { requestRender() } }
        scope.launch { panelOpen.collect { requestRender() } }
        scope.launch { theme.collect { mode -> themeMode.value = mode } }
        scope.launch { now.collect { requestRender() } }
        scope.launch { virtualScreenState.collect { requestRender() } }
        scope.launch { renderTrigger.collect { render() } }
    }

    fun stop() {
        if (!started) return
        started = false
        showJob?.cancel()
        showJob = null
        hideWindows()
        virtualScreenJob?.cancel()
        virtualScreenJob = null
        feed.stop()
        feedScope.cancel()
        scope.cancel()
    }

    private fun requestRender() {
        renderTrigger.update { it + 1 }
    }

    private fun render() {
        if (!started) return
        refreshVirtualScreen()
        val visible = enabled && Settings.canDrawOverlays(context)
        if (!visible) {
            showJob?.cancel()
            showJob = null
            hideWindows()
            return
        }
        if (shown) {
            updateWindows()
            return
        }
        if (showJob != null) return
        showJob = scope.launch {
            delay(SHOW_DELAY_MS)
            showJob = null
            if (!started) return@launch
            if (!enabled || !Settings.canDrawOverlays(context)) return@launch
            showWindows()
        }
    }

    private fun showWindows() {
        if (shown) return
        shown = true
        bubbleWindowPosition = restoredBubblePosition()
        if (!createBubbleWindow()) {
            shown = false
            return
        }
        startTicker()
        updateWindows()
    }

    private fun hideWindows() {
        if (!shown) return
        shown = false
        removeSurfaceWindow()
        removeBubbleWindow()
        ticker?.cancel()
        ticker = null
        panelOpen.value = false
    }

    private fun updateWindows() {
        if (!shown) return
        bubbleState.value = FloatingBubbleState(
            mood = floatingWhaleMood(feed.state.value, now.value),
            unread = floatingUnreadCount(feed.state.value),
        )
        updateSurfaceWindow()
    }

    private fun updateSurfaceWindow() {
        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val bubbleVisual = FloatingBubblePosition(
            x = bubbleWindowPosition.x + px(FloatingBubbleWindowPadding),
            y = bubbleWindowPosition.y + px(FloatingBubbleWindowPadding),
        )
        val feedState = feed.state.value
        val card = if (panelOpen.value || appForeground.value) {
            null
        } else {
            floatingEventCard(feedState, System.currentTimeMillis())
        }
        when {
            panelOpen.value -> showSurfaceWindow(
                ui = FloatingSurfaceUi.Panel(
                    runs = feedState.runs,
                    questions = feedState.questions,
                    deliveries = feedState.deliveries,
                    virtualScreen = virtualScreenState.value,
                    now = now.value,
                ),
                surfaceWidth = FloatingPanelWidth,
                maxHeightRatio = FloatingPanelMaxHeightRatio,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                bubbleVisual = bubbleVisual,
                sessionTitle = "",
            )

            card != null -> showSurfaceWindow(
                ui = FloatingSurfaceUi.Card(card = card),
                surfaceWidth = FloatingCardWidth,
                maxHeightRatio = FloatingCardMaxHeightRatio,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                bubbleVisual = bubbleVisual,
                sessionTitle = sessionTitle(card.sessionId()),
            )

            else -> removeSurfaceWindow()
        }
    }

    private fun showSurfaceWindow(
        ui: FloatingSurfaceUi,
        surfaceWidth: Dp,
        maxHeightRatio: Float,
        screenWidth: Int,
        screenHeight: Int,
        bubbleVisual: FloatingBubblePosition,
        sessionTitle: String,
    ) {
        if (surfaceView == null) createSurfaceWindow()
        val view = surfaceView ?: return
        val params = surfaceParams ?: return
        val windowWidth = px(surfaceWidth) + px(FloatingSurfaceWindowPadding) * 2
        val placement = floatingPlaceSurface(
            bubble = bubbleVisual,
            bubbleSize = px(FloatingBubbleSize),
            windowWidth = windowWidth,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            edgeMargin = px(FloatingEdgeMargin),
            gap = px(FloatingSurfaceGap),
            windowPadding = px(FloatingSurfaceWindowPadding),
            arrowInset = px(FloatingArrowWidth) / 2 + px(8.dp),
            maxHeightRatio = maxHeightRatio,
        )
        params.width = windowWidth
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.LEFT or if (placement.anchorBottom) Gravity.BOTTOM else Gravity.TOP
        params.x = placement.x
        params.y = placement.y
        runCatching { windowManager.updateViewLayout(view, params) }
        surfaceUi.value = when (ui) {
            is FloatingSurfaceUi.Card -> ui.copy(
                sessionTitle = sessionTitle,
                maxHeightPx = (placement.maxContentHeight - px(FloatingArrowHeight)).coerceAtLeast(0),
                arrowSide = placement.arrowSide,
                arrowOffsetPx = placement.arrowOffset -
                    px(FloatingSurfaceWindowPadding) -
                    px(FloatingArrowWidth) / 2,
            )

            is FloatingSurfaceUi.Panel -> ui.copy(maxHeightPx = placement.maxContentHeight)
        }
    }

    private fun createSurfaceWindow() {
        if (surfaceView != null) return
        val host = FloatingWindowHost()
        host.start()
        val compose = host.composeView(windowContext)
        compose.setContent {
            val theme by themeMode.collectAsState()
            DeepDeepTheme(theme, fontStyle) {
                val surface by surfaceUi.collectAsState()
                val density = LocalDensity.current
                when (val value = surface) {
                    is FloatingSurfaceUi.Card -> FloatingEventCard(
                        card = value.card,
                        sessionTitle = value.sessionTitle,
                        maxHeight = with(density) { value.maxHeightPx.toDp() },
                        arrowSide = value.arrowSide,
                        arrowOffset = with(density) { value.arrowOffsetPx.toDp() },
                        onAnswerQuestion = ::answerQuestion,
                        onOpenQuestion = { pending -> openSession(pending.sessionId) },
                        onOpenDelivery = { delivery ->
                            feed.markDeliveryRead(delivery.key)
                            openSession(delivery.sessionId)
                        },
                        onOpenCompletion = { completion -> openSession(completion.sessionId) },
                    )

                    is FloatingSurfaceUi.Panel -> FloatingPanel(
                        runs = value.runs,
                        questions = value.questions,
                        deliveries = value.deliveries,
                        virtualScreen = value.virtualScreen,
                        now = value.now,
                        maxHeight = with(density) { value.maxHeightPx.toDp() },
                        onOpenRun = { run -> openSession(run.sessionId) },
                        onOpenQuestion = { pending -> openSession(pending.sessionId) },
                        onOpenDelivery = { delivery ->
                            feed.markDeliveryRead(delivery.key)
                            openSession(delivery.sessionId)
                        },
                        onOpenVirtualScreen = { virtualScreen?.openViewer(context) },
                        onCollapse = { panelOpen.value = false },
                    )

                    null -> Unit
                }
            }
        }
        val view = FloatingTouchInterceptor(windowContext).apply {
            addView(
                compose,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
            )
            onTouch = { event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    onSurfaceOutsideTouch()
                    true
                } else {
                    false
                }
            }
        }
        host.bindOwners(view)
        val params = WindowManager.LayoutParams(
            px(FloatingCardWidth) + px(FloatingSurfaceWindowPadding) * 2,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
        }
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            host.destroy()
            return
        }
        surfaceHost = host
        surfaceView = view
        surfaceParams = params
        host.resume()
    }

    private fun removeSurfaceWindow() {
        surfaceUi.value = null
        val view = surfaceView ?: return
        runCatching { windowManager.removeView(view) }
        surfaceHost?.destroy()
        surfaceHost = null
        surfaceView = null
        surfaceParams = null
    }

    private fun createBubbleWindow(): Boolean {
        if (bubbleView != null) return true
        val host = FloatingWindowHost()
        host.start()
        val compose = host.composeView(windowContext)
        compose.setContent {
            val theme by themeMode.collectAsState()
            DeepDeepTheme(theme, fontStyle) {
                val state by bubbleState.collectAsState()
                FloatingBubble(mood = state.mood, unread = state.unread)
            }
        }
        val view = FloatingTouchInterceptor(windowContext).apply {
            addView(
                compose,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            onTouch = ::handleBubbleTouch
        }
        host.bindOwners(view)
        val size = px(FloatingBubbleWindowSize)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = bubbleWindowPosition.x
            y = bubbleWindowPosition.y
        }
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            host.destroy()
            return false
        }
        bubbleHost = host
        bubbleView = view
        bubbleParams = params
        host.resume()
        return true
    }

    private fun removeBubbleWindow() {
        val view = bubbleView
        if (view != null) {
            cancelLongPress()
            runCatching { windowManager.removeView(view) }
        }
        bubbleHost?.destroy()
        bubbleHost = null
        bubbleView = null
        bubbleParams = null
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                delay(if (panelOpen.value) TICK_PANEL_MS else TICK_IDLE_MS)
            }
        }
    }

    private fun handleBubbleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownRawX = event.rawX
                touchDownRawY = event.rawY
                touchDownWindowX = bubbleWindowPosition.x
                touchDownWindowY = bubbleWindowPosition.y
                dragging = false
                longPressFired = false
                panelOpenAtDown = panelOpen.value
                val action = Runnable { onBubbleLongPress() }
                longPressAction = action
                bubbleView?.postDelayed(action, LONG_PRESS_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchDownRawX
                val dy = event.rawY - touchDownRawY
                if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop()) {
                    dragging = true
                    cancelLongPress()
                }
                if (dragging) {
                    moveBubble(touchDownWindowX + dx.toInt(), touchDownWindowY + dy.toInt())
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (longPressFired) {
                    longPressFired = false
                } else if (dragging) {
                    finishBubbleDrag()
                } else {
                    onBubbleClick()
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (dragging) finishBubbleDrag()
                dragging = false
                longPressFired = false
                return true
            }
        }
        return false
    }

    private fun onBubbleClick() {
        val closedByOutside = SystemClock.uptimeMillis() - lastPanelCloseAt < PANEL_CLOSE_GUARD_MS
        panelOpen.value = !panelOpenAtDown && !closedByOutside
    }

    private fun onBubbleLongPress() {
        longPressFired = true
        panelOpen.value = false
        AssistantEntry.openOverlay(context)
    }

    private fun onSurfaceOutsideTouch() {
        if (panelOpen.value) {
            panelOpen.value = false
            lastPanelCloseAt = SystemClock.uptimeMillis()
            return
        }
        val card = floatingEventCard(feed.state.value, System.currentTimeMillis()) ?: return
        feed.dismissCard(card.key)
    }

    private fun moveBubble(x: Int, y: Int) {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val metrics = context.resources.displayMetrics
        val clamped = floatingClampBubblePosition(
            x = x,
            y = y,
            bubbleSize = px(FloatingBubbleWindowSize),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            edgeMargin = 0,
        )
        params.x = clamped.x
        params.y = clamped.y
        bubbleWindowPosition = clamped
        runCatching { windowManager.updateViewLayout(view, params) }
        updateSurfaceWindow()
    }

    private fun finishBubbleDrag() {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val metrics = context.resources.displayMetrics
        val clamped = floatingClampBubblePosition(
            x = bubbleWindowPosition.x,
            y = bubbleWindowPosition.y,
            bubbleSize = px(FloatingBubbleWindowSize),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            edgeMargin = 0,
        )
        params.x = clamped.x
        params.y = clamped.y
        bubbleWindowPosition = clamped
        runCatching { windowManager.updateViewLayout(view, params) }
        feedScope.launch { preferences.setBubblePosition(clamped.x, clamped.y) }
        requestRender()
    }

    private fun restoredBubblePosition(): FloatingBubblePosition {
        val metrics = context.resources.displayMetrics
        val settings = settingsState.value
        val size = px(FloatingBubbleWindowSize)
        return if (settings.hasBubblePosition) {
            floatingClampBubblePosition(
                x = settings.bubbleX,
                y = settings.bubbleY,
                bubbleSize = size,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                edgeMargin = 0,
            )
        } else {
            floatingDefaultBubblePosition(
                bubbleSize = size,
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                edgeMargin = 0,
            )
        }
    }

    private fun answerQuestion(pending: PendingQuestion, option: QuestionOption) {
        val question = pending.questions.singleOrNull() ?: return
        questionCenter.answer(
            pending.requestId,
            listOf(QuestionAnswer(questionId = question.id, selected = listOf(option.label))),
        )
    }

    private fun openSession(sessionId: String) {
        panelOpen.value = false
        if (!AssistantEntry.openOverlay(context, sessionId)) onOpenApp()
    }

    private fun sessionTitle(sessionId: String): String {
        val titles = feed.state.value.sessionTitles
        return titles[sessionId].orEmpty()
            .ifBlank { activeRuns.value[sessionId]?.title.orEmpty() }
    }

    private fun refreshVirtualScreen() {
        val controller = runCatching { virtualScreenProvider() }.getOrNull()
        if (controller === virtualScreen) return
        virtualScreen = controller
        virtualScreenState.value = controller?.state?.value
        virtualScreenJob?.cancel()
        virtualScreenJob = null
        if (controller != null) {
            virtualScreenJob = scope.launch {
                controller.state.collect { state -> virtualScreenState.value = state }
            }
        }
    }

    private fun cancelLongPress() {
        longPressAction?.let { action -> bubbleView?.removeCallbacks(action) }
        longPressAction = null
    }

    private fun px(value: Dp): Int = (value.value * context.resources.displayMetrics.density).roundToInt()

    private fun touchSlop(): Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private companion object {
        const val SHOW_DELAY_MS = 350L
        const val LONG_PRESS_MS = 480L
        const val TICK_PANEL_MS = 1_000L
        const val TICK_IDLE_MS = 20_000L
        const val PANEL_CLOSE_GUARD_MS = 400L
    }
}

internal data class FloatingBubbleState(
    val mood: FloatingWhaleMood = FloatingWhaleMood.IDLE,
    val unread: Int = 0,
)

private sealed interface FloatingSurfaceUi {
    data class Card(
        val card: FloatingEventCard,
        val sessionTitle: String = "",
        val maxHeightPx: Int = 0,
        val arrowSide: FloatingArrowSide = FloatingArrowSide.BOTTOM,
        val arrowOffsetPx: Int = 0,
    ) : FloatingSurfaceUi

    data class Panel(
        val runs: List<FloatingRun>,
        val questions: List<PendingQuestion>,
        val deliveries: List<FloatingDelivery>,
        val virtualScreen: VirtualScreenState?,
        val now: Long,
        val maxHeightPx: Int = 0,
    ) : FloatingSurfaceUi
}
