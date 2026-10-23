package io.github.whywood.deepdeep.data

import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val AGENT_DOC_MAX_BYTES: Int = 64 * 1024

private const val AGENT_DOC_FILE = "AGENTS.md"
private const val AGENT_DOC_OVERRIDE_FILE = "AGENTS.override.md"

data class AgentDoc(
    val fileName: String,
    val content: String,
    val isOverride: Boolean,
)

class AgentDocStore(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun read(cwd: String): AgentDoc? = withContext(ioDispatcher) {
        if (cwd.isBlank()) return@withContext null
        try {
            val directory = File(cwd)
            val override = File(directory, AGENT_DOC_OVERRIDE_FILE)
            if (override.isFile) {
                AgentDoc(
                    fileName = AGENT_DOC_OVERRIDE_FILE,
                    content = override.readText(Charsets.UTF_8),
                    isOverride = true,
                )
            } else {
                val base = File(directory, AGENT_DOC_FILE)
                if (base.isFile) {
                    AgentDoc(
                        fileName = AGENT_DOC_FILE,
                        content = base.readText(Charsets.UTF_8),
                        isOverride = false,
                    )
                } else {
                    null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            null
        }
    }

    suspend fun save(cwd: String, content: String): Boolean = withContext(ioDispatcher) {
        if (cwd.isBlank()) return@withContext false
        if (content.toByteArray(Charsets.UTF_8).size > AGENT_DOC_MAX_BYTES) return@withContext false
        val directory = File(cwd)
        try {
            if (content.isBlank()) {
                effectiveFile(directory).delete()
            } else {
                writeAtomically(effectiveFile(directory), content)
            }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            false
        }
    }

    suspend fun delete(cwd: String): Boolean = withContext(ioDispatcher) {
        if (cwd.isBlank()) return@withContext false
        val directory = File(cwd)
        try {
            File(directory, AGENT_DOC_OVERRIDE_FILE).delete()
            File(directory, AGENT_DOC_FILE).delete()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            false
        }
    }

    private fun effectiveFile(directory: File): File {
        val override = File(directory, AGENT_DOC_OVERRIDE_FILE)
        if (override.isFile) return override
        return File(directory, AGENT_DOC_FILE)
    }

    private fun writeAtomically(target: File, content: String) {
        val parent = target.parentFile ?: throw IOException("Missing parent directory")
        parent.mkdirs()
        val temp = File(parent, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            temp.writeText(content, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IOException("Unable to write ${target.path}")
            }
        } finally {
            temp.delete()
        }
    }
}
