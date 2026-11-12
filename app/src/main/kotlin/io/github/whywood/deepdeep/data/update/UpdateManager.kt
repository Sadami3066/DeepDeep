package io.github.whywood.deepdeep.data.update

import io.github.whywood.deepdeep.sdk.ai.http.HttpRequest
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

sealed interface UpdatePhase {
    data object Idle : UpdatePhase

    data object Checking : UpdatePhase

    data object UpToDate : UpdatePhase

    data class Available(val release: UpdateRelease) : UpdatePhase

    data class Downloading(val release: UpdateRelease, val progress: Float) : UpdatePhase

    data class ReadyToInstall(val release: UpdateRelease, val needsPermission: Boolean) : UpdatePhase

    data class Failed(val failure: UpdateFailure, val release: UpdateRelease? = null) : UpdatePhase
}

data class UpdateUi(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val lastCheckedAt: Long? = null,
)

val UpdatePhase.releaseOrNull: UpdateRelease?
    get() = when (this) {
        is UpdatePhase.Available -> release
        is UpdatePhase.Downloading -> release
        is UpdatePhase.ReadyToInstall -> release
        is UpdatePhase.Failed -> release
        else -> null
    }

val UpdateUi.hasUpdate: Boolean
    get() = phase is UpdatePhase.Available ||
        phase is UpdatePhase.Downloading ||
        phase is UpdatePhase.ReadyToInstall

