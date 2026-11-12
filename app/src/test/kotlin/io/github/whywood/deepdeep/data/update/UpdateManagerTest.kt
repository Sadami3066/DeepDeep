package io.github.whywood.deepdeep.data.update

import io.github.whywood.deepdeep.sdk.ai.http.HttpRequest
import io.github.whywood.deepdeep.sdk.ai.http.HttpResponse
import io.github.whywood.deepdeep.sdk.ai.http.HttpTransport
import java.io.File
import java.net.ConnectException
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val updatesDir: File
        get() = File(temporaryFolder.root, "updates").apply { mkdirs() }

    @Test
    fun autoCheckFindsNewerRelease() = runTest {
        val transport = FakeTransport { respond(200, latestJson("1.0.3")) }
        val store = FakeStore()
        val manager = newManager(transport = transport, store = store)
        advanceUntilIdle()

        manager.maybeAutoCheck()
        advanceUntilIdle()

        val phase = manager.ui.value.phase as UpdatePhase.Available
        assertEquals("1.0.3", phase.release.versionName)
        assertEquals("1.0.3", store.snapshot.release?.versionName)
        assertNotNull(store.snapshot.lastSuccessAt)
    }

    @Test
    fun autoCheckRespectsThrottle() = runTest {
        val transport = FakeTransport { respond(200, latestJson("1.0.3")) }
        val store = FakeStore(UpdateSnapshot(lastSuccessAt = System.currentTimeMillis() - 60_000L))
        val manager = newManager(transport = transport, store = store)
        advanceUntilIdle()

        manager.maybeAutoCheck()
        advanceUntilIdle()

        assertEquals(0, transport.requests.size)
    }

    @Test
    fun upToDateResultIsReported() = runTest {
        val manager = newManager(transport = FakeTransport { respond(200, latestJson("1.0.2")) })
        advanceUntilIdle()

        manager.checkManually()
        advanceUntilIdle()

        assertEquals(UpdatePhase.UpToDate, manager.ui.value.phase)
    }

    @Test
    fun networkFailureIsReported() = runTest {
        val manager = newManager(transport = FakeTransport { throw ConnectException("offline") })
        advanceUntilIdle()

        manager.checkManually()
        advanceUntilIdle()

        val phase = manager.ui.value.phase as UpdatePhase.Failed
        assertEquals(UpdateFailure.NETWORK, phase.failure)
    }

    @Test
    fun serverFailureIsReported() = runTest {
        val manager = newManager(transport = FakeTransport { respond(500, "") })
        advanceUntilIdle()

        manager.checkManually()
        advanceUntilIdle()

        val phase = manager.ui.value.phase as UpdatePhase.Failed
        assertEquals(UpdateFailure.SERVICE, phase.failure)
    }

    @Test
    fun releaseWithoutApkIsReported() = runTest {
        val json = """{"tag_name":"v1.0.3","body":"","assets":[]}"""
        val manager = newManager(transport = FakeTransport { respond(200, json) })
        advanceUntilIdle()

        manager.checkManually()
        advanceUntilIdle()

        val phase = manager.ui.value.phase as UpdatePhase.Failed
        assertEquals(UpdateFailure.NO_RELEASE, phase.failure)
    }

    @Test
    fun autoCheckFailureKeepsKnownUpdate() = runTest {
        val release = UpdateRelease(
            tag = "v1.0.3",
            versionName = "1.0.3",
            notes = "更新内容",
            apkUrl = "https://example.com/DeepDeep-1.0.3.apk",
            apkSize = APK_BODY.length.toLong(),
            sha256 = sha256Hex(APK_BODY),
            pageUrl = "https://github.com/example/releases/tag/v1.0.3",
        )
        val store = FakeStore(UpdateSnapshot(lastSuccessAt = 0L, release = release))
        val manager = newManager(transport = FakeTransport { throw ConnectException("offline") }, store = store)
        advanceUntilIdle()
        assertTrue(manager.ui.value.phase is UpdatePhase.Available)

        manager.maybeAutoCheck()
        advanceUntilIdle()

        assertTrue(manager.ui.value.phase is UpdatePhase.Available)
    }

    @Test
    fun checkingStateAppearsBeforeResult() = runTest {
        val gate = CompletableDeferred<Unit>()
        val transport = FakeTransport {
            gate.await()
            respond(200, latestJson("1.0.3"))
        }
        val manager = newManager(transport = transport)
        advanceUntilIdle()

        manager.checkManually()
        advanceUntilIdle()
        assertEquals(UpdatePhase.Checking, manager.ui.value.phase)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(manager.ui.value.phase is UpdatePhase.Available)
    }

    @Test
    fun downloadStoresVerifiedApk() = runTest {
        val transport = FakeTransport { request ->
            if (request.url == UpdatePolicy.LATEST_URL) respond(200, latestJson("1.0.3")) else respond(200, APK_BODY)
        }
        val installer = FakeInstaller()
        val manager = newManager(transport = transport, installer = installer)
        advanceUntilIdle()
        manager.checkManually()
        advanceUntilIdle()

        manager.download()
        advanceUntilIdle()

        val phase = manager.ui.value.phase as UpdatePhase.ReadyToInstall
        assertFalse(phase.needsPermission)
        val apk = File(updatesDir, "DeepDeep-1.0.3.apk")
        assertTrue(apk.isFile)
        assertEquals(APK_BODY, apk.readText())
        assertNull(File(updatesDir, "DeepDeep-1.0.3.apk.part").takeIf { it.exists() })
    }

    @Test
    fun downloadWithWrongDigestIsDiscarded() = runTest {
        val transport = FakeTransport { request ->
            if (request.url == UpdatePolicy.LATEST_URL) {
                respond(200, latestJson("1.0.3", digest = sha256Hex("other")))
            } else {
                respond(200, APK_BODY)
            }
        }
        val manager = newManager(transport = transport)
        advanceUntilIdle()
        manager.checkManually()
        advanceUntilIdle()

        manager.download()
        advanceUntilIdle()

        val phase = manager.ui.value.phase as UpdatePhase.Failed
        assertEquals(UpdateFailure.VERIFY, phase.failure)
        assertFalse(File(updatesDir, "DeepDeep-1.0.3.apk").exists())
    }

    @Test
    fun downloadWithWrongArchiveIsDiscarded() = runTest {
        val transport = FakeTransport { request ->
            if (request.url == UpdatePolicy.LATEST_URL) respond(200, latestJson("1.0.3")) else respond(200, APK_BODY)
        }
        val installer = FakeInstaller(archiveAccepted = false)
        val manager = newManager(transport = transport, installer = installer)
        advanceUntilIdle()
        manager.checkManually()
        advanceUntilIdle()

        manager.download()
        advanceUntilIdle()

        val phase = manager.ui.value.phase as UpdatePhase.Failed
        assertEquals(UpdateFailure.ARCHIVE, phase.failure)
        assertFalse(File(updatesDir, "DeepDeep-1.0.3.apk").exists())
    }

    @Test
    fun downloadFailureCanBeRetried() = runTest {
        var failDownload = true
        val transport = FakeTransport { request ->
            when {
                request.url == UpdatePolicy.LATEST_URL -> respond(200, latestJson("1.0.3"))
                failDownload -> throw ConnectException("offline")
                else -> respond(200, APK_BODY)
            }
        }
        val manager = newManager(transport = transport)
        advanceUntilIdle()
        manager.checkManually()
        advanceUntilIdle()
        manager.download()
        advanceUntilIdle()
        assertEquals(UpdateFailure.DOWNLOAD, (manager.ui.value.phase as UpdatePhase.Failed).failure)

        failDownload = false
        manager.retry()
        advanceUntilIdle()

        assertTrue(manager.ui.value.phase is UpdatePhase.ReadyToInstall)
    }

    @Test
    fun installWaitsForPermissionAndResumes() = runTest {
        val transport = FakeTransport { request ->
            if (request.url == UpdatePolicy.LATEST_URL) respond(200, latestJson("1.0.3")) else respond(200, APK_BODY)
        }
        val installer = FakeInstaller(canInstall = false)
        val manager = newManager(transport = transport, installer = installer)
        advanceUntilIdle()
        manager.checkManually()
        advanceUntilIdle()
        manager.download()
        advanceUntilIdle()
        val waiting = manager.ui.value.phase as UpdatePhase.ReadyToInstall
        assertTrue(waiting.needsPermission)

        manager.openInstallPermission()
        assertTrue(installer.permissionSettingsOpened)
        installer.canInstall = true
        manager.onScreenResumed()
        advanceUntilIdle()

        assertNotNull(installer.installed)
        val phase = manager.ui.value.phase as UpdatePhase.ReadyToInstall
        assertFalse(phase.needsPermission)
    }

    @Test
    fun hydrateDropsStaleReleaseAndFile() = runTest {
        val stale = UpdateRelease(
            tag = "v1.0.3",
            versionName = "1.0.3",
            notes = "",
            apkUrl = "https://example.com/DeepDeep-1.0.3.apk",
            apkSize = 0,
            sha256 = null,
            pageUrl = "https://github.com/example/releases/tag/v1.0.3",
        )
        val apk = File(updatesDir, "DeepDeep-1.0.3.apk").apply { writeText("stale") }
        val store = FakeStore(UpdateSnapshot(lastSuccessAt = 1L, release = stale))
        val manager = newManager(transport = FakeTransport { error("no request") }, store = store, currentVersionName = "1.0.3")
        advanceUntilIdle()

        assertEquals(UpdatePhase.UpToDate, manager.ui.value.phase)
        assertFalse(apk.exists())
    }

    private fun TestScope.newManager(
        transport: HttpTransport,
        store: UpdateStore = FakeStore(),
        installer: UpdateInstaller = FakeInstaller(),
        currentVersionName: String = "1.0.2",
    ): UpdateManager = UpdateManager(
        transport = transport,
        store = store,
        installer = installer,
        updatesDir = updatesDir,
        scope = this,
        currentVersionName = currentVersionName,
    )

    private fun respond(status: Int, body: String): HttpResponse =
        HttpResponse(status, emptyMap(), Buffer().writeUtf8(body))

    private fun latestJson(
        version: String,
        body: String = APK_BODY,
        digest: String? = sha256Hex(body),
    ): String = """
        {
          "tag_name": "v$version",
          "body": "更新内容",
          "draft": false,
          "prerelease": false,
          "html_url": "https://github.com/example/releases/tag/v$version",
          "assets": [
            {
              "name": "DeepDeep-$version.apk",
              "browser_download_url": "https://example.com/DeepDeep-$version.apk",
              "size": ${body.length},
              "digest": "sha256:$digest"
            }
          ]
        }
    """.trimIndent()

    private class FakeStore(var snapshot: UpdateSnapshot = UpdateSnapshot()) : UpdateStore {
        override suspend fun read(): UpdateSnapshot = snapshot

        override suspend fun write(snapshot: UpdateSnapshot) {
            this.snapshot = snapshot
        }
    }

    private class FakeInstaller(
        var canInstall: Boolean = true,
        var archiveAccepted: Boolean = true,
    ) : UpdateInstaller {
        var permissionSettingsOpened = false
        var installed: File? = null

        override fun canInstallPackages(): Boolean = canInstall

        override fun openInstallPermissionSettings() {
            permissionSettingsOpened = true
        }

        override fun isInstallableArchive(apk: File): Boolean = archiveAccepted

        override fun install(apk: File): Boolean {
            installed = apk
            return true
        }
    }

    private class FakeTransport(
        var handler: suspend (HttpRequest) -> HttpResponse,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler(request)
        }
    }

    private companion object {
        const val APK_BODY = "apk-bytes"

        fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
