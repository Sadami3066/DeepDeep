package io.github.whywood.deepdeep.ui.common

import dev.pi.ai.RunSignal
import dev.pi.ai.auth.oauth.OAuthInteraction
import dev.pi.ai.auth.oauth.OAuthSelectOption
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

data class OAuthPrompt(
    val message: String,
    val placeholder: String? = null,
    val secret: Boolean = false,
)

data class OAuthLoginState(
    val message: String? = null,
    val url: String? = null,
    val instructions: String? = null,
    val prompt: OAuthPrompt? = null,
    val options: List<OAuthSelectOption> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
    val finished: Boolean = false,
) {
    val waiting: Boolean
        get() = busy && url != null && prompt == null && options.isEmpty() && error == null && !finished
}

class OAuthLoginSession(
    val providerName: String,
    private val onOpenUrl: (String) -> Unit,
    private val onStateChanged: () -> Unit = {},
) : OAuthInteraction {
    private val mutableState = MutableStateFlow(OAuthLoginState(busy = true))
    val state: StateFlow<OAuthLoginState> = mutableState.asStateFlow()

    override val signal: RunSignal = RunSignal()

    private val lock = Any()
    private var promptWaiter: CancellableContinuation<String>? = null
    private var selectWaiter: CancellableContinuation<String>? = null
    private var selectChoices: List<OAuthSelectOption> = emptyList()

    override suspend fun openUrl(url: String, instructions: String?) {
        if (mutableState.value.finished) return
        update { it.copy(url = url, instructions = instructions) }
        onOpenUrl(url)
    }

    override suspend fun showMessage(message: String) {
        val stale = synchronized(lock) {
            if (mutableState.value.finished) return
            val stale = takeWaitersLocked()
            mutableState.value = mutableState.value.copy(
                message = message,
                prompt = null,
                options = emptyList(),
            )
            stale
        }
        notifyStateChanged()
        stale.cancel()
    }

    override suspend fun prompt(message: String, placeholder: String?, secret: Boolean): String =
        suspendCancellableCoroutine { continuation ->
            val stale = synchronized(lock) {
                if (mutableState.value.finished) {
                    null
                } else {
                    val stale = takeWaitersLocked()
                    promptWaiter = continuation
                    mutableState.value = mutableState.value.copy(
                        prompt = OAuthPrompt(message = message, placeholder = placeholder, secret = secret),
                        options = emptyList(),
                    )
                    stale
                }
            }
            if (stale == null) {
                continuation.cancel()
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                synchronized(lock) {
                    if (promptWaiter === continuation) promptWaiter = null
                }
            }
            notifyStateChanged()
            stale.cancel()
        }

    override suspend fun select(message: String, options: List<OAuthSelectOption>): String =
        suspendCancellableCoroutine { continuation ->
            val stale = synchronized(lock) {
                if (mutableState.value.finished) {
                    null
                } else {
                    val stale = takeWaitersLocked()
                    selectWaiter = continuation
                    selectChoices = options
                    mutableState.value = mutableState.value.copy(
                        message = message,
                        prompt = null,
                        options = options,
                    )
                    stale
                }
            }
            if (stale == null) {
                continuation.cancel()
                return@suspendCancellableCoroutine
            }
            continuation.invokeOnCancellation {
                synchronized(lock) {
                    if (selectWaiter === continuation) {
                        selectWaiter = null
                        selectChoices = emptyList()
                    }
                }
            }
            notifyStateChanged()
            stale.cancel()
        }

    fun submitPrompt(text: String): Boolean {
        val waiter = synchronized(lock) {
            val current = promptWaiter
            if (current == null || mutableState.value.finished) {
                null
            } else {
                promptWaiter = null
                mutableState.value = mutableState.value.copy(prompt = null)
                current
            }
        } ?: return false
        notifyStateChanged()
        waiter.resume(text) { _, _, _ -> }
        return true
    }

    fun choose(id: String): Boolean {
        val waiter = synchronized(lock) {
            val current = selectWaiter
            if (current == null || mutableState.value.finished || selectChoices.none { it.id == id }) {
                null
            } else {
                selectWaiter = null
                selectChoices = emptyList()
                mutableState.value = mutableState.value.copy(message = null, options = emptyList())
                current
            }
        } ?: return false
        notifyStateChanged()
        waiter.resume(id) { _, _, _ -> }
        return true
    }

    fun fail(message: String) {
        val stale = synchronized(lock) {
            if (mutableState.value.finished) return
            val stale = takeWaitersLocked()
            mutableState.value = mutableState.value.copy(
                error = message,
                busy = false,
                prompt = null,
                options = emptyList(),
            )
            stale
        }
        notifyStateChanged()
        stale.cancel()
    }

    fun cancel() {
        val stale = synchronized(lock) {
            if (mutableState.value.finished) return
            val stale = takeWaitersLocked()
            mutableState.value = mutableState.value.copy(
                error = CANCELLED,
                busy = false,
                finished = true,
                prompt = null,
                options = emptyList(),
            )
            stale
        }
        signal.abort(CANCELLED)
        notifyStateChanged()
        stale.cancel()
    }

    fun succeed() {
        synchronized(lock) {
            if (mutableState.value.finished) return
            takeWaitersLocked()
            mutableState.value = mutableState.value.copy(
                error = null,
                busy = false,
                finished = true,
                prompt = null,
                options = emptyList(),
            )
        }
        notifyStateChanged()
    }

    private fun update(transform: (OAuthLoginState) -> OAuthLoginState) {
        synchronized(lock) {
            mutableState.value = transform(mutableState.value)
        }
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        onStateChanged()
    }

    private fun takeWaitersLocked(): Waiters {
        val waiters = Waiters(promptWaiter, selectWaiter)
        promptWaiter = null
        selectWaiter = null
        selectChoices = emptyList()
        return waiters
    }

    private class Waiters(
        private val prompt: CancellableContinuation<String>?,
        private val select: CancellableContinuation<String>?,
    ) {
        fun cancel() {
            prompt?.cancel()
            select?.cancel()
        }
    }

    private companion object {
        const val CANCELLED = "cancelled"
    }
}