class UpdateManager(
    private val transport: HttpTransport,
    private val store: UpdateStore,
    private val installer: UpdateInstaller,
    private val updatesDir: File,
    private val scope: CoroutineScope,
    private val currentVersionName: String,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
    private val _ui = MutableStateFlow(UpdateUi())
    val ui: StateFlow<UpdateUi> = _ui.asStateFlow()

    private val hydrated = CompletableDeferred<Unit>()
    private val checkMutex = Mutex()

    @Volatile
    private var checking = false

    @Volatile
    private var downloading = false

    @Volatile
    private var currentRelease: UpdateRelease? = null

    @Volatile
    private var awaitingInstallPermission = false

    init {
        scope.launch { hydrate() }
    }

    fun maybeAutoCheck() {
        scope.launch {
            hydrated.await()
            val snapshot = readSnapshot()
            val now = System.currentTimeMillis()
            if (!UpdatePolicy.shouldAutoCheck(now, snapshot.lastSuccessAt, snapshot.lastAttemptAt, checking || downloading)) {
                return@launch
            }
            runCheck(showChecking = false)
        }
    }

    fun onPageOpened() {
        scope.launch {
            hydrated.await()
            val phase = _ui.value.phase
            if (checking || downloading || phase is UpdatePhase.Downloading || phase is UpdatePhase.ReadyToInstall) {
                return@launch
            }
            val lastCheckedAt = readSnapshot().lastSuccessAt
            val stale = lastCheckedAt == null ||
                System.currentTimeMillis() - lastCheckedAt >= UpdatePolicy.PAGE_REFRESH_INTERVAL_MS
            if (stale) {
                runCheck(showChecking = phase is UpdatePhase.Idle)
            }
        }
    }

    fun checkManually() {
        scope.launch {
            hydrated.await()
            if (downloading) return@launch
            runCheck(showChecking = true)
        }
    }

    fun retry() {
        val phase = _ui.value.phase
        if (phase is UpdatePhase.Failed && phase.release != null) {
            download()
        } else {
            checkManually()
        }
    }

    fun download() {
        scope.launch {
            hydrated.await()
            if (checking || downloading) return@launch
            val release = currentRelease ?: return@launch
            downloading = true
            try {
                downloadRelease(release)
            } finally {
                downloading = false
            }
        }
    }

    fun install() {
        val release = currentRelease ?: return
        val apk = apkFile(release)
        if (!apk.isFile) {
            _ui.value = _ui.value.copy(phase = UpdatePhase.Failed(UpdateFailure.VERIFY, release))
            return
        }
        if (!installer.canInstallPackages()) {
            awaitingInstallPermission = true
            _ui.value = _ui.value.copy(phase = UpdatePhase.ReadyToInstall(release, needsPermission = true))
            return
        }
        awaitingInstallPermission = false
        if (!installer.isInstallableArchive(apk) || !installer.install(apk)) {
            _ui.value = _ui.value.copy(phase = UpdatePhase.Failed(UpdateFailure.INSTALL, release))
            onFailure("update: install failed", IllegalStateException("installer unavailable"))
        }
    }

    fun openInstallPermission() {
        awaitingInstallPermission = true
        installer.openInstallPermissionSettings()
    }

    fun onScreenResumed() {
        val phase = _ui.value.phase as? UpdatePhase.ReadyToInstall ?: return
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        if (!installer.canInstallPackages()) {
            _ui.value = _ui.value.copy(phase = phase.copy(needsPermission = true))
            return
        }
        _ui.value = _ui.value.copy(phase = phase.copy(needsPermission = false))
        install()
    }

    private suspend fun hydrate() {
        try {
            val snapshot = readSnapshot()
            val release = snapshot.release?.takeIf { UpdatePolicy.isNewer(it.versionName, currentVersionName) }
            currentRelease = release
            if (release == null && snapshot.release != null) {
                writeSnapshot(snapshot.copy(release = null))
            }
            pruneUpdates(keep = release)
            val phase = when {
                release == null -> if (snapshot.lastSuccessAt != null) UpdatePhase.UpToDate else UpdatePhase.Idle
                else -> readyToInstall(release) ?: UpdatePhase.Available(release)
            }
            _ui.value = UpdateUi(phase, snapshot.lastSuccessAt)
        } finally {
            hydrated.complete(Unit)
        }
    }

    private fun readyToInstall(release: UpdateRelease): UpdatePhase.ReadyToInstall? {
        val apk = apkFile(release)
        if (!apk.isFile) return null
        if (!installer.isInstallableArchive(apk)) {
            apk.delete()
            return null
        }
        return UpdatePhase.ReadyToInstall(release, needsPermission = !installer.canInstallPackages())
    }

    private fun pruneUpdates(keep: UpdateRelease?) {
        if (!updatesDir.isDirectory) return
        val keepFile = keep?.let(::apkFile)
        updatesDir.listFiles()?.forEach { file ->
            if (file != keepFile) file.delete()
        }
    }

    private suspend fun runCheck(showChecking: Boolean) {
        if (!checkMutex.tryLock()) return
        checking = true
        try {
            val previous = _ui.value
            val now = System.currentTimeMillis()
            writeSnapshot(readSnapshot().copy(lastAttemptAt = now))
            if (showChecking || previous.phase is UpdatePhase.Idle) {
                _ui.value = previous.copy(phase = UpdatePhase.Checking)
            }
            when (val outcome = fetchLatest()) {
                is CheckOutcome.Newer -> {
                    currentRelease = outcome.release
                    writeSnapshot(UpdateSnapshot(lastSuccessAt = now, lastAttemptAt = now, release = outcome.release))
                    _ui.value = UpdateUi(UpdatePhase.Available(outcome.release), now)
                }

                CheckOutcome.Current -> {
                    currentRelease = null
                    pruneUpdates(keep = null)
                    writeSnapshot(UpdateSnapshot(lastSuccessAt = now, lastAttemptAt = now))
                    _ui.value = UpdateUi(UpdatePhase.UpToDate, now)
                }

                is CheckOutcome.Failure -> {
                    _ui.value = previous.copy(
                        phase = when (previous.phase) {
                            is UpdatePhase.Available, is UpdatePhase.ReadyToInstall -> previous.phase
                            else -> UpdatePhase.Failed(outcome.failure)
                        },
                    )
                }
            }
        } finally {
            checking = false
            checkMutex.unlock()
        }
    }

    private suspend fun fetchLatest(): CheckOutcome {
        val response = try {
            transport.execute(
                HttpRequest(
                    method = "GET",
                    url = UpdatePolicy.LATEST_URL,
                    headers = UpdatePolicy.requestHeaders(),
                    timeoutMs = FETCH_TIMEOUT_MS,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return CheckOutcome.Failure(UpdateFailure.NETWORK)
        }
        val status = response.status
        val body = try {
            response.body.readUtf8()
        } catch (error: Exception) {
            return CheckOutcome.Failure(UpdateFailure.NETWORK)
        } finally {
            response.close()
        }
        if (status == 404) return CheckOutcome.Failure(UpdateFailure.NO_RELEASE)
        if (status !in 200..299) {
            val blocked = status == 403 || status == 429 || status == 451
            return CheckOutcome.Failure(if (blocked) UpdateFailure.NETWORK else UpdateFailure.SERVICE)
        }
        return when (val latest = UpdatePolicy.evaluateLatest(body, currentVersionName)) {
            is UpdatePolicy.Latest.Newer -> CheckOutcome.Newer(latest.release)
            UpdatePolicy.Latest.Current -> CheckOutcome.Current
            UpdatePolicy.Latest.MissingAsset -> CheckOutcome.Failure(UpdateFailure.NO_RELEASE)
            UpdatePolicy.Latest.Invalid -> CheckOutcome.Failure(UpdateFailure.SERVICE)
        }
    }

    private suspend fun downloadRelease(release: UpdateRelease) {
        _ui.value = _ui.value.copy(phase = UpdatePhase.Downloading(release, 0f))
        val apk = apkFile(release)
        val part = File(updatesDir, "${apk.name}.part")
        updatesDir.mkdirs()
        apk.delete()
        part.delete()
        try {
            val response = transport.execute(
                HttpRequest(
                    method = "GET",
                    url = release.apkUrl,
                    headers = UpdatePolicy.requestHeaders(),
                    timeoutMs = DOWNLOAD_TIMEOUT_MS,
                ),
            )
            var downloaded = 0L
            var lastPercent = -1
            try {
                if (response.status !in 200..299) {
                    throw UpdateFailureException(UpdateFailure.DOWNLOAD)
                }
                val input = response.body.inputStream()
                part.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (release.apkSize > 0L) {
                            val percent = ((downloaded * 100) / release.apkSize).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _ui.value = _ui.value.copy(
                                    phase = UpdatePhase.Downloading(release, percent / 100f),
                                )
                            }
                        }
                    }
                }
            } finally {
                response.close()
            }
            if (!UpdatePolicy.verifyDownload(downloaded, sha256(part), release.apkSize, release.sha256)) {
                throw UpdateFailureException(UpdateFailure.VERIFY)
            }
            if (!part.renameTo(apk)) throw UpdateFailureException(UpdateFailure.DOWNLOAD)
            if (!installer.isInstallableArchive(apk)) {
                apk.delete()
                throw UpdateFailureException(UpdateFailure.ARCHIVE)
            }
            _ui.value = _ui.value.copy(
                phase = UpdatePhase.ReadyToInstall(release, needsPermission = !installer.canInstallPackages()),
            )
        } catch (error: CancellationException) {
            part.delete()
            apk.delete()
            throw error
        } catch (error: UpdateFailureException) {
            part.delete()
            apk.delete()
            _ui.value = _ui.value.copy(phase = UpdatePhase.Failed(error.failure, release))
        } catch (error: Exception) {
            part.delete()
            apk.delete()
            onFailure("update: download failed", error)
            _ui.value = _ui.value.copy(phase = UpdatePhase.Failed(UpdateFailure.DOWNLOAD, release))
        }
    }

    private fun apkFile(release: UpdateRelease): File = File(updatesDir, UpdatePolicy.apkFileName(release.versionName))

    private suspend fun readSnapshot(): UpdateSnapshot =
        runCatching { store.read() }.getOrDefault(UpdateSnapshot())

    private suspend fun writeSnapshot(snapshot: UpdateSnapshot) {
        runCatching { store.write(snapshot) }.onFailure { onFailure("update: state write failed", it) }
    }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrNull()

    private sealed interface CheckOutcome {
        data class Newer(val release: UpdateRelease) : CheckOutcome

        data object Current : CheckOutcome

        data class Failure(val failure: UpdateFailure) : CheckOutcome
    }

    private class UpdateFailureException(val failure: UpdateFailure) : Exception()

    private companion object {
        const val FETCH_TIMEOUT_MS = 20_000L
        const val DOWNLOAD_TIMEOUT_MS = 900_000L
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
    }
}
