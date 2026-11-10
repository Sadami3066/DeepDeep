package io.github.whywood.deepdeep.graph

import android.content.Context
import io.github.whywood.deepdeep.conversation.ConversationStore
import io.github.whywood.deepdeep.data.SkillRepository
import io.github.whywood.deepdeep.plugin.manager.PluginManagerFacade
import io.github.whywood.deepdeep.runtime.AgentRuntimeHost
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.runtime.QuestionCenter
import io.github.whywood.deepdeep.runtime.TimerStore
import io.github.whywood.deepdeep.shell.LinuxEnvironment
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
    val linuxEnvironment: LinuxEnvironment,
    val pluginManager: () -> PluginManagerFacade,
    val skillRepository: SkillRepository,
    val installBundledSkills: suspend () -> Unit,
    val prepare: suspend () -> Unit,
    val jobWake: StateFlow<Boolean>,
    val hostEnvironment: HostEnvironmentProvider,
)
