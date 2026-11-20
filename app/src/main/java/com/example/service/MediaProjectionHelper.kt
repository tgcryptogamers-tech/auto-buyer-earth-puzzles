package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.example.engine.BotLogs

object MediaProjectionHelper {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var latestCachedBitmap: Bitmap? = null
    private var lastFrameTimestampMs: Long = 0L
    private val bitmapLock = Any()

    var width = 720
        private set
    var height = 1280
        private set
    private var dpi = 240

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w("MediaProjectionHelper", "MediaProjection stopped by system")
            BotLogs.addLog("⚠️ [СКРИНШОТ] Разрешение захвата экрана (MediaProjection) остановлено системой!")
            release()
        }
    }

    fun initProjection(projection: MediaProjection, context: Context) {
        release()

        mediaProjection = projection
        try {
            mediaProjection?.registerCallback(projectionCallback, null)
        } catch (e: Exception) {
            Log.e("MediaProjectionHelper", "Error registering callback: ${e.message}")
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        width = metrics.widthPixels
        height = metrics.heightPixels
        dpi = metrics.densityDpi

        val maxDimension = 1080
        val currentMax = maxOf(width, height)
        if (currentMax > maxDimension) {
            val scale = maxDimension.toFloat() / currentMax
            width = (width * scale).toInt()
            height = (height * scale).toInt()
        }

        if (width % 2 != 0) width--
        if (height % 2 != 0) height--

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 4)

        val displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "EarthPuzzlesCapture",
            width,
            height,
            dpi,
            displayFlags,
            imageReader?.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            processAvailableFrame(reader)
        }, null)
    }

    private fun processAvailableFrame(reader: ImageReader): Boolean {
        var image: android.media.Image? = null
        try {
            image = reader.acquireLatestImage() ?: return false
            val planes = image.planes
            if (planes.isEmpty()) return false

            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmapWidth = width + rowPadding / pixelStride

            val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = if (rowPadding == 0) {
                bitmap
            } else {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (cropped != bitmap) {
                    bitmap.recycle()
                }
                cropped
            }

            synchronized(bitmapLock) {
                latestCachedBitmap?.recycle()
                latestCachedBitmap = croppedBitmap
                lastFrameTimestampMs = System.currentTimeMillis()
            }
            return true
        } catch (e: Exception) {
            Log.e("MediaProjectionHelper", "Error processing frame: ${e.message}", e)
            return false
        } finally {
            image?.close()
        }
    }

    /**
     * Waits up to [waitTimeoutMs] for a frame captured at or after [minTimestampMs].
     * Falls back to the latest cached frame if the screen remained static without emitting a new frame.
     */
    @SuppressLint("WrongConstant")
    fun captureFreshScreenshot(minTimestampMs: Long, waitTimeoutMs: Long = 500L): Bitmap? {
        val reader = imageReader
        if (reader == null) {
            BotLogs.addLog("⚠️ [СКРИНШОТ] Захват экрана не инициализирован. Разрешите Screen Capture в приложении.")
            return null
        }

        val deadline = System.currentTimeMillis() + waitTimeoutMs.coerceAtLeast(60L)
        while (true) {
            processAvailableFrame(reader)
            synchronized(bitmapLock) {
                val cached = latestCachedBitmap
                if (cached != null && !cached.isRecycled && lastFrameTimestampMs >= minTimestampMs) {
                    return cached.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                break
            }
            try {
                Thread.sleep(35)
            } catch (_: InterruptedException) {
                break
            }
        }

        synchronized(bitmapLock) {
            latestCachedBitmap?.let { cached ->
                if (!cached.isRecycled) {
                    return cached.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        }
        return null
    }

    @SuppressLint("WrongConstant")
    fun getLatestScreenshot(): Bitmap? {
        imageReader?.let { processAvailableFrame(it) }

        synchronized(bitmapLock) {
            latestCachedBitmap?.let { cached ->
                if (!cached.isRecycled) {
                    return cached.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        }

        val reader = imageReader
        if (reader == null) {
            BotLogs.addLog("⚠️ [СКРИНШОТ] Захват экрана не инициализирован. Разрешите Screen Capture в приложении.")
            return null
        }

        for (attempt in 1..12) {
            if (processAvailableFrame(reader)) {
                synchronized(bitmapLock) {
                    latestCachedBitmap?.let { cached ->
                        if (!cached.isRecycled) {
                            return cached.copy(Bitmap.Config.ARGB_8888, false)
                        }
                    }
                }
            }
            try {
                Thread.sleep(60)
            } catch (_: InterruptedException) {
                break
            }
        }

        BotLogs.addLog("⚠️ [СКРИНШОТ] Не удалось получить снимок экрана за 12 попыток.")
        return null
    }

    fun release() {
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {
        }
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null

        synchronized(bitmapLock) {
            latestCachedBitmap?.recycle()
            latestCachedBitmap = null
            lastFrameTimestampMs = 0L
        }
    }

    fun hasProjection(): Boolean = mediaProjection != null
}
