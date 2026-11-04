package io.github.whywood.deepdeep.ui.chat

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

internal class CameraBarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build(),
    )
    private val busy = AtomicBoolean(false)
    private var lastScanAt = 0L

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val media = image.image
        if (busy.get() || media == null || now - lastScanAt < SCAN_INTERVAL_MS) {
            image.close()
            return
        }
        busy.set(true)
        lastScanAt = now
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes
                    .mapNotNull { barcode ->
                        val value = barcode.rawValue?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val box = barcode.boundingBox
                        value to (if (box == null) 0L else box.width().toLong() * box.height())
                    }
                    .maxByOrNull { it.second }
                    ?.first
                    ?.let(onBarcode)
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }

    fun close() {
        scanner.close()
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 250L
    }
}
