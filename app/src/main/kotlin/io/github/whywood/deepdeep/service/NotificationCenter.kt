package io.github.whywood.deepdeep.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.whywood.deepdeep.MainActivity
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.conversation.RunCompletion
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.runtime.ConversationQueries
import io.github.whywood.deepdeep.runtime.InterruptedQuestion
import io.github.whywood.deepdeep.runtime.InterruptedRun
import io.github.whywood.deepdeep.runtime.PendingQuestion
import io.github.whywood.deepdeep.runtime.RunStatus
import io.github.whywood.deepdeep.runtime.RuntimeActivity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotificationCenter(
    private val context: Context,
    private val runtime: ConversationQueries,
    private val store: ConversationStore,
    private val pendingQuestions: StateFlow<List<PendingQuestion>>,
) {
    private val manager = NotificationManagerCompat.from(context)
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val postedQuestionSessions = ConcurrentHashMap.newKeySet<String>()
    private var collector: Job? = null
    private var questionCollector: Job? = null
    @Volatile
    private var foregroundStarted = false

    fun start() {
        if (collector != null) return
        createChannels()
        collector = scope.launch {
            store.runCompletions.collect(::onRunCompletion)
        }
        questionCollector = scope.launch {
            pendingQuestions.collect(::onPendingQuestions)
        }
    }

    fun stop() {
        collector?.cancel()
        collector = null
        questionCollector?.cancel()
        questionCollector = null
        clearQuestionNotifications()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        foregroundStarted = false
        manager.cancel(FOREGROUND_ID)
    }

    fun onForegroundStarted() {
        foregroundStarted = true
        updateForeground()
    }

    fun refreshQuestionNotifications() {
        scope.launch { onPendingQuestions(pendingQuestions.value) }
    }

    fun clearQuestionNotifications() {
        postedQuestionSessions.toList().forEach { sessionId ->
            manager.cancel(questionId(sessionId))
        }
        postedQuestionSessions.clear()
    }

    private suspend fun onPendingQuestions(pending: List<PendingQuestion>) {
        if (AppVisibility.foreground || pending.isEmpty()) {
            clearQuestionNotifications()
            return
        }
        val grouped = pending.groupBy { it.sessionId }
        val stale = postedQuestionSessions - grouped.keys
        stale.forEach { sessionId ->
            manager.cancel(questionId(sessionId))
            postedQuestionSessions -= sessionId
        }
        grouped.forEach { (sessionId, questions) ->
            postQuestion(sessionId, questions)
        }
    }

    private suspend fun postQuestion(sessionId: String, questions: List<PendingQuestion>) {
        val title = runCatching { runtime.sessionTitle(sessionId) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        val body = questionNotificationBody(questions.first())
        val builder = NotificationCompat.Builder(context, CHANNEL_QUESTIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_question_title, title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(sessionId))
            .setAutoCancel(true)
            .setGroup(GROUP_SESSIONS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                0,
                context.getString(R.string.notification_question_action),
                openAppIntent(sessionId),
            )
            .addAction(
                0,
                context.getString(R.string.notification_action_stop),
                stopSessionIntent(sessionId),
            )
        post(questionId(sessionId), builder.build())
        postedQuestionSessions += sessionId
    }

    fun foregroundNotification(): android.app.Notification {
        val active = store.activeRuns.value
        val latest = active.values.maxByOrNull { it.startedAt }
        val title = when {
            active.isEmpty() -> context.getString(R.string.app_name)
            active.size == 1 -> latest?.title.orEmpty().ifBlank { context.getString(R.string.app_name) }
            else -> context.getString(R.string.notification_sessions_running, active.size)
        }
        val content = when {
            active.isEmpty() -> context.getString(R.string.notification_service_idle)
            latest == null -> context.getString(R.string.notification_activity_thinking)
            else -> activityText(latest.activity)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNTIME)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppIntent(latest?.sessionId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (latest != null) {
            builder.addAction(
                0,
                context.getString(R.string.notification_action_stop),
                stopSessionIntent(latest.sessionId),
            )
        }
        return builder.build()
    }

    fun notifyInstall(label: String, stage: String?, progress: Int?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_download_title))
            .setContentText(stage ?: label)
            .setContentIntent(openAppIntent(null))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress ?: 0, progress == null)
        post(INSTALL_ID, builder.build())
    }

    fun notifyInstallFinished(label: String, success: Boolean, error: String? = null) {
        manager.cancel(INSTALL_ID)
        val text = if (success) {
            context.getString(R.string.notification_download_done, label)
        } else {
            context.getString(R.string.notification_download_failed, error?.take(160).orEmpty())
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_download_title))
            .setContentText(text)
            .setContentIntent(openAppIntent(null))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        post(INSTALL_RESULT_ID, builder.build())
    }

    fun clearInstallNotification() {
        manager.cancel(INSTALL_ID)
        manager.cancel(INSTALL_RESULT_ID)
    }

    fun notifyAccessibilityLost() {
        val text = context.getString(R.string.notification_accessibility_lost_text)
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_accessibility_lost_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(accessibilitySettingsIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        post(ACCESSIBILITY_ID, builder.build())
    }

    fun notifyInterruptedWork(questions: List<InterruptedQuestion>, runs: List<InterruptedRun>) {
        val sessionId = interruptedNotificationTarget(questions, runs) ?: return
        val text = context.getString(R.string.notification_interrupted_text, interruptedNotificationCount(questions, runs))
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notification_interrupted_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(sessionId))
            .setAutoCancel(true)
            .setGroup(GROUP_SESSIONS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        post(INTERRUPTED_ID, builder.build())
    }

    private fun accessibilitySettingsIntent(): PendingIntent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun onRunCompletion(change: RunCompletion) {
        updateForeground()
        postResult(
            sessionId = change.sessionId,
            title = change.title,
            status = change.status,
            text = change.text,
            error = change.error,
        )
    }

    private fun updateForeground() {
        if (!foregroundStarted) return
        post(FOREGROUND_ID, foregroundNotification())
    }

    private fun postResult(
        sessionId: String,
        title: String,
        status: RunStatus,
        text: String,
        error: String?,
    ) {
        if (AppVisibility.foreground) return
        val line = when (status) {
            RunStatus.DONE -> context.getString(R.string.notification_result_done)
            RunStatus.STOPPED -> context.getString(R.string.notification_result_stopped)
            RunStatus.FAILED -> context.getString(R.string.notification_result_failed, error?.take(160).orEmpty())
        }
        val preview = text.trim().take(600)
        val builder = NotificationCompat.Builder(context, CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(line)
            .setContentIntent(openAppIntent(sessionId))
            .setAutoCancel(true)
            .setGroup(GROUP_SESSIONS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (preview.isNotEmpty()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText("$line\n$preview"))
        }
        post(resultId(sessionId), builder.build())
    }

    private fun activityText(activity: RuntimeActivity?): String = when (activity) {
        null -> context.getString(R.string.notification_activity_thinking)
        is RuntimeActivity.Thinking -> context.getString(R.string.notification_activity_thinking)
        is RuntimeActivity.Retrying -> context.getString(R.string.notification_activity_retrying, activity.attempt)
        is RuntimeActivity.Compacting -> context.getString(R.string.notification_activity_compacting)
        is RuntimeActivity.CompactionFailed -> context.getString(R.string.notification_activity_compacting)
        is RuntimeActivity.RunningTool -> context.getString(R.string.notification_activity_tool, activity.name)
    }

    private fun openAppIntent(sessionId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!sessionId.isNullOrBlank()) putExtra(EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getActivity(
            context,
            sessionId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopSessionIntent(sessionId: String): PendingIntent {
        val intent = Intent(context, AgentService::class.java).apply {
            action = AgentService.ACTION_STOP_SESSION
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getService(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun resultId(sessionId: String): Int = 1000 + (sessionId.hashCode() and 0x3FFFF)

    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        runCatching { manager.notify(id, notification) }
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RUNTIME,
                context.getString(R.string.notification_channel_runtime),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_runtime_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULTS,
                context.getString(R.string.notification_channel_results),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_results_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUESTIONS,
                context.getString(R.string.notification_channel_questions),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_questions_desc)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_downloads_desc)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val EXTRA_SESSION_ID = "io.github.whywood.deepdeep.extra.SESSION_ID"
        private const val CHANNEL_RUNTIME = "agent_runtime"
        private const val CHANNEL_RESULTS = "session_results"
        private const val CHANNEL_QUESTIONS = "agent_questions"
        private const val CHANNEL_DOWNLOADS = "downloads"
        private const val FOREGROUND_ID = 1
        private const val INSTALL_ID = 200
        private const val INSTALL_RESULT_ID = 201
        private const val ACCESSIBILITY_ID = 300
        private const val INTERRUPTED_ID = 400
        private const val QUESTION_ID_BASE = 500
        private const val GROUP_SESSIONS = "deepdeep_sessions"

        private fun questionId(sessionId: String): Int = QUESTION_ID_BASE + (sessionId.hashCode() and 0x3FFFF)

        fun areNotificationsEnabled(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

internal fun interruptedNotificationCount(
    questions: List<InterruptedQuestion>,
    runs: List<InterruptedRun>,
): Int = questions.size + runs.size

internal fun interruptedNotificationTarget(
    questions: List<InterruptedQuestion>,
    runs: List<InterruptedRun>,
): String? {
    val question = questions.minByOrNull { it.createdAt }
    val run = runs.minByOrNull { it.startedAt }
    return when {
        question == null -> run?.sessionId
        run == null -> question.sessionId
        question.createdAt <= run.startedAt -> question.sessionId
        else -> run.sessionId
    }
}

internal fun questionNotificationBody(question: PendingQuestion): String {
    val first = question.questions.firstOrNull() ?: return ""
    val header = first.header?.takeIf { it.isNotBlank() }
    val text = first.question.trim()
    val line = if (header == null) text else "$header · $text"
    return if (line.length <= 160) line else line.take(160).trimEnd() + "…"
}
