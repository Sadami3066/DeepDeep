package io.github.whywood.deepdeep.plugin.manager

import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.plugin.PluginStrings
import io.github.whywood.deepdeep.plugin.api.LocalizedText
import io.github.whywood.deepdeep.plugin.api.PluginProgress
import io.github.whywood.deepdeep.plugin.api.PluginSetupUi
import io.github.whywood.deepdeep.plugin.api.PluginStatus
import io.github.whywood.deepdeep.plugin.host.PluginPhase
import io.github.whywood.deepdeep.plugin.host.PluginTextResolver
import io.github.whywood.deepdeep.runtime.PluginSetupResult
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class PluginSetupFlow(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<PluginsUiState>,
    private val strings: PluginStrings,
    private val textResolver: PluginTextResolver,
    private val busy: AtomicBoolean,
    private val managedPlugin: (String) -> ManagedPlugin?,
    private val isForeground: () -> Boolean,
    private val builtinStateStore: BuiltinPluginStateStore,
    private val notifyInstall: (String, String?, Int?) -> Unit,
    private val notifyInstallFinished: (String, Boolean, String?) -> Unit,
    private val refresh: suspend () -> Unit,
    private val syncSkills: suspend (ManagedPlugin) -> Unit,
    private val onContributionsChanged: () -> Unit,
    private val onSetupReady: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onOperation: (String, String, String) -> Unit,
) {
    private val confirmWaiter = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val setupCancelled = AtomicBoolean(false)
    private val setupFinished = AtomicBoolean(false)
    private val setupJob = AtomicReference<Job?>(null)
    private val setupPluginId = AtomicReference<String?>(null)

    fun install(pluginId: String) {
        scope.launch { withBusy { installManaged(pluginId) } }
    }

    fun runAction(pluginId: String, actionId: String) {
        scope.launch {
            setupCancelled.set(false)
            withBusy {
                val managed = managedPlugin(pluginId) ?: return@withBusy
                if (!managed.enabled) {
                    onError(strings.get(R.string.plugins_error_disabled))
                    return@withBusy
                }
                val plugin = managed.plugin ?: return@withBusy
                val call = scope.async { plugin.runAction(managed.host, actionId, setupUi(managed)) }
                setupJob.set(call)
                try {
                    call.await()
                } catch (error: CancellationException) {
                    call.cancel()
                    if (!setupCancelled.get()) throw error
                } finally {
                    setupJob.compareAndSet(call, null)
                }
            }
        }
    }

    fun onAppForeground() {
        if (!busy.get()) return
        val interaction = state.value.interaction ?: return
        val managed = managedPlugin(interaction.pluginId) ?: return
        scope.launch {
            val status = runCatching { managed.plugin?.status(managed.host) }.getOrNull()
            if (status is PluginStatus.Ready) {
                completeConfirm(true)
                setupFinished.set(true)
                setupJob.getAndSet(null)?.cancel()
                state.update { it.copy(interaction = null) }
                onSetupReady(interaction.title)
            }
        }
    }

    fun confirmInstall() {
        completeConfirm(true)
    }

    fun cancelInteraction() {
        setupCancelled.set(true)
        setupJob.getAndSet(null)?.cancel()
        completeConfirm(false)
        state.update { it.copy(interaction = null) }
    }

    fun cancelSetup(pluginId: String) {
        if (setupPluginId.get() != pluginId) return
        cancelInteraction()
    }

    suspend fun setup(pluginId: String): PluginSetupResult {
        setupPluginId.set(pluginId)
        setupCancelled.set(false)
        setupFinished.set(false)
        return try {
            setupInternal(pluginId)
        } finally {
            setupPluginId.compareAndSet(pluginId, null)
        }
    }

    private suspend fun installManaged(pluginId: String) {
        val managed = managedPlugin(pluginId) ?: return
        if (!PluginRules.canInstall(managed.manifest)) return
        installInternal(managed)
        refresh()
        syncSkills(managed)
        onContributionsChanged()
    }

    private suspend fun setupInternal(pluginId: String): PluginSetupResult {
        val managed = managedPlugin(pluginId)
            ?: return PluginSetupResult(false, pluginId, false, false, error = strings.get(R.string.plugins_error_unknown))
        val plugin = managed.plugin
            ?: return PluginSetupResult(false, pluginId, false, false, error = managed.error)
        if (!managed.enabled) {
            return PluginSetupResult(false, pluginId, managed.installedVersion != null, false, error = strings.get(R.string.plugins_error_disabled))
        }
        if (!isForeground()) {
            return PluginSetupResult(false, pluginId, managed.installedVersion != null, false, error = strings.get(R.string.plugins_error_foreground))
        }
        if (managed.manifest.installable && managed.installedVersion == null && managed.builtin) {
            if (!beginBusy()) {
                return PluginSetupResult(false, pluginId, false, false, error = strings.get(R.string.plugins_error_busy))
            }
            try {
                if (!awaitInstall(managed)) {
                    return PluginSetupResult(false, pluginId, false, false, cancelled = setupCancelled.get())
                }
                val installCall = scope.async { installInternal(managed) }
                setupJob.set(installCall)
                try {
                    installCall.await()
                } catch (error: CancellationException) {
                    installCall.cancel()
                    if (!setupCancelled.get()) throw error
                    return PluginSetupResult(false, pluginId, false, false, cancelled = true)
                } finally {
                    setupJob.compareAndSet(installCall, null)
                }
            } finally {
                finishBusy()
            }
        }
        val current = runCatching { plugin.status(managed.host) }.getOrNull()
        if (current is PluginStatus.Ready) {
            refresh()
            return PluginSetupResult(true, pluginId, true, true, current.label, current.account)
        }
        if (!beginBusy()) {
            return PluginSetupResult(false, pluginId, true, false, error = strings.get(R.string.plugins_error_busy))
        }
        val call = scope.async { plugin.setup(managed.host, setupUi(managed)) }
        setupJob.set(call)
        var cancelled = false
        return try {
            try {
                call.await()
                if (setupCancelled.get()) cancelled = true
            } catch (error: CancellationException) {
                call.cancel()
                when {
                    setupCancelled.get() -> {
                        cancelled = true
                        if (!currentCoroutineContext().isActive) throw error
                    }

                    setupFinished.get() -> Unit
                    else -> throw error
                }
            }
            refresh()
            val status = runCatching { plugin.status(managed.host) }.getOrNull()
            when {
                cancelled -> PluginSetupResult(false, pluginId, true, false, cancelled = true)
                status is PluginStatus.Ready -> PluginSetupResult(true, pluginId, true, true, status.label, status.account)
                status is PluginStatus.NotReady -> PluginSetupResult(false, pluginId, true, false, status.label)
                status is PluginStatus.Failed -> PluginSetupResult(false, pluginId, true, false, status.label, error = status.label)
                else -> PluginSetupResult(false, pluginId, true, false, error = strings.get(R.string.plugins_error_status))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PluginSetupResult(false, pluginId, true, false, error = error.message)
        } finally {
            setupJob.compareAndSet(call, null)
            setupFinished.set(false)
            finishBusy()
        }
    }

    private suspend fun installInternal(managed: ManagedPlugin) {
        val plugin = managed.plugin ?: throw IllegalStateException(managed.error ?: "plugin unavailable")
        val label = textResolver.resolve(managed.manifest.name)
        val firstInstall = managed.installedVersion == null
        onOperation(managed.manifest.id, label, strings.get(R.string.plugins_stage_preparing))
        notifyInstall(label, strings.get(R.string.plugins_stage_preparing), null)
        try {
            plugin.install(managed.host, progressUi(managed, label))
            managed.installedVersion = managed.manifest.version
            managed.phase = PluginPhase.INSTALLED
            managed.error = null
            if (managed.builtin) {
                builtinStateStore.upsert(
                    managed.manifest.id,
                    BuiltinPluginState(true, managed.enabled, managed.manifest.version),
                )
            }
            notifyInstallFinished(label, true, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            managed.error = error.message
            if (firstInstall) runCatching { plugin.uninstall(managed.host) }
            notifyInstallFinished(label, false, error.message)
            throw error
        } finally {
            state.update { it.copy(operation = null) }
        }
    }

    private fun setupUi(managed: ManagedPlugin): PluginSetupUi = object : PluginSetupUi {
        override fun stage(
            title: LocalizedText,
            message: LocalizedText?,
            url: String?,
            imagePath: String?,
            progress: Int?,
        ) {
            val titleText = textResolver.resolve(title)
            state.update {
                it.copy(
                    interaction = PluginInteraction(
                        pluginId = managed.manifest.id,
                        title = textResolver.resolve(managed.manifest.name),
                        stageTitle = titleText,
                        stageMessage = message?.let(textResolver::resolve),
                        url = url,
                        imagePath = imagePath,
                        progress = progress,
                    ),
                )
            }
        }

        override fun progress(percent: Int?, label: LocalizedText) {
            state.update {
                it.copy(
                    interaction = it.interaction?.copy(
                        progress = percent,
                        stageMessage = textResolver.resolve(label),
                    ),
                )
            }
        }

        override fun cancelled(): Boolean = setupCancelled.get()
    }

    private fun progressUi(managed: ManagedPlugin, label: String): PluginProgress =
        object : PluginProgress {
            override fun report(percent: Int?, label: LocalizedText) {
                val stage = textResolver.resolve(label)
                state.update { current ->
                    current.copy(
                        operation = PluginOperation(managed.manifest.id, label(managed), stage, percent),
                        interaction = current.interaction?.let { interaction ->
                            if (interaction.pluginId == managed.manifest.id) {
                                interaction.copy(stageTitle = stage, progress = percent)
                            } else {
                                interaction
                            }
                        },
                    )
                }
                notifyInstall(label(managed), stage, percent)
            }
        }

    private fun label(managed: ManagedPlugin): String = textResolver.resolve(managed.manifest.name)

    private suspend fun awaitInstall(managed: ManagedPlugin): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmWaiter.set(deferred)
        state.update {
            it.copy(
                interaction = PluginInteraction(
                    pluginId = managed.manifest.id,
                    title = textResolver.resolve(managed.manifest.name),
                    stageTitle = strings.get(R.string.plugins_stage_confirm),
                    confirmInstall = true,
                ),
                busy = true,
            )
        }
        val confirmed = try {
            deferred.await()
        } finally {
            confirmWaiter.set(null)
        }
        state.update { current ->
            current.copy(
                interaction = if (confirmed) {
                    current.interaction?.copy(
                        confirmInstall = false,
                        stageTitle = strings.get(R.string.plugins_stage_installing),
                        stageMessage = null,
                    )
                } else {
                    null
                },
            )
        }
        return confirmed
    }

    private fun completeConfirm(value: Boolean) {
        confirmWaiter.getAndSet(null)?.complete(value)
    }

    private suspend fun beginBusy(): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        state.update { it.copy(busy = true, error = null) }
        return true
    }

    private fun finishBusy() {
        busy.set(false)
        state.update { it.copy(busy = false, operation = null, interaction = null) }
    }

    private suspend fun withBusy(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) {
            onError(strings.get(R.string.plugins_error_busy))
            return
        }
        state.update { it.copy(busy = true, error = null) }
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error.message ?: error.javaClass.simpleName)
        } finally {
            busy.set(false)
            state.update { it.copy(busy = false, operation = null, interaction = null) }
            runCatching { refresh() }
            onContributionsChanged()
        }
    }
}
