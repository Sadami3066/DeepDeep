package io.github.whywood.deepdeep.ui.common.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.whywood.deepdeep.R
import io.github.whywood.deepdeep.design.DdAlpha
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSpacing
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import io.github.whywood.deepdeep.ui.common.scan.ScanDecision
import io.github.whywood.deepdeep.ui.common.scan.ScanLauncher
import io.github.whywood.deepdeep.ui.common.scan.ScanMatcher
import io.github.whywood.deepdeep.ui.common.scan.ScanPrimary
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategy
import io.github.whywood.deepdeep.ui.common.scan.ScanStrategyStore
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext



private const val CAMERA_CACHE_TTL_MS = 60 * 60 * 1000L
private const val SCAN_CARD_HIDE_MS = 1200L

@Composable
internal fun CameraCaptureSheet(
    onDismiss: () -> Unit,
    onCaptured: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { granted = context.hasCameraPermission() }
    LaunchedEffect(Unit) {
        if (!context.hasCameraPermission()) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    val store = remember(context) { ScanStrategyStore(context) }
    var strategies by remember { mutableStateOf<List<ScanStrategy>>(emptyList()) }
    LaunchedEffect(store) {
        strategies = withContext(Dispatchers.IO) { store.load().matching }
    }
    DdSheet(onDismiss = onDismiss, fullBleed = true) {
        if (granted) {
            CameraBody(
                strategies = strategies,
                onCaptured = onCaptured,
                onDismiss = onDismiss,
            )
        } else {
            CameraPermissionHint(
                onOpenSettings = {
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun CameraPermissionHint(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DdSpacing.Xxxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.camera_permission_hint),
                style = DdType.body,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(DdSpacing.Lg))
            DdPrimaryButton(
                text = stringResource(R.string.camera_open_settings),
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DdIconButton(
            icon = DdIcons.Close,
            onClick = onDismiss,
            contentDescription = stringResource(R.string.camera_close),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(DdSpacing.Xs),
            tint = Color.White,
            iconSize = 20.dp,
        )
    }
}

@Composable
private fun CameraBody(
    strategies: List<ScanStrategy>,
    onCaptured: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captureFailed = stringResource(R.string.camera_failed)
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    val active = remember { mutableStateOf(true) }
    val detected = remember { mutableStateOf<List<DetectedCode>>(emptyList()) }
    val lastBarcodeAt = remember { mutableLongStateOf(0L) }
    val installedPackages: (String) -> Boolean = remember {
        { packageName ->
            runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess
        }
    }
    val analyzer = remember {
        CameraBarcodeAnalyzer { codes ->
            lastBarcodeAt.longValue = SystemClock.elapsedRealtime()
            if (codes.isNotEmpty()) detected.value = codes
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            val codes = detected.value
            if (codes.isNotEmpty() &&
                SystemClock.elapsedRealtime() - lastBarcodeAt.longValue > SCAN_CARD_HIDE_MS
            ) {
                detected.value = emptyList()
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        onDispose {
            active.value = false
            surfaceRequest = null
            provider?.unbindAll()
            analysisExecutor.shutdown()
            analyzer.close()
        }
    }
    LaunchedEffect(lifecycleOwner, lensFacing) {
        val cameraProvider = try {
            awaitCameraProvider(context)
        } catch (_: Exception) {
            unavailable = true
            return@LaunchedEffect
        }
        provider = cameraProvider
        val resolvedFacing = cameraProvider.resolveLensFacing(lensFacing)
        if (resolvedFacing != lensFacing) {
            lensFacing = resolvedFacing
            return@LaunchedEffect
        }
        try {
            cameraProvider.unbindAll()
            surfaceRequest = null
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider { request -> surfaceRequest = request }
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1280, 960),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                            ),
                        )
                        .build(),
                )
                .build()
                .apply { setAnalyzer(analysisExecutor, analyzer) }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture, analysis)
            } catch (_: IllegalArgumentException) {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            }
            imageCapture = capture
            unavailable = false
        } catch (_: Exception) {
            unavailable = true
        }
    }
    val takePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture != null && !capturing) {
            capturing = true
            val directory = File(context.cacheDir, "camera")
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            val staleBefore = System.currentTimeMillis() - CAMERA_CACHE_TTL_MS
            directory.listFiles()
                ?.filter { it.lastModified() < staleBefore }
                ?.forEach { it.delete() }
            directory.mkdirs()
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (!active.value) {
                            file.delete()
                            return
                        }
                        onCaptured(
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.files",
                                file,
                            ),
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        if (!active.value) return
                        capturing = false
                        Toast.makeText(context, captureFailed, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
    val decisions = remember(detected.value, strategies) {
        detected.value
            .map { code -> ScanMatcher.resolve(code.raw, strategies, installedPackages) }
            .sortedBy { priorityOf(it.primary) }
    }
    var pickerOpen by remember { mutableStateOf(false) }
    val scanCard = decisions.ifEmpty { null }
    var lastDecisions by remember { mutableStateOf<List<ScanDecision>>(emptyList()) }
    LaunchedEffect(scanCard) {
        if (scanCard != null) lastDecisions = scanCard
    }
    val cardDecisions = scanCard ?: lastDecisions
    val pickerStrategies = remember(strategies) {
        strategies.filter { installedPackages(it.packageName) }.distinctBy { it.packageName }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f),
    ) {
        val request = surfaceRequest
        when {
            unavailable -> Text(
                text = stringResource(R.string.camera_unavailable),
                style = DdType.body,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = DdSpacing.Xxxl),
            )

            request != null -> CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
                implementationMode = ImplementationMode.EMBEDDED,
                contentScale = ContentScale.Crop,
            )

            else -> DdSpinner(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = DdAlpha.Border)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = DdSpacing.Md, end = DdSpacing.Md, bottom = DdSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DdIconButton(
                icon = DdIcons.Close,
                onClick = onDismiss,
                contentDescription = stringResource(R.string.camera_close),
                tint = Color.White,
            )
            Spacer(Modifier.weight(1f))
            ShutterButton(
                enabled = imageCapture != null && !capturing,
                capturing = capturing,
                contentDescription = stringResource(R.string.camera_shutter),
                onClick = takePhoto,
            )
            Spacer(Modifier.weight(1f))
            DdIconButton(
                icon = DdIcons.Refresh,
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                contentDescription = stringResource(R.string.camera_switch_lens),
                tint = Color.White,
                enabled = !capturing,
            )
        }
        AnimatedVisibility(
            visible = scanCard != null && !pickerOpen,
            enter = fadeIn(tween(140)) + slideInVertically(tween(160)) { height -> height / 4 },
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = DdSpacing.Lg, end = DdSpacing.Lg, bottom = 100.dp),
        ) {
            ScanResultCard(
                decisions = cardDecisions,
                showAppPicker = cardDecisions.any { it.primary is ScanPrimary.Copy },
                onActivate = { decision ->
                    if (ScanLauncher.launch(context, decision)) onDismiss()
                },
                onOpenAppPicker = { pickerOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (pickerOpen) {
            ScanAppPickerOverlay(
                strategies = pickerStrategies,
                onSelect = { strategy ->
                    if (ScanLauncher.launchStrategy(context, strategy)) onDismiss()
                },
                onDismiss = { pickerOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun priorityOf(primary: ScanPrimary): Int = when (primary) {
    is ScanPrimary.AppScan -> 0
    is ScanPrimary.Browse -> 1
    ScanPrimary.Copy -> 2
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    capturing: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = deepDeepColors()
    DdPressable(
        onClick = onClick,
        modifier = Modifier.size(72.dp).border(3.dp, Color.White, CircleShape),
        shape = CircleShape,
        enabled = enabled,
        contentDescription = contentDescription,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (enabled || capturing) Color.White else Color.White.copy(alpha = DdAlpha.Divider)),
            contentAlignment = Alignment.Center,
        ) {
            if (capturing) {
                DdSpinner(size = 20.dp, color = colors.ink, strokeWidth = 2.dp)
            }
        }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                if (continuation.isActive) {
                    try {
                        continuation.resume(future.get())
                    } catch (exception: Exception) {
                        continuation.resumeWithException(exception)
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation { future.cancel(false) }
    }

private fun ProcessCameraProvider.resolveLensFacing(requested: Int): Int {
    if (hasCameraLens(requested)) return requested
    val fallback = if (requested == CameraSelector.LENS_FACING_BACK) {
        CameraSelector.LENS_FACING_FRONT
    } else {
        CameraSelector.LENS_FACING_BACK
    }
    return if (hasCameraLens(fallback)) fallback else requested
}

private fun ProcessCameraProvider.hasCameraLens(lensFacing: Int): Boolean = runCatching {
    hasCamera(CameraSelector.Builder().requireLensFacing(lensFacing).build())
}.getOrDefault(false)
