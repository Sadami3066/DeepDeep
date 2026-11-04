package io.github.whywood.deepdeep.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.github.whywood.deepdeep.design.DdIconButton
import io.github.whywood.deepdeep.design.DdIcons
import io.github.whywood.deepdeep.design.DdPressable
import io.github.whywood.deepdeep.design.DdPrimaryButton
import io.github.whywood.deepdeep.design.DdSheet
import io.github.whywood.deepdeep.design.DdSheetHeader
import io.github.whywood.deepdeep.design.DdSpinner
import io.github.whywood.deepdeep.design.DdType
import io.github.whywood.deepdeep.design.deepDeepColors
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val CAMERA_CACHE_TTL_MS = 60 * 60 * 1000L

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
    DdSheet(onDismiss = onDismiss) {
        DdSheetHeader(
            title = stringResource(R.string.camera_title),
            onClose = onDismiss,
        )
        if (granted) {
            CameraBody(onCaptured = onCaptured)
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
            )
        }
    }
}

@Composable
private fun CameraPermissionHint(onOpenSettings: () -> Unit) {
    val colors = deepDeepColors()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.camera_permission_hint),
            style = DdType.body,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        DdPrimaryButton(
            text = stringResource(R.string.camera_open_settings),
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CameraBody(onCaptured: (Uri) -> Unit) {
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
    DisposableEffect(lifecycleOwner) {
        onDispose {
            active.value = false
            surfaceRequest = null
            provider?.unbindAll()
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
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                preview,
                capture,
            )
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val request = surfaceRequest
            when {
                unavailable -> Text(
                    text = stringResource(R.string.camera_unavailable),
                    style = DdType.body,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )

                request != null -> CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier.fillMaxSize(),
                    implementationMode = ImplementationMode.EMBEDDED,
                    contentScale = ContentScale.Crop,
                )

                else -> DdSpinner(color = Color.White)
            }
            if (!unavailable) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(148.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    ShutterButton(
                        enabled = imageCapture != null && !capturing,
                        capturing = capturing,
                        contentDescription = stringResource(R.string.camera_shutter),
                        onClick = takePhoto,
                    )
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
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
                            background = Color.White.copy(alpha = 0.14f),
                            enabled = !capturing,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
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
        modifier = Modifier.size(76.dp).border(3.dp, Color.White, CircleShape),
        shape = CircleShape,
        enabled = enabled,
        contentDescription = contentDescription,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (enabled || capturing) Color.White else Color.White.copy(alpha = 0.4f)),
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
