package io.github.whywood.deepdeep.plugin.feishu

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeishuPluginTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun restoreCommandKeepsExistingBinaryOnRollback() {
        val kept = feishuRestoreCommand(hadBinary = true)
        assertTrue(kept.contains(LARK_CLI_BACKUP_PATH))
        assertTrue(kept.contains(LARK_CLI_TEMP_PATH))
        assertFalse(kept.contains("rm -f ${FeishuCli.INSTALL_PATH}"))

        val fresh = feishuRestoreCommand(hadBinary = false)
        assertTrue(fresh.contains(LARK_CLI_BACKUP_PATH))
        assertTrue(fresh.contains("rm -f ${FeishuCli.INSTALL_PATH}"))
    }

    @Test
    fun restoreCommandRestoresBackupAfterFailedReplacement() {
        val binary = File(folder.root, "lark-cli").apply {
            writeText("old-binary")
            setExecutable(true)
        }
        val backup = File(folder.root, ".lark-cli.old").apply {
            writeText("old-binary")
            setExecutable(true)
        }
        val staged = File(folder.root, ".lark-cli.new").apply { writeText("new-binary") }
        binary.writeText("new-binary")

        runRestore(feishuRestoreCommand(hadBinary = true))

        assertTrue(binary.isFile)
        assertTrue(binary.canExecute())
        assertEquals("old-binary", binary.readText())
        assertFalse(backup.exists())
        assertFalse(staged.exists())
    }

    @Test
    fun restoreCommandKeepsExistingBinaryAndRemovesFirstInstallRemnants() {
        val existing = File(folder.root, "lark-cli").apply { writeText("old-binary") }
        runRestore(feishuRestoreCommand(hadBinary = true))
        assertTrue(existing.isFile)
        assertEquals("old-binary", existing.readText())

        val partial = File(folder.root, "lark-cli").apply { writeText("half-installed") }
        File(folder.root, ".lark-cli.new").apply { writeText("staged") }
        runRestore(feishuRestoreCommand(hadBinary = false))
        assertFalse(partial.exists())
        assertFalse(File(folder.root, ".lark-cli.new").exists())
    }

    private fun runRestore(command: String) {
        val rooted = command.replace("/usr/local/bin", folder.root.absolutePath)
        val process = ProcessBuilder("bash", "-c", rooted).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
    }
}
