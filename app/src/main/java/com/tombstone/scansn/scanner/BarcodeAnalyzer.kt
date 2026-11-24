package com.tombstone.scansn.scanner

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import com.tombstone.scansn.ui.ScanTarget
import java.util.concurrent.atomic.AtomicBoolean

class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val target: ScanTarget,
    private val scanRegionProvider: () -> ViewScanRegion?,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val delivered = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    // Require temporal agreement for every field to reduce one-frame false positives.
    private val requiredMatches = if (target == ScanTarget.IMEI) 3 else 2
    private var pendingValue: String? = null
    private var matchCount = 0
    private var missedFrames = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || delivered.get() || !processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val imageRegion = scanRegionProvider()?.let { region ->
            ScanGeometry.mapViewRegionToImage(region, imageProxy.width, imageProxy.height, rotation)
        }
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val candidates = barcodes.mapNotNull { barcode ->
                    barcode.rawValue?.let { rawValue ->
                        BarcodeCandidate(
                            rawValue = rawValue,
                            bounds = barcode.boundingBox?.let { box ->
                                ScanBounds(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
                            },
                        )
                    }
                }
                val value = BarcodeSelector.select(candidates, imageRegion, target)
                if (isConfirmed(value) && value != null && delivered.compareAndSet(false, true)) {
                    mainHandler.post { onCode(value) }
                }
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    private fun isConfirmed(value: String?): Boolean {
        if (value == null) {
            missedFrames += 1
            if (missedFrames > 2) {
                pendingValue = null
                matchCount = 0
            }
            return false
        }
        missedFrames = 0
        if (value == pendingValue) {
            matchCount += 1
        } else {
            pendingValue = value
            matchCount = 1
        }
        return matchCount >= requiredMatches
    }
}
