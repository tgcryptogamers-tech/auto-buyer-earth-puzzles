package com.example.service

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.engine.BotLogs
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class LotCardLocation(
    val handshakeCenterX: Float,
    val handshakeCenterY: Float,
    val cardRect: Rect,
    val priceCropRect: Rect
)

object OpenCvVisionEngine {
    private var isInitialized = false

    fun init(): Boolean {
        if (isInitialized) return true
        return try {
            isInitialized = OpenCVLoader.initDebug()
            if (isInitialized) {
                BotLogs.addLog("👁️ [OpenCV] Движок компьютерного зрения успешно инициализирован.")
            } else {
                BotLogs.addLog("⚠️ [OpenCV] Не удалось инициализировать нативный OpenCV Loader.")
            }
            isInitialized
        } catch (e: Exception) {
            BotLogs.addLog("⚠️ [OpenCV] Ошибка инициализации OpenCV: ${e.message}")
            isInitialized = false
            false
        }
    }

    fun findLotCards(
        fullBitmap: Bitmap,
        screenWidth: Float,
        screenHeight: Float
    ): List<LotCardLocation> {
        if (!isInitialized) init()
        val cards = mutableListOf<LotCardLocation>()

        val scaleX = screenWidth / fullBitmap.width.toFloat()
        val scaleY = screenHeight / fullBitmap.height.toFloat()

        val cropTop = (fullBitmap.height * 0.46f).toInt().coerceIn(0, fullBitmap.height - 1)
        val cropBottom = (fullBitmap.height * 0.89f).toInt().coerceIn(cropTop + 1, fullBitmap.height)
        val cropHeight = cropBottom - cropTop
        val cropWidth = fullBitmap.width
        if (cropHeight <= 20 || cropWidth <= 20) return cards

        val croppedBitmap = try {
            Bitmap.createBitmap(fullBitmap, 0, cropTop, cropWidth, cropHeight)
        } catch (_: Exception) {
            return cards
        }

        val rgbaMat = Mat()
        val rgbMat = Mat()
        val hsvMat = Mat()
        val yellowMask = Mat()

        try {
            Utils.bitmapToMat(croppedBitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)

            Core.inRange(hsvMat, Scalar(16.0, 100.0, 120.0), Scalar(36.0, 255.0, 255.0), yellowMask)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(yellowMask, yellowMask, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(yellowMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()

            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                val area = Imgproc.contourArea(contour)
                val centerXPercent = (rect.x + rect.width / 2f) / cropWidth.toFloat()

                if (centerXPercent in 0.35f..0.65f && rect.width >= 35 && rect.height >= 25 && area >= 120.0) {
                    val globalHandshakeX = (rect.x + rect.width / 2f) * scaleX
                    val globalHandshakeY = (cropTop + rect.y + rect.height / 2f) * scaleY

                    val cardLeft = ((rect.x + rect.width / 2f) - cropWidth * 0.46f).toInt().coerceAtLeast(0)
                    val cardRight = ((rect.x + rect.width / 2f) + cropWidth * 0.46f).toInt().coerceAtMost(cropWidth)
                    val cardTop = (cropTop + rect.y - rect.height * 0.9f).toInt().coerceAtLeast(0)
                    val cardBottom = (cropTop + rect.y + rect.height * 1.9f).toInt().coerceAtMost(fullBitmap.height)

                    val priceLeft = (rect.x + rect.width + 10).coerceAtMost(cropWidth - 10)
                    val priceRight = (cardRight - 10).coerceAtLeast(priceLeft + 10).coerceAtMost(cropWidth)
                    val priceTop = (cropTop + rect.y - 10).coerceAtLeast(0)
                    val priceBottom = (cardBottom - 5).coerceAtLeast(priceTop + 10).coerceAtMost(fullBitmap.height)

                    cards.add(
                        LotCardLocation(
                            handshakeCenterX = globalHandshakeX,
                            handshakeCenterY = globalHandshakeY,
                            cardRect = Rect(cardLeft, cardTop, cardRight, cardBottom),
                            priceCropRect = Rect(priceLeft, priceTop, priceRight, priceBottom)
                        )
                    )
                }
                contour.release()
            }
        } catch (e: Exception) {
            BotLogs.addLog("⚠️ [OpenCV] Ошибка анализа лот-карт: ${e.message}")
        } finally {
            rgbaMat.release()
            rgbMat.release()
            hsvMat.release()
            yellowMask.release()
            croppedBitmap.recycle()
        }

        return cards
    }

    fun findConfirmationBuyButton(
        fullBitmap: Bitmap,
        screenWidth: Float,
        screenHeight: Float
    ): Pair<Float, Float>? {
        if (!isInitialized) init()

        val scaleX = screenWidth / fullBitmap.width.toFloat()
        val scaleY = screenHeight / fullBitmap.height.toFloat()

        val cropTop = (fullBitmap.height * 0.54f).toInt().coerceIn(0, fullBitmap.height - 1)
        val cropBottom = (fullBitmap.height * 0.89f).toInt().coerceIn(cropTop + 1, fullBitmap.height)
        val cropLeft = (fullBitmap.width * 0.16f).toInt().coerceIn(0, fullBitmap.width - 1)
        val cropRight = (fullBitmap.width * 0.84f).toInt().coerceIn(cropLeft + 1, fullBitmap.width)
        val cropWidth = cropRight - cropLeft
        val cropHeight = cropBottom - cropTop
        if (cropWidth <= 20 || cropHeight <= 20) return null

        val croppedBitmap = try {
            Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
        } catch (_: Exception) {
            return null
        }

        val rgbaMat = Mat()
        val rgbMat = Mat()
        val hsvMat = Mat()
        val yellowMask = Mat()
        val greenMask = Mat()

        try {
            Utils.bitmapToMat(croppedBitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)

            // 1. Check for emerald-green MoneyMining action button first (wider, button-shaped rectangle)
            Core.inRange(hsvMat, Scalar(38.0, 60.0, 55.0), Scalar(92.0, 255.0, 255.0), greenMask)
            val greenKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(12.0, 6.0))
            Imgproc.morphologyEx(greenMask, greenMask, Imgproc.MORPH_CLOSE, greenKernel)
            greenKernel.release()

            val greenContours = mutableListOf<MatOfPoint>()
            val greenHierarchy = Mat()
            Imgproc.findContours(greenMask, greenContours, greenHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            greenHierarchy.release()

            var bestGreen: org.opencv.core.Rect? = null
            var maxGreenArea = 0.0
            for (contour in greenContours) {
                val rect = Imgproc.boundingRect(contour)
                val area = Imgproc.contourArea(contour)
                val centerXPercent = (cropLeft + rect.x + rect.width / 2f) / fullBitmap.width.toFloat()
                if (centerXPercent in 0.34f..0.66f &&
                    rect.width >= (cropWidth * 0.20f).toInt() &&
                    rect.height in 20..(fullBitmap.height * 0.16f).toInt() &&
                    area >= 450.0
                ) {
                    if (area > maxGreenArea) {
                        maxGreenArea = area
                        bestGreen = rect
                    }
                }
                contour.release()
            }

            if (bestGreen != null) {
                val btnCenterX = (cropLeft + bestGreen.x + bestGreen.width / 2f) * scaleX
                val btnCenterY = (cropTop + bestGreen.y + bestGreen.height / 2f) * scaleY
                return Pair(btnCenterX, btnCenterY)
            }

            // 2. Fallback to golden/yellow button border or merged yellow button text
            Core.inRange(hsvMat, Scalar(15.0, 75.0, 100.0), Scalar(38.0, 255.0, 255.0), yellowMask)
            val yellowKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(16.0, 5.0))
            Imgproc.morphologyEx(yellowMask, yellowMask, Imgproc.MORPH_CLOSE, yellowKernel)
            yellowKernel.release()

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(yellowMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            hierarchy.release()

            var bestCandidate: org.opencv.core.Rect? = null
            var maxScore = 0.0

            for (contour in contours) {
                val rect = Imgproc.boundingRect(contour)
                val area = Imgproc.contourArea(contour)
                val centerXPercent = (cropLeft + rect.x + rect.width / 2f) / fullBitmap.width.toFloat()
                val centerYPercent = (cropTop + rect.y + rect.height / 2f) / fullBitmap.height.toFloat()

                // Prefer wide button-like contours in the lower modal area (avoid top coin icons)
                if (centerXPercent in 0.35f..0.65f &&
                    centerYPercent in 0.66f..0.86f &&
                    rect.width >= 40 &&
                    area >= 100.0
                ) {
                    val centerBias = 1.0 - kotlin.math.abs(centerXPercent - 0.50f)
                    val score = (area + rect.width * 3.0) * centerBias
                    if (score > maxScore) {
                        maxScore = score
                        bestCandidate = rect
                    }
                }
                contour.release()
            }

            if (bestCandidate != null) {
                val btnCenterX = (cropLeft + bestCandidate.x + bestCandidate.width / 2f) * scaleX
                val btnCenterY = (cropTop + bestCandidate.y + bestCandidate.height / 2f) * scaleY
                return Pair(btnCenterX, btnCenterY)
            }
        } catch (_: Exception) {
        } finally {
            rgbaMat.release()
            rgbMat.release()
            hsvMat.release()
            yellowMask.release()
            greenMask.release()
            croppedBitmap.recycle()
        }
        return null
    }
}
