package io.github.whywood.deepdeep.runtime

import java.io.File
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InstallationIdStore(
    agentDir: File,
) {
    private val mutex = Mutex()
    private val file = File(agentDir, FILE_NAME)
    private var cached: String? = null

    suspend fun get(): String = mutex.withLock {
        cached?.let { return@withLock it }
        val loaded = readExisting() ?: UUID.randomUUID().toString().also { created ->
            file.parentFile?.mkdirs()
            file.writeText(created)
        }
        cached = loaded
        loaded
    }

    private fun readExisting(): String? {
        if (!file.isFile) return null
        val text = runCatching { file.readText().trim() }.getOrNull() ?: return null
        return text.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
    }

    private companion object {
        const val FILE_NAME = "install-id"
    }
}
