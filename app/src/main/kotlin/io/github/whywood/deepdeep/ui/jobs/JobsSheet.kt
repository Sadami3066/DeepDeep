package io.github.whywood.deepdeep.ui.jobs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdHairline
import io.github.whywood.deepdeep.design.DdIcon
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.runtime.JobSnapshot
import io.github.whywood.deepdeep.runtime.JobStatus
import java.util.Locale
import kotlinx.coroutines.delay

private const val JOBS_OUTPUT_MAX_HEIGHT_DP = 240
private const val JOBS_KILL_CONFIRM_MILLIS = 3_000L
private const val JOBS_TICK_MILLIS = 1_000L

@Composable
fun JobsSheet(
    jobs: List<JobSnapshot>,
    selectedJobId: String?,
    output: String?,
    onSelect: (String) -> Unit,
    onCloseOutput: () -> Unit,
    onKill: (String) -> Unit,
    onClearSettled: () -> Unit,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmingKillId by remember { mutableStateOf<String?>(null) }
    val running = jobs.filter { it.status == JobStatus.RUNNING }.sortedBy { it.startedAt }
    val settled = jobs
        .filter { it.status != JobStatus.RUNNING }
        .sortedByDescending { it.finishedAt ?: it.startedAt }
    val ordered = running + settled
    val hasRunning = running.isNotEmpty()
    LaunchedEffect(hasRunning) {
        if (!hasRunning) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(JOBS_TICK_MILLIS)
        }
    }
    LaunchedEffect(confirmingKillId) {
        if (confirmingKillId != null) {
            delay(JOBS_KILL_CONFIRM_MILLIS)
            confirmingKillId = null
        }
    }
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(
            title = stringResource(R.string.jobs_title),
            onClose = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            if (ordered.isEmpty()) {
                EmptyJobs()
            } else {
                ordered.forEachIndexed { index, job ->
                    if (index > 0) DdHairline()
                    JobRow(
                        job = job,
                        selected = job.id == selectedJobId,
                        now = now,
                        confirmingKill = confirmingKillId == job.id,
                        onOpen = { onSelect(job.id) },
                        onKill = {
                            if (confirmingKillId == job.id) {
                                confirmingKillId = null
                                onKill(job.id)
                            } else {
                                confirmingKillId = job.id
                            }
                        },
                    )
                    if (job.id == selectedJobId) {
                        JobOutput(output = output, onCloseOutput = onCloseOutput)
                    }
                }
                if (settled.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    DdTextAction(
                        text = stringResource(R.string.jobs_clear_settled),
                        onClick = onClearSettled,
                        modifier = Modifier.testTag("jobs_clear_settled"),
                        color = deepDeepColors().inkTertiary,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EmptyJobs() {
    val colors = deepDeepColors()
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DdIcon(icon = DdIcons.Terminal, tint = colors.inkTertiary, size = 22.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.jobs_empty_title),
            style = DdType.body,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.jobs_empty_hint),
            style = DdType.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JobRow(
    job: JobSnapshot,
    selected: Boolean,
    now: Long,
    confirmingKill: Boolean,
    onOpen: () -> Unit,
    onKill: () -> Unit,
) {
    val colors = deepDeepColors()
    val running = job.status == JobStatus.RUNNING
    val statusColor = when (job.status) {
        JobStatus.RUNNING -> colors.accent
        JobStatus.FAILED -> colors.danger
        JobStatus.DONE, JobStatus.KILLED, JobStatus.INTERRUPTED -> colors.inkTertiary
    }
    val end = if (running) now else (job.finishedAt ?: now)
    val elapsed = (end - job.startedAt).coerceAtLeast(0L)
    val label = job.label.ifBlank { job.command }
    DdPressable(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("jobs_item_${job.id}"),
        shape = RectangleShape,
        background = if (selected) colors.wash else Color.Transparent,
        contentDescription = label,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIcon(
                icon = DdIcons.Terminal,
                tint = if (running) colors.accent else colors.inkTertiary,
                size = 16.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = DdType.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = jobStatusLabel(job.status),
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = statusColor,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = formatDuration(elapsed),
                style = DdType.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.inkTertiary,
                maxLines = 1,
            )
            if (running) {
                Spacer(Modifier.width(4.dp))
                DdTextAction(
                    text = stringResource(
                        if (confirmingKill) R.string.jobs_kill_confirm else R.string.jobs_kill,
                    ),
                    onClick = onKill,
                    modifier = Modifier.testTag("jobs_kill_${job.id}"),
                    color = if (confirmingKill) colors.danger else colors.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun JobOutput(output: String?, onCloseOutput: () -> Unit) {
    val colors = deepDeepColors()
    val body = if (output == null) {
        stringResource(R.string.jobs_output_loading)
    } else {
        output.ifBlank { stringResource(R.string.jobs_output_empty) }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.jobs_output),
                modifier = Modifier.weight(1f),
                style = DdType.micro,
                color = colors.inkTertiary,
            )
            DdTextAction(
                text = stringResource(R.string.jobs_output_close),
                onClick = onCloseOutput,
                modifier = Modifier.testTag("jobs_output_close"),
                color = colors.inkTertiary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = JOBS_OUTPUT_MAX_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.codeBackground)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                text = body,
                style = DdType.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun jobStatusLabel(status: JobStatus): String = stringResource(
    when (status) {
        JobStatus.RUNNING -> R.string.jobs_status_running
        JobStatus.DONE -> R.string.jobs_status_done
        JobStatus.FAILED -> R.string.jobs_status_failed
        JobStatus.KILLED -> R.string.jobs_status_killed
        JobStatus.INTERRUPTED -> R.string.jobs_status_interrupted
    },
)

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
