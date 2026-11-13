package io.github.whywood.deepdeep.plugin.host.interaction

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdDialog
import io.github.whywood.deepdeep.design.DdElevation
import io.github.whywood.deepdeep.design.DdImageScaling
import io.github.whywood.deepdeep.design.DdListRow
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdTextAction
import io.github.whywood.deepdeep.design.DdTextArea
import io.github.whywood.deepdeep.design.DdTextField
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.plugin.api.PluginActivityResult
import io.github.whywood.deepdeep.plugin.api.UiImageSource
import io.github.whywood.deepdeep.ui.common.chat.CameraCaptureSheet
import io.github.whywood.deepdeep.ui.common.chat.readQrCodes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PluginInteractionOverlay() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickFailedLabel = stringResource(R.string.plugin_host_pick_failed)
    val directoryFailedLabel = stringResource(R.string.plugin_host_directory_failed)
    val activeRequest by PluginInteractionQueue.activeRequest.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val active = activeRequest
    val currentRequest = rememberUpdatedState(active)

    fun completePickedFiles(request: PluginInteractionRequest.Files, uris: List<Uri>) {
        if (request.completion.isCompleted) return
        if (uris.isEmpty()) {
            request.complete(emptyList())
            return
        }
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.take(request.request.maxCount.coerceAtLeast(1)).mapNotNull { uri ->
                    PluginPickedFiles.import(context, request.pluginId, uri)
                }
            }
            if (files.isEmpty()) {
                PluginInteractionHost.showSnackbar(
                    pickFailedLabel,
                    null,
                    null,
                )
            }
            request.complete(files)
        }
    }

    fun completePickedImage(request: PluginInteractionRequest.Image, uri: Uri?) {
        if (request.completion.isCompleted) return
        if (uri == null) {
            request.complete(null)
            return
        }
        scope.launch {
            val file = withContext(Dispatchers.IO) { PluginPickedFiles.import(context, request.pluginId, uri) }
            if (file == null) {
                PluginInteractionHost.showSnackbar(
                    pickFailedLabel,
                    null,
                    null,
                )
            }
            request.complete(file)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val request = currentRequest.value as? PluginInteractionRequest.Permissions
            ?: return@rememberLauncherForActivityResult
        if (request.completion.isCompleted) return@rememberLauncherForActivityResult
        request.complete(grants)
    }
    val documentsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val request = currentRequest.value as? PluginInteractionRequest.Files ?: return@rememberLauncherForActivityResult
        if (request.completion.isCompleted) return@rememberLauncherForActivityResult
        completePickedFiles(request, uris)
    }
    val documentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val request = currentRequest.value as? PluginInteractionRequest.Files ?: return@rememberLauncherForActivityResult
        if (request.completion.isCompleted) return@rememberLauncherForActivityResult
        completePickedFiles(request, listOfNotNull(uri))
    }
    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val request = currentRequest.value as? PluginInteractionRequest.Directory
            ?: return@rememberLauncherForActivityResult
        if (request.completion.isCompleted) return@rememberLauncherForActivityResult
        if (uri == null) {
            request.complete(null)
        } else {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    PluginPickedFiles.resolveDirectoryPath(context, uri)
                }
                if (path == null) {
                    PluginInteractionHost.showSnackbar(
                        directoryFailedLabel,
                        null,
                        null,
                    )
                }
                request.complete(path)
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val request = currentRequest.value as? PluginInteractionRequest.Image
            ?: return@rememberLauncherForActivityResult
        if (request.completion.isCompleted) return@rememberLauncherForActivityResult
        completePickedImage(request, uri)
    }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val request = currentRequest.value as? PluginInteractionRequest.StartActivity
            ?: return@rememberLauncherForActivityResult
        if (request.completion.isCompleted) return@rememberLauncherForActivityResult
        request.complete(
            PluginActivityResult(
                resultCode = result.resultCode,
                data = result.data,
                cancelled = result.resultCode == Activity.RESULT_CANCELED,
            ),
        )
    }

    LaunchedEffect(active?.key, resumed) {
        val request = active ?: return@LaunchedEffect
        if (!resumed || PluginInteractionQueue.lastLaunchedKey == request.key) return@LaunchedEffect
        PluginInteractionQueue.lastLaunchedKey = request.key
        when (request) {
            is PluginInteractionRequest.Permissions -> permissionsLauncher.launch(request.permissions)
            is PluginInteractionRequest.StartActivity -> activityLauncher.launch(request.intent)
            is PluginInteractionRequest.Files -> {
                val mimeTypes = request.request.mimeTypes.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
                if (request.request.multiple) {
                    documentsLauncher.launch(mimeTypes.toTypedArray())
                } else {
                    documentLauncher.launch(arrayOf(mimeTypes.first()))
                }
            }

            is PluginInteractionRequest.Directory -> treeLauncher.launch(null)
            is PluginInteractionRequest.Image -> {
                if (request.request.source == UiImageSource.GALLERY) {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }

            else -> Unit
        }
    }

    when (val request = active) {
        is PluginInteractionRequest.Confirm -> ConfirmInteraction(request)
        is PluginInteractionRequest.Prompt -> PromptInteraction(request)
        is PluginInteractionRequest.Choose -> ChooseInteraction(request)
        is PluginInteractionRequest.PickDate -> DateInteraction(request)
        is PluginInteractionRequest.PickTime -> TimeInteraction(request)
        is PluginInteractionRequest.Image -> if (request.request.source == UiImageSource.CAMERA) {
            CameraInteraction(
                onDismiss = { request.complete(null) },
                onCaptured = { uri -> completePickedImage(request, uri) },
            )
        }

        is PluginInteractionRequest.Scan -> ScanInteraction(request)
        else -> Unit
    }

    PluginSnackbarOverlay()
}

