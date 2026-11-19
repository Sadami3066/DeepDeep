package io.github.whywood.superpi.graph

import android.content.Context
import io.github.whywood.superpi.conversation.ConversationStore
import io.github.whywood.superpi.data.SkillRepository
import io.github.whywood.superpi.plugin.manager.PluginManagerFacade
import io.github.whywood.superpi.runtime.AgentRuntimeHost
import io.github.whywood.superpi.runtime.host.HostEnvironmentProvider
import io.github.whywood.superpi.runtime.QuestionCenter
import io.github.whywood.superpi.runtime.TimerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal class ServiceGraphDeps(
    val appContext: Context,
    val uiContext: Context,
    val scope: CoroutineScope,
    val host: () -> AgentRuntimeHost,
    val conversationStore: () -> ConversationStore,
    val questionCenter: QuestionCenter,
    val timerStore: TimerStore,
    val pluginManager: () -> PluginManagerFacade,
    val skillRepository: SkillRepository,
    val installBundledSkills: suspend () -> Unit,
    val prepare: suspend () -> Unit,
    val jobWake: StateFlow<Boolean>,
    val hostEnvironment: HostEnvironmentProvider,
    val devPackagesCwd: () -> String,
    val devPackagesReady: () -> Boolean,
)
