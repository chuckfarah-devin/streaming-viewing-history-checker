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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onResult: (String) -> Unit,
    onSearchManual: () -> Unit,
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

    val capturedImage     by viewModel.capturedImage.collectAsState()
    val ocrResult         by viewModel.ocrResult.collectAsState()
    val isRecognizing     by viewModel.isRecognizing.collectAsState()
    val visionFallbackState by viewModel.visionFallbackState.collectAsState()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan TV Screen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
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
                    ocrResult      = ocrResult!!,
                    onResult       = onResult,
                    onTryAgain     = viewModel::clearImage,
                    onSearchManual = onSearchManual,
                    onBack         = onBack,
                    onTryEnhanced  = viewModel::onTryEnhancedRecognition,
                    canTryEnhanced = viewModel.visionEnabled,
                )
                capturedImage != null -> CapturedImageState(
                    bitmap    = capturedImage!!,
                    onRetake  = viewModel::clearImage,
                    onUse     = viewModel::recognizeCapturedImage,
                    onBack    = onBack,
                )
                permissionState == PermissionState.Granted -> LivePreview(
                    provider       = provider,
                    imageCapture   = imageCapture,
                    lifecycleOwner = lifecycleOwner,
                    executor       = executor,
                    isCapturing    = isCapturing,
                    onCapture      = { isCapturing = true },
                    onCaptured     = { bitmap ->
                        isCapturing = false
                        viewModel.onImageCaptured(bitmap)
                    },
                    onError        = { msg ->
                        isCapturing = false
                        error = msg
                    },
                    onBindError    = { msg -> error = msg },
                )
                else -> CircularProgressIndicator()
            }

            if (isCapturing || isRecognizing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Text(
                            text = if (isCapturing) "Capturing…" else "Reading text…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            if (visionFallbackState == VisionFallbackState.AwaitingConsent) {
                AlertDialog(
                    onDismissRequest = viewModel::onVisionConsentDeclined,
                    title = { Text("Enhanced image recognition") },
                    text = {
                        Text(
                            "This will send the captured photo over the internet to Google's " +
                                    "Cloud Vision service so it can try to read the title. " +
                                    "Only the image is sent; your imported viewing history stays on this device."
                        )
                    },
                    confirmButton = {
                        Button(onClick = viewModel::onVisionConsentGranted) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::onVisionConsentDeclined) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

private enum class PermissionState { Idle, Granted, Denied }

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

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

    val dimColor = Color.Black.copy(alpha = 0.55f)
    val frameWidth = 280.dp
    val frameHeight = 100.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(dimColor),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(frameHeight)
                        .background(dimColor),
                )
                Box(
                    modifier = Modifier
                        .size(frameWidth, frameHeight)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(
                                2.dp,
                                Color.White.copy(alpha = 0.9f),
                                RoundedCornerShape(12.dp),
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(frameHeight)
                        .background(dimColor),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(dimColor),
            )
        }

        Text(
            text = "Center the show or movie title in the frame",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (frameHeight / 2) + 16.dp)
                .padding(horizontal = 16.dp),
        )

        Button(
            onClick = {
                if (isCapturing) return@Button
                onCapture()
                captureImage(context, imageCapture, executor, onCaptured, onError)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .size(72.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Capture",
                modifier = Modifier.size(32.dp),
            )
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
        },
    )
}

@Composable
private fun ErrorState(error: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onBack) { Text("Go back") }
    }
}

@Composable
private fun PermissionDeniedState(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera permission required",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Camera access is needed to scan the TV screen. " +
                    "Please enable the permission in Settings.",
            style = MaterialTheme.typography.bodyLarge,
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Use this photo?",
            style = MaterialTheme.typography.headlineSmall,
        )
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured TV screen",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onRetake) { Text("Retake") }
            Button(onClick = onUse) { Text("Read text") }
        }
    }
}