@Composable
private fun ConfirmInteraction(request: PluginInteractionRequest.Confirm) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = { request.complete(false) },
        title = request.request.title,
        content = {
            request.request.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(text = message, style = DdType.bodySmall, color = colors.inkSecondary)
            }
        },
        actions = {
            DdTextAction(
                text = request.request.cancelLabel ?: stringResource(R.string.plugin_host_cancel),
                onClick = { request.complete(false) },
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = request.request.confirmLabel ?: stringResource(R.string.plugin_host_confirm),
                onClick = { request.complete(true) },
                color = if (request.request.destructive) colors.danger else colors.accent,
            )
        },
    )
}

@Composable
private fun PromptInteraction(request: PluginInteractionRequest.Prompt) {
    val colors = deepDeepColors()
    var value by rememberSaveable(request.key) { mutableStateOf(request.request.initial) }
    DdDialog(
        onDismissRequest = { request.complete(null) },
        title = request.request.title,
        content = {
            request.request.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(text = message, style = DdType.bodySmall, color = colors.inkSecondary)
                Spacer(Modifier.height(DdSpacing.Md))
            }
            val placeholder = request.request.placeholder
                ?: stringResource(R.string.plugin_host_prompt_hint)
            if (request.request.multiline) {
                DdTextArea(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = placeholder,
                    minHeight = 120.dp,
                )
            } else {
                DdTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = placeholder,
                )
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.plugin_host_cancel),
                onClick = { request.complete(null) },
                color = colors.inkSecondary,
            )
            DdTextAction(
                text = stringResource(R.string.plugin_host_confirm),
                onClick = { request.complete(value) },
                color = colors.accent,
            )
        },
    )
}

