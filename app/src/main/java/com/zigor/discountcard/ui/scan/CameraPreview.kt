package com.zigor.discountcard.ui.scan

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.zigor.discountcard.util.mlKitFormatName
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Превью камеры с непрерывным распознаванием штрих-кодов (офлайн, модель внутри APK). */
@Composable
fun BarcodeCameraPreview(
    torchEnabled: Boolean,
    onCode: (value: String, format: String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnError by rememberUpdatedState(onError)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val scanner: BarcodeScanner = remember { BarcodeScanning.getClient() }
    val cameraState = remember { mutableStateOf<Camera?>(null) }
    val providerState = remember { mutableStateOf<ProcessCameraProvider?>(null) }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                providerState.value = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, BarcodeAnalyzer(scanner) { value, format ->
                    currentOnCode(value, format)
                })

                provider.unbindAll()
                cameraState.value = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure {
                Log.e("BarcodeCamera", "Камера недоступна", it)
                currentOnError(it.message ?: it::class.java.simpleName)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerState.value?.unbindAll() }
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    LaunchedEffect(torchEnabled, cameraState.value) {
        runCatching {
            val camera = cameraState.value ?: return@runCatching
            if (camera.cameraInfo.hasFlashUnit()) camera.cameraControl.enableTorch(torchEnabled)
        }
    }
}

@ExperimentalGetImage
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onCode: (String, String) -> Unit,
) : ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val best = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (best != null) {
                    onCode(best.rawValue!!.trim(), mlKitFormatName(best.format))
                }
            }
            .addOnFailureListener { Log.w("BarcodeAnalyzer", "Ошибка распознавания", it) }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/** Все форматы, которые умеет читать сканер. */
val SUPPORTED_SCAN_FORMATS = listOf(
    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93, Barcode.FORMAT_ITF,
    Barcode.FORMAT_CODABAR, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
    Barcode.FORMAT_AZTEC, Barcode.FORMAT_PDF417,
)
