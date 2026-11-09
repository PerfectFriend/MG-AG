package com.example.ui.components

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Real CameraX & ML Kit QR Code Scanner for SimpleX Chat invitations and links.
 * Provides live camera stream, targeting reticle, flashlight control, and clipboard paste fallback.
 */
@Composable
fun QrCodeScannerView(
    onQrScanned: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraControl: Camera? by remember { mutableStateOf(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var lastScannedValue by remember { mutableStateOf("") }
    var debounceActive by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.background(Color(0xFF0A0E13)),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            @androidx.camera.core.ExperimentalGetImage
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                val scanner = BarcodeScanning.getClient()
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            val rawValue = barcode.rawValue ?: continue
                                            if (rawValue.isNotBlank() && rawValue != lastScannedValue && !debounceActive) {
                                                lastScannedValue = rawValue
                                                debounceActive = true
                                                onQrScanned(rawValue)
                                                previewView.postDelayed({ debounceActive = false }, 2000)
                                            }
                                        }
                                    }
                                    .addOnFailureListener { err ->
                                        Log.e("QrCodeScannerView", "ML Kit scan failed", err)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            cameraControl = cam
                        } catch (exc: Exception) {
                            Log.e("QrCodeScannerView", "Use case binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Reticle overlay
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(2.dp, Color(0xFF00FF66).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            ) {
                // Laser beam animation inside reticle
                val inf = rememberInfiniteTransition(label = "laser")
                val lineProgress by inf.animateFloat(
                    initialValue = 0.05f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "laserLine"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * lineProgress
                    drawLine(
                        color = Color(0xFF00FF66),
                        start = Offset(12.dp.toPx(), y),
                        end = Offset(size.width - 12.dp.toPx(), y),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }

            // Top bar controls (Dismiss + Torch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                // Flashlight toggle
                Button(
                    onClick = {
                        isFlashOn = !isFlashOn
                        cameraControl?.cameraControl?.enableTorch(isFlashOn)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFlashOn) Color(0xFFFFD700) else Color.Black.copy(alpha = 0.6f),
                        contentColor = if (isFlashOn) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (isFlashOn) "🔦 ВЫКЛ" else "🔦 СВЕТ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Bottom paste / guidance bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Наведите камеру на QR-код контакта или приглашения SimpleX",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                // Paste from clipboard button
                OutlinedButton(
                    onClick = {
                        val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipMgr.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                        if (!clip.isNullOrBlank()) {
                            onQrScanned(clip)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF66)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text("📋 Вставить ссылку из буфера", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    cameraExecutor.shutdown()
                    cameraControl?.cameraControl?.enableTorch(false)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "📷 Требуется доступ к камере",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Для мгновенного сканирования QR-кодов SimpleX предоставьте разрешение приложению.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF66), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Разрешить камеру", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
