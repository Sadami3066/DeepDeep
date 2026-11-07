package io.github.whywood.deepdeep.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.core.AppDependencies
import io.github.whywood.deepdeep.runtime.memory.MemoryStatus
import io.github.whywood.deepdeep.ui.memory.MemoryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MemoryRoute(
    dependencies: AppDependencies,
    conversationId: String?,
    onBack: () -> Unit,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<MemoryStatus?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val manualReason = stringResource(R.string.memory_reason_manual)

    suspend fun load() {
        status = withContext(Dispatchers.IO) {
            runCatching { dependencies.memory.status() }.getOrNull()
        }
    }

    LaunchedEffect(dependencies.memory) { load() }

    fun summarizeCurrent() {
        val sessionId = conversationId
        if (sessionId == null) {
            notice = resources.getString(R.string.memory_no_session)
            return
        }
        val messages = dependencies.conversationStore.peek(sessionId)?.messages.orEmpty()
        if (messages.size < 4) {
            notice = resources.getString(R.string.memory_too_short)
            return
        }
        busy = true
        notice = resources.getString(R.string.memory_summarizing)
        scope.launch {
            try {
                dependencies.memoryRecap.summarizeMessages(sessionId, messages, manualReason)
                notice = resources.getString(R.string.memory_summarized)
            } catch (error: Exception) {
                notice = error.message
            } finally {
                busy = false
            }
            load()
        }
    }

    fun rebuildIndex() {
        busy = true
        scope.launch {
            try {
                val chunks = withContext(Dispatchers.IO) { dependencies.memory.rebuildIndex() }
                notice = resources.getString(R.string.memory_rebuild_done, chunks)
            } catch (error: Exception) {
                notice = error.message
            } finally {
                busy = false
            }
        }
    }

    MemoryScreen(
        status = status,
        busy = busy,
        notice = notice,
        canSummarize = conversationId != null,
        onSummarize = ::summarizeCurrent,
        onRebuild = ::rebuildIndex,
        onRefresh = {
            notice = null
            scope.launch { load() }
        },
        onBack = onBack,
    )
}
