package io.github.whywood.deepdeep.runtime

import dev.pi.agent.AgentToolResult
import dev.pi.ai.RequestAbortedException
import dev.pi.coding.tools.CodingTool
import dev.pi.coding.tools.textResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
private val jobStartParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("command") {
            put("type", "string")
            put("description", "Shell command to run in the background")
        }
        putJsonObject("label") {
            put("type", "string")
            put("description", "Short label shown in the jobs list (defaults to the command)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("command")) })
}

private val jobOutputParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("job_id") {
            put("type", "string")
            put("description", "Background job id (from job_start or a job reported by job_list)")
        }
        putJsonObject("wait") {
            put("type", "boolean")
            put("description", "Wait for new output or job completion instead of returning immediately")
        }
        putJsonObject("timeout_ms") {
            put("type", "number")
            put("description", "Maximum wait in milliseconds (default 30000, clamped to 1..600000)")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("job_id")) })
}

private val jobListParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

private val jobKillParameters: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("job_id") {
            put("type", "string")
            put("description", "Background job id (from job_start or a job reported by job_list)")
        }
        putJsonObject("reason") {
            put("type", "string")
            put("description", "Optional reason recorded on the job")
        }
    }
    put("required", buildJsonArray { add(JsonPrimitive("job_id")) })
}

fun jobTools(binding: SessionBinding, registry: JobRegistry): List<CodingTool> = listOf(
    jobStartTool(binding, registry),
    jobOutputTool(binding, registry),
    jobListTool(binding, registry),
    jobKillTool(binding, registry),
)

private fun jobStartTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(
    name = "job_start",
    description = "Start a shell command as a background job and return its id immediately. Use job_output to read its output and job_kill to stop it.",
    parameters = jobStartParameters,
    label = "job_start",
    promptSnippet = "Start a background job",
    promptGuidelines = listOf(
        "Use job_start for commands that run for a long time; read their output with job_output and stop them with job_kill.",
        "Use the foreground bash tool for commands that finish quickly.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        jobToolResult {
            val session = activeJobSession(binding)
            val command = args.requiredJobString("command")
            val label = args.jobStringOrNull("label").orEmpty()
            val start = registry.start(session.sessionId, command, label, session.cwd)
            val jobId = start.id
            if (jobId == null) {
                textResult(start.error ?: "failed to start job", isError = true)
            } else {
                textResult(
                    "Started background job $jobId.\n\n" +
                        "Use job_output with job id $jobId to read output, or job_kill to stop it.",
                )
            }
        }
    },
)

private fun jobOutputTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(
    name = "job_output",
    description = "Read output produced by a background job since the previous job_output call. Always ends with a status line. Set wait=true to suspend until new output arrives or the job finishes.",
    parameters = jobOutputParameters,
    label = "job_output",
    promptSnippet = "Read background job output",
    promptGuidelines = listOf(
        "Call job_output after starting a job with job_start; each call returns only output written since the previous read.",
        "Use wait=true when the job is still running and you want to block until there is progress or the job settles.",
        "A status line of running means the job is still active; done, failed and killed are terminal.",
    ),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        jobToolResult {
            activeJobSession(binding)
            val jobId = args.requiredJobString("job_id")
            val wait = args.jobBoolOrNull("wait") ?: false
            val timeout = args.jobLongOrNull("timeout_ms")
            val read = when {
                !wait -> registry.readIncremental(jobId)
                timeout == null -> registry.awaitRead(jobId, true)
                else -> registry.awaitRead(jobId, true, timeout)
            }
            val status = "[status: ${jobStatusText(read.status, read.exitCode)}]"
            val prefix = if (read.dropped) "[older output was dropped from the in-memory buffer]\n" else ""
            val text = prefix + if (read.output.isEmpty()) status else read.output.trimEnd('\n') + "\n" + status
            textResult(text, isError = read.status == JobStatus.FAILED)
        }
    },
)

private fun jobListTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(
    name = "job_list",
    description = "List background jobs started in the current conversation with their status and label.",
    parameters = jobListParameters,
    label = "job_list",
    promptSnippet = "List background jobs",
    promptGuidelines = listOf("Use job_list to find a job id before calling job_output or job_kill."),
    execute = { _, _, signal, _ ->
        signal?.throwIfAborted()
        jobToolResult {
            val session = activeJobSession(binding)
            val lines = registry.jobs.value
                .filter { it.sessionId == session.sessionId }
                .map { "${it.id} [bash] ${it.status.name.lowercase()} — ${it.label}" }
            textResult(if (lines.isEmpty()) "no background jobs" else lines.joinToString("\n"))
        }
    },
)

private fun jobKillTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(
    name = "job_kill",
    description = "Request cancellation of a running background job. The job settles as killed once its process tree is stopped.",
    parameters = jobKillParameters,
    label = "job_kill",
    promptSnippet = "Cancel a background job",
    promptGuidelines = listOf("Use job_kill only when the running work is no longer needed."),
    execute = { _, args, signal, _ ->
        signal?.throwIfAborted()
        jobToolResult {
            activeJobSession(binding)
            val jobId = args.requiredJobString("job_id")
            val reason = args.jobStringOrNull("reason")
            val requested = registry.kill(jobId, reason)
            textResult(if (requested) "cancellation-requested" else "already-finished")
        }
    },
)

private fun activeJobSession(binding: SessionBinding): BoundSession =
    binding.current() ?: throw IllegalStateException("No session is bound")

private suspend fun jobToolResult(block: suspend () -> AgentToolResult): AgentToolResult =
    try {
        block()
    } catch (error: RequestAbortedException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        textResult(error.message ?: "background job operation failed", isError = true)
    }

private fun JsonObject.jobStringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonObject.requiredJobString(name: String): String =
    jobStringOrNull(name) ?: throw IllegalArgumentException("Missing required argument: $name")

private fun JsonObject.jobBoolOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.jobLongOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()
