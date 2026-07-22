package com.pulsefusionnet.app.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.pulsefusionnet.app.ppg.FrameStats
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Opens the rear camera (no torch/flash — ambient light through the fingertip, same as the
 * web app) and streams downsampled RGB frame statistics for finger detection + PPG extraction.
 *
 * No preview surface is bound: the sensor is only fed to ImageAnalysis at a small target
 * resolution, since all we need is the average R/G/B and the spatial std-dev of red.
 */
class CameraController(private val onFrame: (FrameStats) -> Unit) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var previewViewRef: PreviewView? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    // Sample every Nth pixel to bound per-frame CPU cost — a coarse grid is all the
    // PPG signal needs (the web app downsamples to an 80x60 canvas for the same reason).
    private val sampleStride = 6

    fun start(lifecycleOwner: LifecycleOwner, providerFuture: ProcessCameraProvider) {
        cameraProvider = providerFuture
        currentLifecycleOwner = lifecycleOwner
        bindCamera()
    }

    fun attachPreview(previewView: PreviewView) {
        previewViewRef = previewView
        bindCamera()
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        currentLifecycleOwner = null
        previewViewRef = null
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val owner = currentLifecycleOwner ?: return
        provider.unbindAll()

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(320, 240))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { image -> processFrame(image) }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val pView = previewViewRef

        if (pView != null) {
            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider(pView.surfaceProvider)
            provider.bindToLifecycle(owner, selector, preview, analysis)
        } else {
            provider.bindToLifecycle(owner, selector, analysis)
        }
    }

    private fun processFrame(image: ImageProxy) {
        try {
            val stats = extractYuvStats(image)
            onFrame(stats)
        } catch (e: Exception) {
            // Drop malformed frames silently — the next frame arrives in ~33ms.
        } finally {
            image.close()
        }
    }

    /** Converts a strided grid of YUV_420_888 pixels to RGB and accumulates channel stats. */
    private fun extractYuvStats(image: ImageProxy): FrameStats {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumR2 = 0.0
        var count = 0

        var row = 0
        while (row < height) {
            var col = 0
            while (col < width) {
                val yIndex = row * yRowStride + col
                val uvRow = row / 2
                val uvCol = col / 2
                val uIndex = uvRow * uRowStride + uvCol * uPixelStride
                val vIndex = uvRow * vRowStride + uvCol * vPixelStride

                if (yIndex < yBuffer.capacity() && uIndex < uBuffer.capacity() && vIndex < vBuffer.capacity()) {
                    val yVal = (yBuffer.get(yIndex).toInt() and 0xFF)
                    val uVal = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                    val vVal = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                    val r = (yVal + 1.402 * vVal).coerceIn(0.0, 255.0)
                    val g = (yVal - 0.344136 * uVal - 0.714136 * vVal).coerceIn(0.0, 255.0)
                    val b = (yVal + 1.772 * uVal).coerceIn(0.0, 255.0)

                    sumR += r
                    sumG += g
                    sumB += b
                    sumR2 += r * r
                    count++
                }
                col += sampleStride
            }
            row += sampleStride
        }

        val n = max(count, 1)
        val avgR = sumR / n
        val avgG = sumG / n
        val avgB = sumB / n
        val spatialStdR = sqrt(max(0.0, sumR2 / n - avgR * avgR))

        return FrameStats(avgR, avgG, avgB, spatialStdR)
    }
}

fun getCameraProvider(context: android.content.Context, onReady: (ProcessCameraProvider) -> Unit) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({ onReady(future.get()) }, ContextCompat.getMainExecutor(context))
}
