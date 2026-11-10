package io.github.whywood.deepdeep.data

import io.github.whywood.deepdeep.runtime.host.HostAccess
import io.github.whywood.deepdeep.runtime.host.HostCapabilities
import io.github.whywood.deepdeep.runtime.host.HostEnvironment
import io.github.whywood.deepdeep.runtime.host.HostEnvironmentProvider
import io.github.whywood.deepdeep.sdk.ai.RunSignal
import io.github.whywood.deepdeep.sdk.coding.tools.ShellExecutor
import io.github.whywood.deepdeep.sdk.coding.tools.ShellResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevPackageManagerTest {
    @Test
    fun refreshRunsInEnvironmentOfEachAccessLevel() = runBlocking {
        for (access in HostAccess.entries) {
            val executors = HostAccess.entries.associateWith { level ->
                RecordingExecutor { command ->
                    if (command.contains("apk info")) ShellResult("curl\n", 0, false) else ShellResult("", 0, false)
                }
            }
            val provider = providerFor(executors)
            provider.selected = access
            val manager = managerFor(provider)

            manager.refreshAndAwait()

            assertEquals(setOf("curl"), manager.state.value.installed)
            val executor = executors.getValue(access)
            assertTrue(executor.commands.any { it.contains("apk info") })
            assertEquals("/workspace", executor.lastCwd)
            HostAccess.entries.filter { it != access }.forEach { other ->
                assertTrue(executors.getValue(other).commands.isEmpty())
            }
        }
    }

    @Test
    fun installRunsInEnvironmentOfEachAccessLevel() = runBlocking {
        for (access in HostAccess.entries) {
            val executors = HostAccess.entries.associateWith { level ->
                RecordingExecutor { command ->
                    if (command.contains("apk info")) ShellResult("curl\n", 0, false) else ShellResult("", 0, false)
                }
            }
            val provider = providerFor(executors)
            provider.selected = access
            val notifier = RecordingNotifier()
            val manager = managerFor(provider, notifier = notifier)

            manager.installBundle(DevPackageCatalog.bundleById("base")!!)
            withTimeout(10_000) {
                manager.state.first { it.operation == null && (it.installed.isNotEmpty() || it.error != null) }
            }

            assertNull(manager.state.value.error)
            assertTrue(notifier.finished.any { it.second })
            val executor = executors.getValue(access)
            assertTrue(executor.commands.contains("apk update"))
            assertTrue(executor.commands.any { it.startsWith("apk add ") })
            HostAccess.entries.filter { it != access }.forEach { other ->
                assertTrue(executors.getValue(other).commands.isEmpty())
            }
        }
    }

    @Test
    fun uninstallRunsInCurrentEnvironment() = runBlocking {
        val executor = RecordingExecutor { command ->
            if (command.contains("apk info")) ShellResult("curl\n", 0, false) else ShellResult("", 0, false)
        }
        val provider = providerFor(mapOf(HostAccess.ROOT to executor))
        provider.selected = HostAccess.ROOT
        val manager = managerFor(provider)

        manager.uninstallPackage("curl")
        withTimeout(10_000) { while (executor.commands.none { it == "apk del curl" }) delay(10) }
        withTimeout(10_000) { manager.state.first { it.operation == null && it.installed.isNotEmpty() } }

        assertTrue(executor.commands.contains("apk del curl"))
        assertNull(manager.state.value.error)
    }

    @Test
    fun refreshKeepsInstalledPackagesWhenEnvironmentIsNotReady() = runBlocking {
        val executor = RecordingExecutor { command ->
            if (command.contains("apk info")) ShellResult("curl\n", 0, false) else ShellResult("", 0, false)
        }
        val provider = providerFor(mapOf(HostAccess.LOCAL to executor))
        var readyNow = true
        val manager = managerFor(provider, ready = { readyNow })

        manager.refreshAndAwait()
        assertEquals(setOf("curl"), manager.state.value.installed)

        val commandsBefore = executor.commands.size
        readyNow = false
        manager.refreshAndAwait()

        assertEquals(setOf("curl"), manager.state.value.installed)
        assertEquals(commandsBefore, executor.commands.size)
        assertNull(manager.state.value.error)
    }

    @Test
    fun installKeepsInitialEnvironmentWhenAccessLevelChangesMidOperation() = runBlocking {
        val localExecutor = RecordingExecutor()
        val rootExecutor = RecordingExecutor()
        val provider = FakeHostEnvironmentProvider(
            mapOf(
                HostAccess.LOCAL to FakeHostEnvironment(HostAccess.LOCAL, localExecutor),
                HostAccess.ROOT to FakeHostEnvironment(HostAccess.ROOT, rootExecutor),
            ),
        )
        provider.selected = HostAccess.LOCAL
        localExecutor.respond = { command ->
            if (command == "apk update") provider.selected = HostAccess.ROOT
            if (command.contains("apk info")) ShellResult("curl\n", 0, false) else ShellResult("", 0, false)
        }
        val manager = managerFor(provider)

        manager.installBundle(DevPackageCatalog.bundleById("base")!!)
        withTimeout(10_000) {
            manager.state.first { it.operation == null && (it.installed.isNotEmpty() || it.error != null) }
        }

        assertNull(manager.state.value.error)
        assertEquals(1, provider.calls)
        assertTrue(localExecutor.commands.contains("apk update"))
        assertTrue(localExecutor.commands.any { it.startsWith("apk add ") })
        assertTrue(localExecutor.commands.any { it.contains("apk info") })
        assertTrue(rootExecutor.commands.isEmpty())
    }

    @Test
    fun installReportsMissingEnvironment() = runBlocking {
        val executor = RecordingExecutor()
        val provider = providerFor(mapOf(HostAccess.ADB to executor))
        provider.selected = HostAccess.ADB
        val manager = managerFor(provider, ready = { false })

        manager.installBundle(DevPackageCatalog.bundleById("base")!!)

        assertNotNull(manager.state.value.error)
        assertTrue(executor.commands.isEmpty())
    }

    private fun providerFor(executors: Map<HostAccess, RecordingExecutor>): FakeHostEnvironmentProvider =
        FakeHostEnvironmentProvider(
            executors.mapValues { (access, executor) -> FakeHostEnvironment(access, executor) },
        )

    private fun managerFor(
        provider: FakeHostEnvironmentProvider,
        ready: () -> Boolean = { true },
        notifier: RecordingNotifier = RecordingNotifier(),
    ): DevPackageManager = DevPackageManager(
        strings = object : DevPackageStrings {
            override fun text(resId: Int, vararg args: Any): String = "string_$resId"
        },
        hostEnvironment = provider,
        cwd = { "/workspace" },
        ready = ready,
        notifier = notifier,
    )
}