@Composable
private fun ChooseInteraction(request: PluginInteractionRequest.Choose) {
    val colors = deepDeepColors()
    DdDialog(
        onDismissRequest = { request.complete(null) },
        title = request.request.title,
        content = {
            request.request.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(text = message, style = DdType.bodySmall, color = colors.inkSecondary)
                Spacer(Modifier.height(DdSpacing.Sm))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                request.request.options.forEach { option ->
                    DdListRow(
                        title = option.label,
                        subtitle = option.description,
                        onClick = { request.complete(option.id) },
                        showChevron = true,
                    )
                }
            }
        },
        actions = {
            DdTextAction(
                text = stringResource(R.string.plugin_host_cancel),
                onClick = { request.complete(null) },
                color = colors.inkSecondary,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateInteraction(request: PluginInteractionRequest.PickDate) {
    val colors = deepDeepColors()
    val minDate = remember(request.request.minMs) { request.request.minMs?.toLocalDate() }
    val maxDate = remember(request.request.maxMs) { request.request.maxMs?.toLocalDate() }
    val initialDate = remember(request.request.initialMs) { request.request.initialMs?.toLocalDate() ?: LocalDate.now() }
    val selectableDates = remember(minDate, maxDate) { BoundedSelectableDates(minDate, maxDate) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = { request.complete(null) },
        confirmButton = {
            DdTextAction(
                text = stringResource(R.string.plugin_host_confirm),
                onClick = { request.complete(state.selectedDateMillis?.toLocalDayMillis()) },
                color = colors.accent,
            )
        },
        dismissButton = {
            DdTextAction(
                text = stringResource(R.string.plugin_host_cancel),
                onClick = { request.complete(null) },
                color = colors.inkSecondary,
            )
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeInteraction(request: PluginInteractionRequest.PickTime) {
    val colors = deepDeepColors()
    val now = remember { LocalTime.now() }
    val state = rememberTimePickerState(
        initialHour = request.request.initialHour ?: now.hour,
        initialMinute = request.request.initialMinute ?: now.minute,
        is24Hour = request.request.is24Hour,
    )
    TimePickerDialog(
        onDismissRequest = { request.complete(null) },
        confirmButton = {
            DdTextAction(
                text = stringResource(R.string.plugin_host_confirm),
                onClick = { request.complete(state.hour * 60 + state.minute) },
                color = colors.accent,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.plugin_host_time_title),
                modifier = Modifier.padding(horizontal = DdSpacing.Xxl, vertical = DdSpacing.Md),
                style = DdType.heading,
                color = colors.ink,
            )
        },
        dismissButton = {
            DdTextAction(
                text = stringResource(R.string.plugin_host_cancel),
                onClick = { request.complete(null) },
                color = colors.inkSecondary,
            )
        },
    ) {
        TimePicker(state = state)
    }
}

@Composable
private fun CameraInteraction(
    onDismiss: () -> Unit,
    onCaptured: (Uri) -> Unit,
) {
    CameraCaptureSheet(onDismiss = onDismiss, onCaptured = onCaptured)
}

@Composable
private fun ScanInteraction(request: PluginInteractionRequest.Scan) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanFailedLabel = stringResource(R.string.plugin_host_scan_failed)
    CameraCaptureSheet(
        onDismiss = { request.complete(null) },
        onCaptured = { uri ->
            scope.launch {
                val code = decodeQrCode(context, uri)
                if (code == null) {
                    PluginInteractionHost.showSnackbar(
                        scanFailedLabel,
                        null,
                        null,
                    )
                }
                request.complete(code)
            }
        },
    )
}

private suspend fun decodeQrCode(context: Context, uri: Uri): String? {
    val bitmap = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val sample = DdImageScaling.sampleSizeFor(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    } ?: return null
    return runCatching { readQrCodes(bitmap).firstOrNull() }.getOrNull()?.takeIf { it.isNotBlank() }
}

@Composable
private fun PluginSnackbarOverlay() {
    val colors = deepDeepColors()
    var current by remember { mutableStateOf<PluginSnackbarMessage?>(null) }
    LaunchedEffect(Unit) {
        PluginInteractionHost.snackbarMessages.collect { message ->
            current = message
            delay(if (message.actionLabel.isNullOrBlank()) 3_200L else 6_000L)
            if (current === message) current = null
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + slideInVertically { height -> height / 2 },
            exit = fadeOut() + slideOutVertically { height -> height / 2 },
        ) {
            val message = current
            if (message != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = DdSpacing.Xxl, vertical = DdSpacing.Lg)
                        .navigationBarsPadding()
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .shadow(DdElevation.Soft, RoundedCornerShape(DdSpacing.Md), clip = false)
                        .clip(RoundedCornerShape(DdSpacing.Md))
                        .background(colors.raised)
                        .padding(start = DdSpacing.Lg, end = DdSpacing.Xs, top = DdSpacing.Sm, bottom = DdSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message.message,
                        modifier = Modifier.weight(1f),
                        style = DdType.bodySmall,
                        color = colors.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!message.actionLabel.isNullOrBlank()) {
                        Spacer(Modifier.height(DdSpacing.Xs))
                        DdTextAction(
                            text = message.actionLabel,
                            onClick = {
                                message.action?.invoke()
                                current = null
                            },
                            color = colors.accent,
                        )
                    }
                }
            }
        }
    }
}

private class BoundedSelectableDates(
    private val minDate: LocalDate?,
    private val maxDate: LocalDate?,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = utcTimeMillis.toLocalDate()
        if (minDate != null && date.isBefore(minDate)) return false
        if (maxDate != null && date.isAfter(maxDate)) return false
        return true
    }

    override fun isSelectableYear(year: Int): Boolean {
        if (minDate != null && year < minDate.year) return false
        if (maxDate != null && year > maxDate.year) return false
        return true
    }
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDayMillis(): Long =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
