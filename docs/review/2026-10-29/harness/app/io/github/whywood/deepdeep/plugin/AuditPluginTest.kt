package io.github.whywood.deepdeep.plugin

import io.github.whywood.deepdeep.design.DdIcons
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditPluginTest {
    @Test fun failedUpdateUninstallsPreviouslyWorkingPluginButKeepsItsRecord() = runTest {
        val root = Files.createTempDirectory("deepdeep-audit-plugin").toFile()
        try {
            var installed = true
            val plugin = object : OfficialPlugin {
                override val id = "audit"
                override val icon = DdIcons.Terminal
                override val nameRes = 1
                override val summaryRes = 2
                override val sizeLabelRes = 3
                override val version = "2.0"
                override suspend fun availability() = PluginAvailability.Available
                override suspend fun install(progress: PluginProgress) { error("audit download failed before replacement") }
                override suspend fun uninstall() { installed = false }
                override suspend fun status(): PluginStatus = if (installed) PluginStatus.Ready("ready") else PluginStatus.Failed("missing")
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val records = PluginRecordStore(File(root,"plugins.json"), dispatcher)
            records.upsert(PluginRecord("audit","1.0",0))
            val manager = PluginManager(listOf(plugin),records,PluginSkillStore(File(root,"active"),File(root,"repo"),dispatcher),object : PluginStrings {
                override fun get(id: Int) = id.toString()
                override fun format(id: Int,vararg args: Any) = id.toString()
            },this,{true})
            manager.start()
            manager.update("audit")
            advanceUntilIdle()
            assertFalse(installed)
            assertEquals("1.0",records.load().records["audit"]!!.version)
            assertTrue(manager.state.value.snapshot("audit")!!.installed)
            println("AUDIT failed update removed working plugin; installed record remains")
        } finally { root.deleteRecursively() }
    }
}