private class RecordingExecutor(
    var respond: (String) -> ShellResult = { ShellResult("", 0, false) },
) : ShellExecutor {
    val commands = mutableListOf<String>()
    var lastCwd: String? = null

    override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {
        commands += command
        return respond(command)
    }
}

private class FakeHostEnvironment(
    override val access: HostAccess,
    private val executor: RecordingExecutor,
) : HostEnvironment {
    override val capabilities = HostCapabilities()
    override val sharedStorageEnabled = false
    override val files: FileSystem = FileSystem.SYSTEM
    override val system: ShellExecutor? = null
    override val promptLines: List<String> = emptyList()

    override fun bash(cwd: String): ShellExecutor {
        executor.lastCwd = cwd
        return executor
    }
}

private class FakeHostEnvironmentProvider(
    private val environments: Map<HostAccess, HostEnvironment>,
) : HostEnvironmentProvider {
    var selected: HostAccess = HostAccess.LOCAL
    var calls: Int = 0

    override fun current(): HostEnvironment {
        calls += 1
        return environments.getValue(selected)
    }
}

private class RecordingNotifier : DevPackageNotifier {
    val finished = mutableListOf<Pair<String, Boolean>>()

    override fun clearInstallNotification() = Unit

    override fun notifyInstall(label: String, stage: String?, progress: Int?) = Unit

    override fun notifyInstallFinished(label: String, success: Boolean, error: String?) {
        finished += label to success
    }
}
