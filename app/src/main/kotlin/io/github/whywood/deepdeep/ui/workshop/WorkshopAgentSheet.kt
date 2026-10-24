package io.github.whywood.deepdeep.ui.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.DdTypingDots
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.WorkshopAgentEvent
import io.github.whywood.deepdeep.runtime.WorkshopAgentService
import io.github.whywood.deepdeep.runtime.WorkshopCreation
import io.github.whywood.deepdeep.runtime.WorkshopPageError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class AgentPhase { IDLE, THINKING, GENERATING, TOOL, DONE, FAILED }

@Composable
fun WorkshopAgentSheet(
    service: WorkshopAgentService,
    creation: WorkshopCreation,
    initialError: WorkshopPageError?,
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
) {
    val colors = deepDeepColors()
    val scope = rememberCoroutineScope()
    val errorFix = stringResource(R.string.workshop_agent_error_fix)
    val failedText = stringResource(R.string.workshop_agent_failed)
    var instruction by remember(creation.manifest.id) {
        mutableStateOf(buildFixInstruction(initialError, errorFix))
    }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(AgentPhase.IDLE) }
    var toolName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(output) { scrollState.scrollTo(scrollState.maxValue) }

    fun applyEvent(event: WorkshopAgentEvent) {
        when (event) {
            WorkshopAgentEvent.Started -> {
                phase = AgentPhase.THINKING
                toolName = null
            }

            is WorkshopAgentEvent.Text -> {
                output += event.delta
                phase = AgentPhase.GENERATING
            }

            is WorkshopAgentEvent.Tool -> {
                phase = AgentPhase.TOOL
                toolName = event.name
            }

            WorkshopAgentEvent.Finished -> {
                phase = AgentPhase.DONE
                toolName = null
            }
        }
    }

    fun start() {
        val prompt = instruction.trim()
        if (prompt.isEmpty() || running) return
        running = true
        output = ""
        error = null
        toolName = null
        phase = AgentPhase.THINKING
        job = scope.launch {
            try {
                val result = service.runFull(creation, prompt) { event ->
                    scope.launch { applyEvent(event) }
                }
                if (output.isBlank()) output = result
                running = false
                phase = AgentPhase.DONE
                onApplied()
                onDismiss()
            } catch (cancelled: CancellationException) {
                running = false
                phase = AgentPhase.IDLE
            } catch (failure: Exception) {
                running = false
                phase = AgentPhase.FAILED
                error = failure.message ?: failedText
            }
        }
    }

    fun stop() {
        if (!running) return
        job?.cancel()
        job = null
        running = false
        phase = AgentPhase.IDLE
        scope.launch { runCatching { service.cancel(creation.manifest.id) } }
    }

    val statusLabel = when (phase) {
        AgentPhase.THINKING -> stringResource(R.string.workshop_agent_thinking)
        AgentPhase.GENERATING -> stringResource(R.string.workshop_agent_generating)
        AgentPhase.TOOL -> stringResource(R.string.workshop_agent_tool, toolName.orEmpty())
        AgentPhase.DONE -> stringResource(R.string.workshop_agent_done)
        AgentPhase.FAILED -> stringResource(R.string.workshop_agent_failed)
        AgentPhase.IDLE -> ""
    }

    DdSheet(onDismiss = { if (!running) onDismiss() }) {
        DdSheetHeader(
            title = stringResource(R.string.workshop_agent_title),
            onClose = { if (!running) onDismiss() },
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.workshop_agent_hint),
                style = DdType.bodySmall,
                color = colors.inkTertiary,
            )
            Spacer(Modifier.height(12.dp))
            DdTextArea(
                value = instruction,
                onValueChange = { if (!running) instruction = it },
                placeholder = stringResource(R.string.workshop_agent_placeholder),
                minHeight = 112.dp,
                maxHeight = 180.dp,
                testTag = "workshop_agent_input",
            )
            Spacer(Modifier.height(12.dp))
            if (running || output.isNotEmpty() || phase == AgentPhase.DONE) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.wash)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (running && phase != AgentPhase.TOOL) {
                            DdTypingDots(color = colors.inkTertiary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = statusLabel,
                            style = DdType.bodySmall,
                            color = if (error != null) colors.danger else colors.inkTertiary,
                        )
                    }
                    if (output.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = output,
                            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                            style = DdType.bodySmall,
                            color = colors.inkSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            error?.let { message ->
                Text(
                    text = message,
                    style = DdType.bodySmall,
                    color = colors.danger,
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DdTextAction(
                    text = stringResource(R.string.workshop_close),
                    onClick = { if (!running) onDismiss() },
                    modifier = Modifier.testTag("workshop_agent_close"),
                    color = colors.inkSecondary,
                    enabled = !running,
                )
                Spacer(Modifier.weight(1f))
                DdPrimaryButton(
                    text = stringResource(
                        if (running) R.string.workshop_agent_stop else R.string.workshop_agent_start,
                    ),
                    onClick = { if (running) stop() else start() },
                    modifier = Modifier.testTag("workshop_agent_submit"),
                    enabled = running || instruction.isNotBlank(),
                )
            }
        }
    }
}

private fun buildFixInstruction(error: WorkshopPageError?, prefix: String): String {
    if (error == null) return ""
    val stack = error.stack?.takeIf { it.isNotBlank() }
    return buildString {
        append(prefix)
        append(":\n")
        append(error.message)
        if (stack != null) {
            append('\n')
            append(stack)
        }
    }
}
