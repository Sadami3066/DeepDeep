package io.github.whywood.deepdeep.ui.common.chat

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun normalizeQrCodes(rawValues: List<String?>): List<String> =
    rawValues.mapNotNull { value -> value?.takeIf(String::isNotBlank) }.distinct()

internal suspend fun readQrCodes(bitmap: Bitmap): List<String> =
    suspendCancellableCoroutine { continuation ->
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes ->
                if (continuation.isActive) {
                    continuation.resume(normalizeQrCodes(barcodes.map { barcode -> barcode.rawValue }))
                }
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            .addOnCompleteListener {
                scanner.close()
            }
    }
