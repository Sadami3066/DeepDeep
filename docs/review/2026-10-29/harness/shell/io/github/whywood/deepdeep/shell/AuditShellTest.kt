package io.github.whywood.deepdeep.shell

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuditShellTest {
    @Test fun noNewlineOutputExceedsOutputCapInStreamingCallback() = runBlocking {
        val root = Files.createTempDirectory("deepdeep-audit-output").toFile()
        try {
            val proot = File(root,"proot").apply {
                writeText("#!/bin/sh\nlast=\"\"\nfor arg in \"\$@\"; do last=\"\$arg\"; done\nexec /bin/sh -c \"\$last\"\n")
                setExecutable(true)
            }
            val loader = File(root,"loader").apply { writeText("") }
            val fs = File(root,"rootfs").apply { mkdirs() }
            val cwd = File(root,"workspace").apply { mkdirs() }
            val executor = PRootExecutor(proot,loader,fs,cwd,File(root,"tmp"),maxOutputBytes=65536)
            var maxChunk = 0
            val result = executor.executeStreaming("head -c 8388608 /dev/zero | tr '\\0' x",30000) { maxChunk = maxOf(maxChunk,it.length) }
            assertEquals(65536,result.output.length)
            assertTrue(result.truncated)
            assertEquals(8388608,maxChunk)
            println("AUDIT no-newline retained_output=65536 callback_chunk=$maxChunk duration_ms=${result.durationMillis}")
        } finally { root.deleteRecursively() }
    }

    @Test fun installedStateCostScalesWithRootfsFileCount() {
        val root = Files.createTempDirectory("deepdeep-audit-state").toFile()
        try {
            File(root,LinuxRootfsInstaller.MARKER_FILE).writeText("version=3.24.2\n")
            val installer = LinuxRootfsInstaller(root, { error("unused") })
            for (i in 1..10000) {
                val folder = File(root,"packages/${i/100}").apply { mkdirs() }
                File(folder,"file$i").writeText("audit")
                if (i in listOf(100,1000,10000)) {
                    repeat(2) { installer.currentState() }
                    val times = List(9) {
                        val start = System.nanoTime()
                        assertTrue(installer.currentState() is LinuxInstallState.Installed)
                        (System.nanoTime()-start)/1e6
                    }.sorted()
                    println("AUDIT_PERF rootfs_state files=$i median_ms=${times[4]} max_ms=${times.last()}")
                }
            }
        } finally { root.deleteRecursively() }
    }
}
