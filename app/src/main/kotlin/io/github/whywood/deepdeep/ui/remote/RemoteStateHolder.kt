package io.github.whywood.deepdeep.ui.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.whywood.deepdeep.data.remote.RemoteComputer
import io.github.whywood.deepdeep.data.remote.RemoteConnection
import io.github.whywood.deepdeep.data.remote.RemoteGatewayException
import io.github.whywood.deepdeep.data.remote.RemoteGatewayFailure
import io.github.whywood.deepdeep.data.remote.RemotePairingLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class RemoteStateHolder(
    private val connection: RemoteConnection,
    private val scope: CoroutineScope,
) {
    val statuses = connection.statuses

    private var refreshing = false

    var scanOpen by mutableStateOf(false)
        private set
    var scanInvalid by mutableStateOf(false)
        private set
    var pasteOpen by mutableStateOf(false)
        private set
    var pasteInvalid by mutableStateOf(false)
        private set
    var pairing by mutableStateOf(false)
        private set
    var pairFailure by mutableStateOf<RemoteGatewayFailure?>(null)
        private set
    var menuFor by mutableStateOf<String?>(null)
        private set
    var renameFor by mutableStateOf<RemoteComputer?>(null)
        private set
    var deleteFor by mutableStateOf<RemoteComputer?>(null)
        private set

    private var lastLink: RemotePairingLink? = null

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            runCatching { connection.refresh() }
            refreshing = false
        }
    }

    fun openScan() {
        scanInvalid = false
        scanOpen = true
    }

    fun dismissScan() {
        scanOpen = false
        scanInvalid = false
    }

    fun onScanned(raw: String, defaultName: String, onPaired: (String) -> Unit) {
        val link = RemotePairingLink.parse(raw)
        if (link == null) {
            scanInvalid = true
            return
        }
        scanOpen = false
        scanInvalid = false
        startPair(link, defaultName, onPaired)
    }

    fun openPaste() {
        pasteInvalid = false
        pasteOpen = true
    }

    fun dismissPaste() {
        pasteOpen = false
        pasteInvalid = false
    }

    fun submitPaste(raw: String, defaultName: String, onPaired: (String) -> Unit) {
        val link = RemotePairingLink.parse(raw)
        if (link == null) {
            pasteInvalid = true
            return
        }
        pasteOpen = false
        pasteInvalid = false
        startPair(link, defaultName, onPaired)
    }

    fun retryPair(defaultName: String, onPaired: (String) -> Unit) {
        val link = lastLink ?: return
        startPair(link, defaultName, onPaired)
    }

    fun dismissPairFailure() {
        pairFailure = null
        lastLink = null
    }

    fun openMenu(instanceId: String) {
        menuFor = instanceId
    }

    fun dismissMenu() {
        menuFor = null
    }

    fun startRename(computer: RemoteComputer) {
        menuFor = null
        renameFor = computer
    }

    fun dismissRename() {
        renameFor = null
    }

    fun confirmRename(name: String) {
        val computer = renameFor ?: return
        val trimmed = name.trim()
        renameFor = null
        if (trimmed.isEmpty() || trimmed == computer.name) return
        scope.launch { runCatching { connection.rename(computer.instanceId, trimmed) } }
    }

    fun startDelete(computer: RemoteComputer) {
        menuFor = null
        deleteFor = computer
    }

    fun dismissDelete() {
        deleteFor = null
    }

    fun confirmDelete() {
        val computer = deleteFor ?: return
        deleteFor = null
        scope.launch { runCatching { connection.remove(computer.instanceId) } }
    }

    private fun startPair(
        link: RemotePairingLink,
        defaultName: String,
        onPaired: (String) -> Unit,
    ) {
        if (pairing) return
        lastLink = link
        pairFailure = null
        pairing = true
        scope.launch {
            runCatching { connection.pair(link, defaultName) }
                .onSuccess { computer ->
                    pairing = false
                    lastLink = null
                    onPaired(computer.instanceId)
                }
                .onFailure { error ->
                    pairing = false
                    pairFailure = (error as? RemoteGatewayException)?.failure
                        ?: RemoteGatewayFailure.Unreachable
                }
        }
    }
}
