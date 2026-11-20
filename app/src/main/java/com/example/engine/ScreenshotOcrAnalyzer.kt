package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.AppConfiguration
import com.example.data.MarketTab
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DetectedMarketTab(
    val tab: MarketTab,
    val centerX: Float,
    val centerY: Float,
    val rawText: String
)

data class ScannedLotCandidate(
    val rawText: String,
    val parsedPrice: Double,
    val matchesThreshold: Boolean
)

data class ScreenshotAnalysisResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val detectedTabs: List<DetectedMarketTab>,
    val activeTabDetected: MarketTab?,
    val showedSummary: String?,
    val lotCandidates: List<ScannedLotCandidate>,
    val rawLines: List<String>
)

object ScreenshotOcrAnalyzer {

    suspend fun analyzeUri(
        context: Context,
        uri: Uri,
        config: AppConfiguration
    ): Result<ScreenshotAnalysisResult> = withContext(Dispatchers.Default) {
        try {
            val bitmap = decodeBitmap(context, uri)
                ?: return@withContext Result.failure(IllegalStateException("Не удалось декодировать изображение"))

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val visionText = suspendCancellableCoroutine { continuation ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { result -> continuation.resume(result) }
                    .addOnFailureListener { ex -> continuation.resumeWithException(ex) }
            }

            val detectedTabs = mutableListOf<DetectedMarketTab>()
            val rawLines = mutableListOf<String>()
            val lotCandidates = mutableListOf<ScannedLotCandidate>()
            var showedSummary: String? = null

            val width = bitmap.width.toFloat().coerceAtLeast(1f)
            val height = bitmap.height.toFloat().coerceAtLeast(1f)

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val lineText = line.text.trim()
                    if (lineText.isBlank()) continue
                    rawLines.add(lineText)

                    val box = line.boundingBox
                    if (box != null) {
                        val yPercent = box.centerY() / height
                        val lower = lineText.lowercase()

                        // Market category strip sits roughly between 28% and 56% height in MoneyMining
                        if (yPercent in 0.25f..0.60f) {
                            when {
                                lower.contains("puzzle") -> {
                                    if (detectedTabs.none { it.tab == MarketTab.PUZZLES }) {
                                        detectedTabs.add(
                                            DetectedMarketTab(
                                                tab = MarketTab.PUZZLES,
                                                centerX = box.centerX().toFloat(),
                                                centerY = box.centerY().toFloat(),
                                                rawText = lineText
                                            )
                                        )
                                    }
                                }
                                lower.contains("land") -> {
                                    if (detectedTabs.none { it.tab == MarketTab.LANDS }) {
                                        detectedTabs.add(
                                            DetectedMarketTab(
                                                tab = MarketTab.LANDS,
                                                centerX = box.centerX().toFloat(),
                                                centerY = box.centerY().toFloat(),
                                                rawText = lineText
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (lower.contains("showed")) {
                            showedSummary = lineText
                        }

                        // Lot cards appear in the lower half below the tab strip
                        if (yPercent > 0.48f && !lower.contains("showed") && !lower.contains("mine") &&
                            !lower.contains("storage") && !lower.contains("tasks") && !lower.contains("market")
                        ) {
                            val parsedPrice = extractPrice(lineText)
                            if (parsedPrice != null && parsedPrice > 0.0) {
                                val matches = evaluateThreshold(parsedPrice, config)
                                lotCandidates.add(
                                    ScannedLotCandidate(
                                        rawText = lineText,
                                        parsedPrice = parsedPrice,
                                        matchesThreshold = matches
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Detect green active tab if possible by sampling pixel color above tab center
            val activeTab = detectActiveGreenTab(bitmap, detectedTabs)

            Result.success(
                ScreenshotAnalysisResult(
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    detectedTabs = detectedTabs,
                    activeTabDetected = activeTab,
                    showedSummary = showedSummary,
                    lotCandidates = lotCandidates,
                    rawLines = rawLines
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun detectActiveGreenTab(bitmap: Bitmap, tabs: List<DetectedMarketTab>): MarketTab? {
        for (tab in tabs) {
            val sx = tab.centerX.toInt().coerceIn(0, bitmap.width - 1)
            val sy = (tab.centerY - 24f).toInt().coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(sx, sy)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            if (g > r + 25 && g > b + 20) {
                return tab.tab
            }
        }
        return null
    }

    fun evaluateThreshold(price: Double, config: AppConfiguration): Boolean {
        if (!config.usePriceThreshold) return true
        return if (config.isLessThanOperator) {
            price <= (config.priceThreshold + 0.0001)
        } else {
            price >= (config.priceThreshold - 0.0001)
        }
    }

    fun extractPrice(text: String): Double? {
        return try {
            var cleaned = text.trim()
                .replace('O', '0')
                .replace('o', '0')
                .replace('D', '0')
                .replace('Q', '0')
                .replace('I', '1')
                .replace('l', '1')
                .replace('|', '1')
                .replace('S', '5')
                .replace('B', '8')
                .replace(':', '.')
                .replace(',', '.')
                .replace('\'', '.')

            if (cleaned.count { it == '.' } > 1) {
                val firstDot = cleaned.indexOf('.')
                cleaned = cleaned.substring(0, firstDot + 1) +
                    cleaned.substring(firstDot + 1).replace(".", "")
            }

            if (!cleaned.contains('.')) {
                val digitsOnly = cleaned.filter { it.isDigit() }
                if (digitsOnly.length >= 4) {
                    val insertPos = digitsOnly.length - 4
                    cleaned = digitsOnly.substring(0, insertPos) + "." + digitsOnly.substring(insertPos)
                }
            }

            val pattern = Pattern.compile("(\\d+(?:\\.\\d+)?)")
            val matcher = pattern.matcher(cleaned)
            if (matcher.find()) {
                matcher.group(1)?.toDoubleOrNull()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }
}
