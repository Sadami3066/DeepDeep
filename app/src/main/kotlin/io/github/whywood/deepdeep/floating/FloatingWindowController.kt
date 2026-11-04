package io.github.whywood.deepdeep.floating

import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.view.WindowInsetsCompat
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.assistant.AssistantEntry
import io.github.whywood.deepdeep.data.FloatingWindowPreferences
import io.github.whywood.deepdeep.data.FloatingWindowSettings
import io.github.whywood.deepdeep.data.LanguagePreferences
import io.github.whywood.deepdeep.design.DdFontStyle
import io.github.whywood.deepdeep.design.DeepDeepTheme
import io.github.whywood.deepdeep.design.ThemeMode
import io.github.whywood.deepdeep.plugin.PluginSetupNotice
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.QuestionAnswer
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.QuestionOption
import io.github.whywood.deepdeep.service.ActiveRunView
import io.github.whywood.deepdeep.service.AppVisibility
import io.github.whywood.deepdeep.service.NotificationCenter
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
    private val pluginSetup: StateFlow<PluginSetupNotice?> = MutableStateFlow(null),
    private val onOpenApp: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val feedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val feed = FloatingFeed(runtime, activeRuns, questionCenter.pending, feedScope, pluginSetup) { AppVisibility.foreground }
    private val windowContext = LanguagePreferences.wrap(context.applicationContext)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val panelOpen = MutableStateFlow(false)
    private val previewOpen = MutableStateFlow(false)
    private val appForeground = MutableStateFlow(false)
    private val settingsState = MutableStateFlow(FloatingWindowSettings())
    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val bubbleState = MutableStateFlow(FloatingBubbleState())
    private val surfaceUi = MutableStateFlow<FloatingSurfaceUi?>(null)
    private val virtualScreenState = MutableStateFlow<VirtualScreenState?>(null)
    private val renderTrigger = MutableStateFlow(0L)
    private val contentFrame = MutableStateFlow(
        floatingFullContentFrame(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
        ),
    )
    private val previewFrame = MutableStateFlow<ImageBitmap?>(null)
    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    private var previewJob: Job? = null
    private var previewController: VirtualScreenController? = null
    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            refreshContentFrame(ignoreViewFrame = true)
            requestRender()
        }

        override fun onLowMemory() = Unit
    }
    private val ring = MutableStateFlow(FloatingBubbleRing.NONE)
    private var longPressAction: Runnable? = null
    private var ringStartAction: Runnable? = null
    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var touchDownWindowX = 0
    private var touchDownWindowY = 0
    private var dragging = false
    private var longPressFired = false

    fun start() {
        if (started) return
        started = true
        feed.start()
        context.applicationContext.registerComponentCallbacks(configurationCallbacks)
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
        scope.launch { previewOpen.collect { requestRender() } }
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
        runCatching { context.applicationContext.unregisterComponentCallbacks(configurationCallbacks) }
        stopPreview()
        previewScope.cancel()
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
        updatePreview()
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
        refreshContentFrame(ignoreViewFrame = true)
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
        stopPreview()
        removeSurfaceWindow()
        removeBubbleWindow()
        ticker?.cancel()
        ticker = null
        panelOpen.value = false
        previewOpen.value = false
    }

    private fun updateWindows() {
        if (!shown) return
        bubbleState.value = FloatingBubbleState(
            mood = floatingWhaleMood(feed.state.value, now.value),
            pending = floatingPendingCount(feed.state.value),
        )
        updateSurfaceWindow()
    }

    private fun updateSurfaceWindow() {
        val feedState = feed.state.value
        val panel = panelOpen.value
        val preview = previewOpen.value
        val queue = if (panel || appForeground.value) {
            emptyList()
        } else {
            floatingEventQueue(feedState)
        }
        val card = queue.firstOrNull()
        val body: FloatingSurfaceBody? = when {
            panel -> FloatingSurfaceBody.Panel(
                runs = feedState.runs,
                questions = feedState.questions,
                completed = floatingVisibleCompletions(feedState),
                now = now.value,
                subscribedSessionId = feedState.subscription?.sessionId,
            )

            card != null -> FloatingSurfaceBody.Card(
                card = card,
                sessionTitle = cardTitle(card),
                queueSize = queue.size,
            )

            else -> null
        }
        if (body == null && !preview) {
            removeSurfaceWindow()
            return
        }
        showSurfaceWindow(
            body = body,
            preview = preview,
            surfaceWidth = if (body is FloatingSurfaceBody.Panel) FloatingPanelWidth else FloatingCardWidth,
            maxHeightRatio = if (body is FloatingSurfaceBody.Panel) {
                FloatingPanelMaxHeightRatio
            } else {
                FloatingCardMaxHeightRatio
            },
        )
    }

    private fun showSurfaceWindow(
        body: FloatingSurfaceBody?,
        preview: Boolean,
        surfaceWidth: Dp,
        maxHeightRatio: Float,
    ) {
        if (surfaceView == null) createSurfaceWindow()
        val view = surfaceView ?: return
        val params = surfaceParams ?: return
        val frame = contentFrame.value
        val screenWidth = frame.width
        val screenHeight = frame.height
        val padding = px(FloatingSurfaceWindowPadding)
        val bubbleVisual = FloatingBubblePosition(
            x = bubbleWindowPosition.x + px(FloatingBubbleWindowPadding),
            y = bubbleWindowPosition.y + px(FloatingBubbleWindowPadding),
        )
        val state = virtualScreenState.value
        val previewSpec = FloatingPreviewSpec(
            width = px(FloatingVirtualPreviewWidth),
            aspectRatio = state?.let(::floatingPreviewRatio) ?: FLOATING_PREVIEW_DEFAULT_RATIO,
            maxHeightRatio = FloatingVirtualPreviewMaxHeightRatio,
            chipHeight = px(FloatingVirtualPreviewChipHeight),
            online = state is VirtualScreenState.Online,
        )
        if (body != null) {
            val windowWidth = px(surfaceWidth) + padding * 2
            val placement = floatingPlaceSurfaceStack(
                bubble = bubbleVisual,
                bubbleSize = px(FloatingBubbleSize),
                windowWidth = windowWidth,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                edgeMargin = px(FloatingEdgeMargin),
                gap = px(FloatingSurfaceGap),
                windowPadding = padding,
                blockGap = px(FloatingSurfaceBlockGap),
                maxHeightRatio = maxHeightRatio,
                minBodyHeight = px(FloatingSurfaceMinBodyHeight),
                preview = previewSpec.takeIf { preview },
            )
            if (preview && placement.preview == null) {
                showSurfaceWindow(
                    body = null,
                    preview = true,
                    surfaceWidth = surfaceWidth,
                    maxHeightRatio = maxHeightRatio,
                )
                return
            }
            params.width = windowWidth
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.LEFT or if (placement.anchorBottom) Gravity.BOTTOM else Gravity.TOP
            params.x = placement.x
            params.y = placement.y
            runCatching { windowManager.updateViewLayout(view, params) }
            surfaceUi.value = FloatingSurfaceUi(
                body = body.withMaxHeight(placement.maxContentHeight),
                preview = placement.preview?.let { FloatingSurfacePreview(it.width, it.height) },
                anchoredBottom = placement.anchorBottom,
            )
        } else {
            val size = floatingStandalonePreviewSize(previewSpec, screenHeight, padding)
            val windowWidth = size.width + padding * 2
            val windowHeight = size.height + padding * 2
            val placement = floatingPlacePreview(
                bubble = bubbleVisual,
                bubbleSize = px(FloatingBubbleSize),
                windowWidth = windowWidth,
                windowHeight = windowHeight,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                edgeMargin = px(FloatingEdgeMargin),
                gap = px(FloatingSurfaceGap),
            )
            params.width = windowWidth
            params.height = windowHeight
            params.gravity = Gravity.LEFT or Gravity.TOP
            params.x = placement.x
            params.y = placement.y
            runCatching { windowManager.updateViewLayout(view, params) }
            surfaceUi.value = FloatingSurfaceUi(
                preview = FloatingSurfacePreview(size.width, size.height),
                anchoredBottom = placement.arrowSide == FloatingArrowSide.BOTTOM,
            )
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
                val content = surface
                if (content != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(FloatingSurfaceWindowPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val body = content.body
                        val preview = content.preview
                        if (content.anchoredBottom) {
                            if (preview != null) {
                                SurfacePreview(preview)
                                if (body != null) Spacer(Modifier.height(FloatingSurfaceBlockGap))
                            }
                            if (body != null) SurfaceBody(body)
                        } else {
                            if (body != null) {
                                SurfaceBody(body)
                                if (preview != null) Spacer(Modifier.height(FloatingSurfaceBlockGap))
                            }
                            if (preview != null) SurfacePreview(preview)
                        }
                    }
                }
            }
        }
        val view = FloatingTouchInterceptor(windowContext).apply {
            addView(
                compose,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
            )
            onTouch = { event -> event.actionMasked == MotionEvent.ACTION_OUTSIDE }
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

    @Composable
    private fun SurfaceBody(body: FloatingSurfaceBody) {
        val density = LocalDensity.current
        when (body) {
            is FloatingSurfaceBody.Card -> FloatingEventCard(
                card = body.card,
                sessionTitle = body.sessionTitle,
                queueSize = body.queueSize,
                maxHeight = with(density) { body.maxHeightPx.toDp() },
                onAnswerQuestion = ::answerQuestion,
                onOpenQuestion = { pending -> openSession(pending.sessionId) },
                onOpenCompleted = { completion ->
                    feed.removeCompleted(completion.key)
                    openSession(completion.sessionId)
                },
                onOpenTracking = { subscription -> openSession(subscription.sessionId) },
                onOpenSetup = onOpenApp,
                onDismiss = { feed.dismissCard(body.card.key) },
            )

            is FloatingSurfaceBody.Panel -> FloatingPanel(
                runs = body.runs,
                questions = body.questions,
                completed = body.completed,
                now = body.now,
                maxHeight = with(density) { body.maxHeightPx.toDp() },
                subscribedSessionId = body.subscribedSessionId,
                onOpenRun = { run -> openSessionInApp(run.sessionId) },
                onOpenQuestion = { pending -> openSession(pending.sessionId) },
                onOpenCompleted = { completion ->
                    feed.removeCompleted(completion.key)
                    openSessionInApp(completion.sessionId)
                },
                onRemoveCompleted = { completion -> feed.removeCompleted(completion.key) },
                onSubscribe = { sessionId -> feed.subscribe(sessionId) },
                onCollapse = { panelOpen.value = false },
            )
        }
    }

    @Composable
    private fun SurfacePreview(preview: FloatingSurfacePreview) {
        val state by virtualScreenState.collectAsState()
        val frame by previewFrame.collectAsState()
        val density = LocalDensity.current
        FloatingVirtualPreview(
            state = state,
            frame = frame,
            modifier = Modifier
                .width(with(density) { preview.widthPx.toDp() })
                .height(with(density) { preview.heightPx.toDp() }),
        )
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
                val bubbleRing by ring.collectAsState()
                FloatingBubble(mood = state.mood, pending = state.pending, ring = bubbleRing)
            }
        }
        val view = FloatingTouchInterceptor(windowContext).apply {
            addView(
                compose,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            onTouch = ::handleBubbleTouch
            setOnApplyWindowInsetsListener { _, insets ->
                refreshContentFrame()
                insets
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                refreshContentFrame()
            }
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
        refreshContentFrame()
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
                val action = Runnable { onBubbleLongPress() }
                longPressAction = action
                bubbleView?.postDelayed(action, LONG_PRESS_MS)
                val start = Runnable {
                    ring.value = if (previewOpen.value) {
                        FloatingBubbleRing.DRAINING
                    } else {
                        FloatingBubbleRing.FILLING
                    }
                }
                ringStartAction = start
                bubbleView?.postDelayed(start, FloatingRingStartDelayMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - touchDownRawX
                val dy = event.rawY - touchDownRawY
                if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop()) {
                    dragging = true
                    cancelLongPress()
                    fadeRing()
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
                } else {
                    fadeRing()
                    if (dragging) {
                        finishBubbleDrag()
                    } else {
                        onBubbleClick()
                    }
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                fadeRing()
                if (dragging) finishBubbleDrag()
                dragging = false
                longPressFired = false
                return true
            }
        }
        return false
    }

    private fun onBubbleClick() {
        panelOpen.value = true
    }

    private fun onBubbleLongPress() {
        longPressFired = true
        fadeRing()
        previewOpen.value = !previewOpen.value
    }

    private fun fadeRing() {
        if (ring.value == FloatingBubbleRing.NONE) return
        ring.value = FloatingBubbleRing.FADING
        bubbleView?.postDelayed(
            Runnable {
                if (ring.value == FloatingBubbleRing.FADING) ring.value = FloatingBubbleRing.NONE
            },
            FloatingRingResetDelayMs,
        )
    }

    private fun moveBubble(x: Int, y: Int) {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val frame = contentFrame.value
        val clamped = floatingClampBubblePosition(
            x = x,
            y = y,
            bubbleSize = px(FloatingBubbleWindowSize),
            screenWidth = frame.width,
            screenHeight = frame.height,
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
        val frame = contentFrame.value
        val clamped = floatingClampBubblePosition(
            x = bubbleWindowPosition.x,
            y = bubbleWindowPosition.y,
            bubbleSize = px(FloatingBubbleWindowSize),
            screenWidth = frame.width,
            screenHeight = frame.height,
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
        val frame = contentFrame.value
        val settings = settingsState.value
        val size = px(FloatingBubbleWindowSize)
        return if (settings.hasBubblePosition) {
            floatingClampBubblePosition(
                x = settings.bubbleX,
                y = settings.bubbleY,
                bubbleSize = size,
                screenWidth = frame.width,
                screenHeight = frame.height,
                edgeMargin = 0,
            )
        } else {
            floatingDefaultBubblePosition(
                bubbleSize = size,
                screenWidth = frame.width,
                screenHeight = frame.height,
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

    private fun openSessionInApp(sessionId: String) {
        panelOpen.value = false
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(NotificationCenter.EXTRA_SESSION_ID, sessionId)
        if (runCatching { context.startActivity(intent) }.isFailure) onOpenApp()
    }

    private fun cardTitle(card: FloatingEventCard): String = when (card) {
        is FloatingEventCard.Question -> sessionTitle(card.pending.sessionId)
        is FloatingEventCard.PluginSetup -> card.notice.pluginName
        is FloatingEventCard.Completed -> card.completion.title.ifBlank {
            sessionTitle(card.completion.sessionId)
        }

        is FloatingEventCard.Tracking -> card.subscription.title.ifBlank {
            sessionTitle(card.subscription.sessionId)
        }
    }

    private fun sessionTitle(sessionId: String): String {
        val titles = feed.state.value.sessionTitles
        return titles[sessionId].orEmpty()
            .ifBlank { activeRuns.value[sessionId]?.title.orEmpty() }
    }

    private fun refreshVirtualScreen() {
        val controller = runCatching { virtualScreenProvider() }.getOrNull()
        if (controller === virtualScreen) return
        stopPreview()
        virtualScreen = controller
        virtualScreenState.value = controller?.let { floatingStableVirtualScreenState(it.state.value) }
        virtualScreenJob?.cancel()
        virtualScreenJob = null
        if (controller != null) {
            virtualScreenJob = scope.launch {
                controller.state.collect { state ->
                    virtualScreenState.value = floatingStableVirtualScreenState(state)
                }
            }
        }
    }

    private fun updatePreview() {
        val controller = virtualScreen
        if (controller == null || !floatingPreviewEnabled(shown, previewOpen.value)) {
            stopPreview()
            return
        }
        if (previewJob?.isActive == true && previewController === controller) return
        stopPreview()
        previewController = controller
        val targetWidth = px(FloatingVirtualPreviewWidth)
        previewJob = previewScope.launch {
            var lastAcceptedAt = 0L
            controller.state.collect { state ->
                if (state !is VirtualScreenState.Online) {
                    previewFrame.value = null
                    return@collect
                }
                val frame = state.frame ?: return@collect
                val now = SystemClock.elapsedRealtime()
                if (!floatingPreviewAccepted(lastAcceptedAt, now)) return@collect
                val preview = floatingPreviewBitmap(frame, targetWidth) ?: return@collect
                lastAcceptedAt = now
                previewFrame.value = preview.asImageBitmap()
                delay(FLOATING_PREVIEW_FRAME_INTERVAL_MS)
            }
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        previewController = null
        previewFrame.value = null
    }

    private fun refreshContentFrame(ignoreViewFrame: Boolean = false) {
        if (!started) return
        val display = context.resources.displayMetrics
        val frame = metricsContentFrame()
            ?: (if (ignoreViewFrame) null else viewContentFrame())
            ?: floatingFullContentFrame(display.widthPixels, display.heightPixels)
        if (frame == contentFrame.value) return
        contentFrame.value = frame
        clampBubbleIntoContentFrame()
        requestRender()
    }

    private fun viewContentFrame(): FloatingContentFrame? {
        val view = bubbleView ?: return null
        val display = context.resources.displayMetrics
        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return null
        if (rect.width() >= display.widthPixels && rect.height() >= display.heightPixels) return null
        return FloatingContentFrame(
            left = rect.left,
            top = rect.top,
            width = rect.width(),
            height = rect.height(),
        )
    }

    private fun metricsContentFrame(): FloatingContentFrame? {
        if (Build.VERSION.SDK_INT < 30) return null
        val metrics = runCatching { windowManager.currentWindowMetrics }.getOrNull() ?: return null
        val bounds = metrics.bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        return floatingContentFrame(bounds.width(), bounds.height(), metrics.windowInsets.floatingFrameInsets())
    }

    private fun WindowInsets.floatingFrameInsets(): FloatingFrameInsets {
        val insets = WindowInsetsCompat.toWindowInsetsCompat(this)
            .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        return FloatingFrameInsets(insets.left, insets.top, insets.right, insets.bottom)
    }

    private fun clampBubbleIntoContentFrame() {
        if (!shown) return
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val frame = contentFrame.value
        val clamped = floatingClampBubblePosition(
            x = bubbleWindowPosition.x,
            y = bubbleWindowPosition.y,
            bubbleSize = px(FloatingBubbleWindowSize),
            screenWidth = frame.width,
            screenHeight = frame.height,
            edgeMargin = 0,
        )
        if (clamped == bubbleWindowPosition) return
        bubbleWindowPosition = clamped
        params.x = clamped.x
        params.y = clamped.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun cancelLongPress() {
        longPressAction?.let { action -> bubbleView?.removeCallbacks(action) }
        longPressAction = null
        ringStartAction?.let { action -> bubbleView?.removeCallbacks(action) }
        ringStartAction = null
    }

    private fun px(value: Dp): Int = (value.value * context.resources.displayMetrics.density).roundToInt()

    private fun touchSlop(): Float = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private companion object {
        const val SHOW_DELAY_MS = 350L
        const val LONG_PRESS_MS = 480L
        const val TICK_PANEL_MS = 1_000L
        const val TICK_IDLE_MS = 20_000L
    }
}

internal data class FloatingBubbleState(
    val mood: FloatingWhaleMood = FloatingWhaleMood.IDLE,
    val pending: Int = 0,
)

private sealed interface FloatingSurfaceBody {
    data class Card(
        val card: FloatingEventCard,
        val sessionTitle: String,
        val queueSize: Int,
        val maxHeightPx: Int = 0,
    ) : FloatingSurfaceBody

    data class Panel(
        val runs: List<FloatingRun>,
        val questions: List<PendingQuestion>,
        val completed: List<FloatingCompletion>,
        val now: Long,
        val subscribedSessionId: String?,
        val maxHeightPx: Int = 0,
    ) : FloatingSurfaceBody
}

private data class FloatingSurfacePreview(
    val widthPx: Int,
    val heightPx: Int,
)

private data class FloatingSurfaceUi(
    val body: FloatingSurfaceBody? = null,
    val preview: FloatingSurfacePreview? = null,
    val anchoredBottom: Boolean = false,
)

private fun FloatingSurfaceBody.withMaxHeight(maxHeightPx: Int): FloatingSurfaceBody = when (this) {
    is FloatingSurfaceBody.Card -> copy(maxHeightPx = maxHeightPx)
    is FloatingSurfaceBody.Panel -> copy(maxHeightPx = maxHeightPx)
}
