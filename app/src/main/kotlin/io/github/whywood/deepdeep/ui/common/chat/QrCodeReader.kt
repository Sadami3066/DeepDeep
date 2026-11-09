package io.github.whywood.deepdeep.ui.common.chat

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun readQrCodes(bitmap: Bitmap): List<String> = try {
    suspendCancellableCoroutine { continuation ->
    val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )
    scanner.process(InputImage.fromBitmap(bitmap, 0))
        .addOnSuccessListener { barcodes ->
            if (continuation.isActive) {
                continuation.resume(
                    barcodes.mapNotNull { barcode -> barcode.rawValue?.takeIf(String::isNotBlank) }.distinct(),
                )
            }
        }
        .addOnFailureListener {
            if (continuation.isActive) continuation.resume(emptyList())
        }
        .addOnCompleteListener {
            scanner.close()
        }
    }
} catch (t: Throwable) {
    emptyList()
}