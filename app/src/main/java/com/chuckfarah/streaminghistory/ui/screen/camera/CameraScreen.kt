package com.chuckfarah.streaminghistory.ui.screen.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/**
 * Camera capture screen — Step 9.
 *
 * - Opens the device camera using CameraX
 * - Displays a live, full-screen preview with a framing guide
 * - Captures a single still image on tap
 * - Keeps the captured [Bitmap] in [CameraViewModel] for later OCR processing
 * - Does NOT save the image to the gallery or external storage
 * - Handles CAMERA permission and displays a clear error state on failure
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor       = remember { ContextCompat.getMainExecutor(context) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture         = remember { ImageCapture.Builder().build() }

    var provider    by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var error       by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    val capturedImage by viewModel.capturedImage.collectAsState()
    val ocrResult     by viewModel.ocrResult.collectAsState()
    val isRecognizing by viewModel.isRecognizing.collectAsState()

    // ── Permission handling ───────────────────────────────────────────────────
    var permissionState by remember {
        mutableStateOf(
            if (hasCameraPermission(context)) PermissionState.Granted
            else PermissionState.Idle
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = if (granted) PermissionState.Granted else PermissionState.Denied
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission(context)) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── Camera provider async init + cleanup on dispose ──────────────────────
    DisposableEffect(Unit) {
        val listener = Runnable {
            try {
                provider = cameraProviderFuture.get()
            } catch (e: Exception) {
                error = "Unable to access camera: ${e.message}"
            }
        }
        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            provider?.unbindAll()
            provider = null
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan TV Screen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                error != null -> ErrorState(error = error!!, onBack = onBack)
                permissionState == PermissionState.Denied -> PermissionDeniedState(onBack = onBack)
                ocrResult != null -> OcrResultView(
                    ocrResult   = ocrResult!!,
                    onResult    = onResult,
                    onTryAgain  = viewModel::clearImage,
                    onBack      = onBack,
                )
                capturedImage != null -> CapturedImageState(
                    bitmap    = capturedImage!!,
                    onRetake  = viewModel::clearImage,
                    onUse     = viewModel::recognizeCapturedImage,
                    onBack    = onBack,
                )
                permissionState == PermissionState.Granted -> LivePreview(
                    provider      = provider,
                    imageCapture  = imageCapture,
                    lifecycleOwner= lifecycleOwner,
                    executor      = executor,
                    isCapturing   = isCapturing,
                    onCapture     = { isCapturing = true },
                    onCaptured    = { bitmap ->
                        isCapturing = false
                        viewModel.onImageCaptured(bitmap)
                    },
                    onError       = { msg ->
                        isCapturing = false
                        error = msg
                    },
                    onBindError   = { msg -> error = msg },
                )
                else -> CircularProgressIndicator()
            }

            if (isCapturing || isRecognizing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

private enum class PermissionState { Idle, Granted, Denied }

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

// ── Live preview composable ────────────────────────────────────────────────────

@Composable
private fun LivePreview(
    provider: ProcessCameraProvider?,
    imageCapture: ImageCapture,
    lifecycleOwner: LifecycleOwner,
    executor: Executor,
    isCapturing: Boolean,
    onCapture: () -> Unit,
    onCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
    onBindError: (String) -> Unit,
) {
    val context = LocalContext.current
    val previewViewRef = remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        update = { view -> previewViewRef.value = view },
        modifier = Modifier.fillMaxSize(),
    )

    LaunchedEffect(provider, previewViewRef.value) {
        val cameraProvider = provider ?: return@LaunchedEffect
        val previewView = previewViewRef.value ?: return@LaunchedEffect

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        } catch (e: Exception) {
            onBindError("Could not start camera preview: ${e.message}")
        }
    }

    // Framing guide overlay
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp, 120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Transparent),
        ) {
            // We use a subtle border drawn by placing the guide on top
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Transparent),
            )
        }

        Text(
            text = "Frame the title",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 90.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        Button(
            onClick = {
                if (isCapturing) return@Button
                onCapture()
                captureImage(context, imageCapture, executor, onCaptured, onError)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        ) {
            Text("Capture")
        }
    }
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit,
) {
    val file = File(context.cacheDir, "shc_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bitmap = BitmapFactory.decodeFile(file.path)
                file.delete()

                if (bitmap != null) {
                    onCaptured(bitmap)
                } else {
                    onError("Captured image could not be decoded")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onError("Capture failed: ${exception.message}")
            }
        }
    )
}

// ── Sub-state composables ──────────────────────────────────────────────────────

@Composable
private fun ErrorState(error: String, onBack: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Camera error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text  = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onBack) { Text("Go back") }
    }
}

@Composable
private fun PermissionDeniedState(onBack: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Camera permission required",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text  = "Camera access is needed to scan the TV screen. " +
                    "Please enable the permission in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onBack) { Text("Go back") }
    }
}

@Composable
private fun CapturedImageState(
    bitmap: Bitmap,
    onRetake: () -> Unit,
    onUse: () -> Unit,
    onBack: () -> Unit,
    isOcrButton: Boolean = true,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Image captured",
            style = MaterialTheme.typography.titleMedium,
        )
        Image(
            bitmap              = bitmap.asImageBitmap(),
            contentDescription  = "Captured TV screen",
            modifier            = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onRetake) { Text("Retake") }
            Button(onClick = onUse) { Text(if (isOcrButton) "Read text" else "Continue") }
        }
    }
}
