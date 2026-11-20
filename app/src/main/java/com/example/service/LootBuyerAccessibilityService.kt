package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.example.data.AppConfiguration
import com.example.data.AppDatabase
import com.example.data.MarketTab
import com.example.data.PurchaseRecord
import com.example.engine.BotLogs
import com.example.engine.ScreenshotOcrAnalyzer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class LootBuyerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var automationJob: Job? = null

    @Volatile private var isRunning = false
    @Volatile private var cooldownUntilMillis: Long = 0L

    @Volatile private var cachedTabPuzzlesX: Float? = null
    @Volatile private var cachedTabPuzzlesY: Float? = null
    @Volatile private var cachedTabLandsX: Float? = null
    @Volatile private var cachedTabLandsY: Float? = null
    @Volatile private var cachedBuyX: Float? = null
    @Volatile private var cachedBuyY: Float? = null

    @Volatile private var lastPurchasedPrice: Double? = null
    @Volatile private var lastPurchasedQuantity: Double? = null
    @Volatile private var lastPurchaseAttemptTime: Long = 0L
    @Volatile private var isStuckCheckPending: Boolean = false

    @Volatile private var currentCycleNumber = 1
    @Volatile private var cycleEndTimeMs = 0L
    @Volatile private var purchasesThisCycleRun = 0
    @Volatile private var cycleInitialized = false
    @Volatile private var lastHeartbeatLogTime = 0L

    companion object {
        const val CONFIRMATION_INITIAL_DELAY_MS = 220L

        @Volatile
        var instance: LootBuyerAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null

        fun getCooldownRemainingMs(): Long {
            val inst = instance ?: return 0L
            val remaining = inst.cooldownUntilMillis - System.currentTimeMillis()
            return if (remaining > 0L) remaining else 0L
        }

        fun resetCooldown() {
            instance?.let {
                it.cooldownUntilMillis = 0L
                BotLogs.addLog("🔄 [СБРОС] Кулдаун сброшен вручную. Бот готов к покупкам!")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        OpenCvVisionEngine.init()
        BotLogs.addLog("✅ Accessibility Service подключен и готов к работе с игрой.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stopAutomation()
        BotLogs.addLog("ℹ️ Accessibility Service отключен.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        stopAutomation()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Automation loop runs continuously in startAutomation().
    }

    override fun onInterrupt() {
        // Do not cancel background automation on accessibility interruption.
    }

    fun startAutomation() {
        if (isRunning) return
        isRunning = true
        cachedTabPuzzlesX = null
        cachedTabPuzzlesY = null
        cachedTabLandsX = null
        cachedTabLandsY = null
        cachedBuyX = null
        cachedBuyY = null
        cycleInitialized = false
        currentCycleNumber = 1
        purchasesThisCycleRun = 0

        BotLogs.addLog("🚀 Запуск цикла бота для вкладок Puzzles и Lands...")

        automationJob = serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val initialCfg = db.configurationDao().getConfiguration()
                if (initialCfg != null) {
                    val t = MarketTab.fromString(initialCfg.buyTarget)
                    val modeText = if (initialCfg.enableActualBuying) "АВТОПОКУПКА ВКЛ" else "ТОЛЬКО МОНИТОРИНГ"
                    BotLogs.addLog("🎯 Цель: только ${t.tabLabel} | Порог: ${initialCfg.priceThreshold} | $modeText")
                }
                while (isRunning) {
                    val now = System.currentTimeMillis()
                    if (now < cooldownUntilMillis) {
                        val remainingSec = (cooldownUntilMillis - now) / 1000
                        BotLogs.addLog("⏳ Пауза после покупки. Осталось: $remainingSec сек.")
                        delay(5000)
                        continue
                    }

                    val config = db.configurationDao().getConfiguration()
                    if (config == null || !config.autoBuyEnabled) {
                        delay(800)
                        continue
                    }

                    if (config.useSearchCycles) {
                        handleSearchCycles(config)
                    } else {
                        cycleInitialized = false
                    }

                    performOcrScreenScan(config, db)
                    delay(config.scanIntervalMs.coerceAtLeast(150L))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                BotLogs.addLog("❌ Ошибка в цикле автоматизации: ${e.message}")
            }
        }
    }

    fun stopAutomation() {
        isRunning = false
        automationJob?.cancel()
        automationJob = null
        BotLogs.addLog("⏹ Автоматизация остановлена.")
    }

    private suspend fun handleSearchCycles(config: AppConfiguration) {
        if (!cycleInitialized) {
            cycleInitialized = true
            if (currentCycleNumber == 1) purchasesThisCycleRun = 0
            val (mins, randSec) = when (currentCycleNumber) {
                1 -> config.cycle1DurationMin to config.cycle1RandomRangeSec
                2 -> config.cycle2DurationMin to config.cycle2RandomRangeSec
                else -> config.cycle3DurationMin to config.cycle3RandomRangeSec
            }
            val limit = abs(randSec)
            val delta = if (limit > 0) Random.nextInt(-limit, limit + 1) else 0
            val totalSec = (mins.coerceAtLeast(1) * 60 + delta).coerceAtLeast(15)
            cycleEndTimeMs = System.currentTimeMillis() + totalSec * 1000L
            BotLogs.addLog("🕒 [ЦИКЛ $currentCycleNumber] Запущен на ${totalSec} сек (база ${mins}м ±${limit}с).")
        } else if (System.currentTimeMillis() >= cycleEndTimeMs) {
            if (purchasesThisCycleRun == 0) {
                BotLogs.addLog("🔄 [ЦИКЛ $currentCycleNumber] Покупок не было — перезагружаем страницу мини-приложения...")
                restartGameByReloadPage()
                delay(2500L)
            }
            currentCycleNumber = if (currentCycleNumber >= 3) 1 else currentCycleNumber + 1
            cycleInitialized = false
        }
    }

    private fun getTabCoordinates(tab: MarketTab, config: AppConfiguration): Pair<Float, Float>? {
        return when (tab) {
            MarketTab.PUZZLES -> when {
                config.calibratedPuzzlesX != -1f && config.calibratedPuzzlesY != -1f ->
                    config.calibratedPuzzlesX to config.calibratedPuzzlesY
                cachedTabPuzzlesX != null && cachedTabPuzzlesY != null ->
                    cachedTabPuzzlesX!! to cachedTabPuzzlesY!!
                else -> null
            }
            MarketTab.LANDS -> when {
                config.calibratedLandsX != -1f && config.calibratedLandsY != -1f ->
                    config.calibratedLandsX to config.calibratedLandsY
                cachedTabLandsX != null && cachedTabLandsY != null ->
                    cachedTabLandsX!! to cachedTabLandsY!!
                else -> null
            }
        }
    }

    private fun scanTabsFromOcrResults(
        textBlocks: List<Text.TextBlock>,
        scaleX: Float,
        scaleY: Float,
        screenHeight: Float
    ) {
        fun checkAndAssign(rawText: String, bounds: Rect?) {
            if (bounds == null) return
            val text = rawText.lowercase().trim()
            val tX = bounds.centerX() * scaleX
            val tY = bounds.centerY() * scaleY
            val tYPercent = tY / screenHeight
            if (tYPercent !in 0.28f..0.56f) return

            if (text.contains("puzzle") || text.contains("пазл")) {
                cachedTabPuzzlesX = tX
                cachedTabPuzzlesY = tY
            } else if (text.contains("land") || text.contains("земл")) {
                cachedTabLandsX = tX
                cachedTabLandsY = tY
            }
        }

        for (block in textBlocks) {
            for (line in block.lines) {
                checkAndAssign(line.text, line.boundingBox)
                for (element in line.elements) {
                    checkAndAssign(element.text, element.boundingBox)
                }
            }
        }
    }

    private suspend fun performOcrScreenScan(config: AppConfiguration, db: AppDatabase) = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()
        val density = metrics.density

        val targetTab = MarketTab.fromString(config.buyTarget)
        val alternateTab = if (targetTab == MarketTab.PUZZLES) MarketTab.LANDS else MarketTab.PUZZLES

        // Step 0: Check BEFORE switching tabs if a modal dialog (confirmation / error / rate-limit / mail) is already open!
        val preBitmap = MediaProjectionHelper.getLatestScreenshot()
        if (preBitmap == null) {
            BotLogs.addLog("🔍 [ОЖИДАНИЕ] Включите захват экрана (Screen Capture) и откройте рынок в игре.")
            return@withContext
        }

        try {
            val preScaleX = screenWidth / preBitmap.width
            val preScaleY = screenHeight / preBitmap.height
            val preResult = Tasks.await(recognizer.process(InputImage.fromBitmap(preBitmap, 0)))
            scanTabsFromOcrResults(preResult.textBlocks, preScaleX, preScaleY, screenHeight)

            val preLines = preResult.textBlocks.flatMap { it.lines }.filter { line ->
                !isInsideOverlay(line.boundingBox, screenWidth, screenHeight, density, preScaleX, preScaleY) &&
                    !isLogOrOverlayText(line.text)
            }

            if (handleTryThroughDialogIfNeeded(preLines, preScaleX, preScaleY, screenWidth, screenHeight, config)) {
                return@withContext
            }
            if (dismissSystemDialogIfNeeded(preLines, preScaleX, preScaleY, screenWidth, screenHeight, config)) {
                return@withContext
            }
            if (config.enableActualBuying && isConfirmationDialogShowing(preLines, preScaleX, preScaleY, screenWidth, screenHeight)) {
                confirmOpenPurchaseDialog(
                    preLines, preScaleX, preScaleY, screenWidth, screenHeight,
                    config, db, preBitmap, targetTab, recognizer
                )
                return@withContext
            }
        } catch (e: Exception) {
            BotLogs.addLog("⚠️ Ошибка первичного OCR экрана: ${e.message}")
        } finally {
            preBitmap.recycle()
        }

        val targetCoords = getTabCoordinates(targetTab, config)
        val altCoords = getTabCoordinates(alternateTab, config)

        if (targetCoords == null) {
            BotLogs.addLog("⚠️ Вкладка '${targetTab.tabLabel}' не найдена на экране. Включите калибровку в настройках и укажите вкладки вручную.")
            return@withContext
        }

        // Step 2: Switch between Puzzles and Lands in the game to force market refresh
        val switchStartTime = System.currentTimeMillis()
        if (config.switchBetweenTabs && altCoords != null) {
            val rand1 = if (config.tabSwitchRandomizationMs > 0) {
                Random.nextInt(-config.tabSwitchRandomizationMs, config.tabSwitchRandomizationMs + 1)
            } else 0
            val rand2 = if (config.tabSwitchRandomizationMs > 0) {
                Random.nextInt(-config.tabSwitchRandomizationMs, config.tabSwitchRandomizationMs + 1)
            } else 0

            val delay1 = (config.tabSwitchIntervalMs + rand1).coerceAtLeast(30L)
            val delay2 = (config.tabSwitchIntervalMs + rand2).coerceAtLeast(30L)

            if (config.verboseOcrLogging) {
                BotLogs.addLog("🔀 Переключаемся в игре: ${alternateTab.tabLabel} -> ${targetTab.tabLabel}")
            }
            clickAtWithRandomization(altCoords.first, altCoords.second, config)
            delay(delay1)
            clickAtWithRandomization(targetCoords.first, targetCoords.second, config)
            delay(delay2)
        } else {
            clickAtWithRandomization(targetCoords.first, targetCoords.second, config)
            delay(config.tabSwitchIntervalMs.coerceAtLeast(50L))
        }

        // Step 3: Capture fresh screenshot on the target tab (ONLY Puzzles or ONLY Lands)
        val freshBitmap = MediaProjectionHelper.captureFreshScreenshot(
            minTimestampMs = switchStartTime + 30L,
            waitTimeoutMs = 280L
        ) ?: return@withContext

        try {
            val scaleX = screenWidth / freshBitmap.width
            val scaleY = screenHeight / freshBitmap.height
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(freshBitmap, 0)))

            val allLines = result.textBlocks.flatMap { it.lines }
            val filteredLines = allLines.filter { line ->
                !isInsideOverlay(line.boundingBox, screenWidth, screenHeight, density, scaleX, scaleY) &&
                    !isLogOrOverlayText(line.text)
            }

            if (handleTryThroughDialogIfNeeded(filteredLines, scaleX, scaleY, screenWidth, screenHeight, config)) {
                return@withContext
            }

            if (dismissSystemDialogIfNeeded(filteredLines, scaleX, scaleY, screenWidth, screenHeight, config)) {
                return@withContext
            }

            if (config.enableActualBuying && isConfirmationDialogShowing(filteredLines, scaleX, scaleY, screenWidth, screenHeight)) {
                confirmOpenPurchaseDialog(
                    filteredLines, scaleX, scaleY, screenWidth, screenHeight,
                    config, db, freshBitmap, targetTab, recognizer
                )
                return@withContext
            }

            if (config.verboseOcrLogging) {
                BotLogs.addLog("👁️ [${targetTab.tabLabel}] Строк OCR: ${filteredLines.size}")
            }

            val visionLotCards = try {
                OpenCvVisionEngine.findLotCards(freshBitmap, screenWidth, screenHeight)
            } catch (_: Exception) {
                emptyList()
            }

            // Group listing lines into horizontal rows (46%..90% of screen height)
            val listingsTopLimit = freshBitmap.height * 0.46f
            val listingsBottomLimit = freshBitmap.height * 0.90f

            val listingLines = filteredLines.filter { line ->
                val bounds = line.boundingBox ?: return@filter false
                val centerY = bounds.centerY().toFloat()
                val lineText = line.text.lowercase().trim()
                val extracted = ScreenshotOcrAnalyzer.extractPrice(line.text)

                val isPureHeader = (lineText.contains("showed") || lineText.contains("from") ||
                    lineText.contains("market") || lineText.contains("kingdom")) && extracted == null

                centerY in listingsTopLimit..listingsBottomLimit && !isPureHeader &&
                    (extracted != null || line.text.any { it.isDigit() })
            }

            val tolerance = 65f
            val horizontalRows = mutableListOf<MutableList<Text.Line>>()
            for (line in listingLines) {
                val centerY = line.boundingBox?.centerY()?.toFloat() ?: continue
                val existing = horizontalRows.firstOrNull { row ->
                    val rowY = row.first().boundingBox?.centerY()?.toFloat() ?: 0f
                    abs(centerY - rowY) < tolerance
                }
                if (existing != null) existing.add(line) else horizontalRows.add(mutableListOf(line))
            }

            horizontalRows.sortBy { it.first().boundingBox?.top ?: 0 }
            horizontalRows.forEach { row -> row.sortBy { it.boundingBox?.left ?: 0 } }

            // Check stuck lot
            val currentScreenLots = mutableListOf<Pair<Double, Double>>()
            for (row in horizontalRows) {
                val numberPairs = row.mapNotNull { line ->
                    val v = ScreenshotOcrAnalyzer.extractPrice(line.text)
                    if (v != null) Pair(v, line.boundingBox) else null
                }
                val pricePair = numberPairs.maxByOrNull { it.second?.centerX() ?: 0 }
                if (pricePair != null && (pricePair.second?.left ?: 0) >= freshBitmap.width * 0.35f) {
                    val qty = numberPairs.firstOrNull { it != pricePair }?.first ?: 1.0
                    currentScreenLots.add(pricePair.first to qty)
                }
            }

            if (isStuckCheckPending && System.currentTimeMillis() - lastPurchaseAttemptTime < 20_000L) {
                val attemptedPrice = lastPurchasedPrice
                if (attemptedPrice != null && currentScreenLots.any { abs(it.first - attemptedPrice) < 0.1 }) {
                    BotLogs.addLog("⚠️ [ЗАВИСАНИЕ] Лот $attemptedPrice всё ещё на рынке — перезагружаем страницу...")
                    isStuckCheckPending = false
                    restartGameByReloadPage()
                    return@withContext
                }
                isStuckCheckPending = false
            }

            var foundLotsCount = 0
            for (row in horizontalRows) {
                val numberPairs = row.mapNotNull { line ->
                    val v = ScreenshotOcrAnalyzer.extractPrice(line.text)
                    if (v != null) Pair(v, line.boundingBox) else null
                }
                if (numberPairs.isEmpty()) continue

                val pricePair = numberPairs.maxByOrNull { it.second?.centerX() ?: 0 } ?: continue
                val leftCoord = pricePair.second?.left ?: 0
                if (leftCoord < freshBitmap.width * 0.35f) continue

                val price = pricePair.first
                val qty = numberPairs.firstOrNull { it != pricePair }?.first ?: 1.0
                foundLotsCount++

                val matches = ScreenshotOcrAnalyzer.evaluateThreshold(price, config)
                BotLogs.addLog(
                    "[${targetTab.tabLabel}] Лот #$foundLotsCount — Цена $price MM (${if (matches) "ПОДХОДИТ" else "пропуск"})"
                )

                if (matches && config.enableActualBuying) {
                    val top = row.mapNotNull { it.boundingBox?.top }.minOrNull() ?: 0
                    val bottom = row.mapNotNull { it.boundingBox?.bottom }.maxOrNull() ?: 0
                    val lotCenterY = ((top + bottom) / 2f) * scaleY
                    val clickX = (screenWidth * 0.50f).coerceIn(screenWidth * 0.35f, screenWidth * 0.75f)
                    val clickY = lotCenterY.coerceIn(screenHeight * 0.51f, screenHeight * 0.87f)

                    BotLogs.addLog("👉 [${targetTab.tabLabel}] Нажимаем на лот в (${clickX.toInt()}, ${clickY.toInt()})...")
                    val tapLotTime = System.currentTimeMillis()
                    clickAtWithRandomizationSuspend(clickX, clickY, config)

                    executeBuyConfirmation(
                        targetTab = targetTab,
                        price = price,
                        qty = qty,
                        config = config,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        density = density,
                        db = db,
                        recognizer = recognizer,
                        tapLotTime = tapLotTime
                    )
                    break
                }
            }

            // Stage 2: OpenCV isolated price ROI fallback
            if (foundLotsCount == 0 && visionLotCards.isNotEmpty()) {
                for (card in visionLotCards) {
                    val pRect = card.priceCropRect
                    if (pRect.width() <= 10 || pRect.height() <= 10) continue
                    val cropBmp = try {
                        Bitmap.createBitmap(freshBitmap, pRect.left, pRect.top, pRect.width(), pRect.height())
                    } catch (_: Exception) {
                        null
                    } ?: continue

                    var boughtViaOpenCv = false
                    try {
                        val cropResult = Tasks.await(recognizer.process(InputImage.fromBitmap(cropBmp, 0)))
                        val price = ScreenshotOcrAnalyzer.extractPrice(cropResult.text)
                        if (price != null) {
                            foundLotsCount++
                            val matches = ScreenshotOcrAnalyzer.evaluateThreshold(price, config)
                            BotLogs.addLog(
                                "[${targetTab.tabLabel} OpenCV] Лот — Цена $price MM (${if (matches) "ПОДХОДИТ" else "пропуск"})"
                            )
                            if (matches && config.enableActualBuying) {
                                BotLogs.addLog(
                                    "👉 [${targetTab.tabLabel}] Нажимаем по OpenCV-лоту (${card.handshakeCenterX.toInt()}, ${card.handshakeCenterY.toInt()})..."
                                )
                                val tapLotTime = System.currentTimeMillis()
                                clickAtWithRandomizationSuspend(card.handshakeCenterX, card.handshakeCenterY, config)
                                executeBuyConfirmation(
                                    targetTab = targetTab,
                                    price = price,
                                    qty = 1.0,
                                    config = config,
                                    screenWidth = screenWidth,
                                    screenHeight = screenHeight,
                                    density = density,
                                    db = db,
                                    recognizer = recognizer,
                                    tapLotTime = tapLotTime
                                )
                                boughtViaOpenCv = true
                            }
                        }
                    } finally {
                        cropBmp.recycle()
                    }
                    if (boughtViaOpenCv) break
                }
            }

            if (foundLotsCount == 0) {
                val nowMs = System.currentTimeMillis()
                if (config.verboseOcrLogging || nowMs - lastHeartbeatLogTime >= 3000L) {
                    lastHeartbeatLogTime = nowMs
                    BotLogs.addLog("🔎 [${targetTab.tabLabel}] Скан выполнен: подходящих лотов на экране нет (порог ${config.priceThreshold})")
                }
            }
        } finally {
            freshBitmap.recycle()
        }
    }

    private suspend fun executeBuyConfirmation(
        targetTab: MarketTab,
        price: Double,
        qty: Double,
        config: AppConfiguration,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        db: AppDatabase,
        recognizer: TextRecognizer,
        tapLotTime: Long
    ): Boolean {
        BotLogs.addLog("⏳ Ожидаем появление модального окна подтверждения покупки...")
        delay(CONFIRMATION_INITIAL_DELAY_MS)

        if (config.calibratedConfirmX != -1f && config.calibratedConfirmY != -1f) {
            val calX = config.calibratedConfirmX
            val calY = config.calibratedConfirmY
            BotLogs.addLog("🎯 [КАЛИБРОВКА] Кликаем сохранённую кнопку подтверждения в (${calX.toInt()}, ${calY.toInt()})")
            val confirmTapTime = System.currentTimeMillis()
            dispatchClickSuspend(calX, calY)
            return verifyPurchaseResultAndHandleFailure(
                targetTab, price, qty, config, screenWidth, screenHeight, density, db, recognizer,
                lastClickX = calX, lastClickY = calY, confirmTapTime = confirmTapTime
            )
        }

        // Poll fresh screenshots until the confirmation modal finishes rendering (up to ~1.2s)
        var detectedButton: Pair<Pair<Float, Float>, String>? = null
        var modalSeenOnScreen = false

        for (attempt in 1..6) {
            val minTs = if (attempt == 1) tapLotTime + 50L else System.currentTimeMillis() - 30L
            val dialogBitmap = MediaProjectionHelper.captureFreshScreenshot(
                minTimestampMs = minTs,
                waitTimeoutMs = 240L
            )
            if (dialogBitmap != null) {
                try {
                    val scaleX = screenWidth / dialogBitmap.width
                    val scaleY = screenHeight / dialogBitmap.height
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(dialogBitmap, 0)))
                    val lines = result.textBlocks.flatMap { it.lines }.filter { line ->
                        !isInsideOverlay(line.boundingBox, screenWidth, screenHeight, density, scaleX, scaleY) &&
                            !isLogOrOverlayText(line.text)
                    }

                    if (handleTryThroughDialogIfNeeded(lines, scaleX, scaleY, screenWidth, screenHeight, config)) {
                        return false
                    }
                    if (dismissSystemDialogIfNeeded(lines, scaleX, scaleY, screenWidth, screenHeight, config)) {
                        return false
                    }

                    val modalOpen = isConfirmationDialogShowing(lines, scaleX, scaleY, screenWidth, screenHeight)
                    val buttonMatch = findBuyButtonCoordinates(
                        dialogBitmap, lines, scaleX, scaleY, screenWidth, screenHeight, config, allowCache = false
                    )

                    if (buttonMatch != null && (modalOpen || buttonMatch.second.startsWith("OCR"))) {
                        detectedButton = buttonMatch
                        modalSeenOnScreen = true
                        break
                    } else if (modalOpen) {
                        modalSeenOnScreen = true
                        // Wait one extra short beat if button wasn't found yet, unless this is the final poll
                        if (attempt >= 3) {
                            detectedButton = buttonMatch
                            break
                        }
                    }
                } catch (e: Exception) {
                    BotLogs.addLog("⚠️ Ошибка OCR модального окна: ${e.message}")
                } finally {
                    dialogBitmap.recycle()
                }
            }
            delay(130L)
        }

        if (!modalSeenOnScreen && detectedButton == null) {
            // Check cached coordinates ONLY if modal might have rendered without recognizable text
            BotLogs.addLog("⚠️ [ПОДТВЕРЖДЕНИЕ] Окно покупки не появилось после клика по лоту (возможно, лот уже снят или WebView не успел открыться).")
            return false
        }

        val (coords, source) = detectedButton ?: run {
            val fallback = (cachedBuyX ?: (screenWidth * 0.50f)) to (cachedBuyY ?: (screenHeight * 0.746f))
            val label = if (cachedBuyX != null && cachedBuyY != null) "Кэш" else "Резервная геометрия"
            fallback to label
        }

        val buyClickX = coords.first
        val buyClickY = coords.second
        if (source.startsWith("OCR") || source == "OpenCV") {
            cachedBuyX = buyClickX
            cachedBuyY = buyClickY
        }

        BotLogs.addLog("⚡ [ПОДТВЕРЖДЕНИЕ] Нажимаем кнопку [$source] в (${buyClickX.toInt()}, ${buyClickY.toInt()})")
        val confirmTapTime = System.currentTimeMillis()
        dispatchClickSuspend(buyClickX, buyClickY)

        return verifyPurchaseResultAndHandleFailure(
            targetTab, price, qty, config, screenWidth, screenHeight, density, db, recognizer,
            lastClickX = buyClickX, lastClickY = buyClickY, confirmTapTime = confirmTapTime
        )
    }

    private suspend fun confirmOpenPurchaseDialog(
        lines: List<Text.Line>,
        scaleX: Float,
        scaleY: Float,
        screenWidth: Float,
        screenHeight: Float,
        config: AppConfiguration,
        db: AppDatabase,
        bitmap: Bitmap,
        targetTab: MarketTab,
        recognizer: TextRecognizer
    ): Boolean {
        val detectedPrice = lines.firstNotNullOfOrNull { ScreenshotOcrAnalyzer.extractPrice(it.text) } ?: 0.0
        val found = findBuyButtonCoordinates(
            bitmap, lines, scaleX, scaleY, screenWidth, screenHeight, config, allowCache = true
        )
        val (coords, source) = found ?: ((screenWidth * 0.50f to screenHeight * 0.746f) to "Резервная геометрия")

        if (source.startsWith("OCR") || source == "OpenCV") {
            cachedBuyX = coords.first
            cachedBuyY = coords.second
        }

        BotLogs.addLog("🎯 [ОТКРЫТОЕ ОКНО ПОКУПКИ] Нажимаем кнопку [$source] в (${coords.first.toInt()}, ${coords.second.toInt()})")
        val confirmTapTime = System.currentTimeMillis()
        dispatchClickSuspend(coords.first, coords.second)

        return verifyPurchaseResultAndHandleFailure(
            targetTab, detectedPrice, 1.0, config, screenWidth, screenHeight,
            resources.displayMetrics.density, db, recognizer,
            lastClickX = coords.first, lastClickY = coords.second, confirmTapTime = confirmTapTime
        )
    }

    private suspend fun verifyPurchaseResultAndHandleFailure(
        targetTab: MarketTab,
        price: Double,
        qty: Double,
        config: AppConfiguration,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        db: AppDatabase,
        recognizer: TextRecognizer,
        lastClickX: Float,
        lastClickY: Float,
        confirmTapTime: Long
    ): Boolean {
        var prevX = lastClickX
        var prevY = lastClickY
        var tapTimestamp = confirmTapTime

        for (attempt in 1..4) {
            delay(360L)
            val verifyBitmap = MediaProjectionHelper.captureFreshScreenshot(
                minTimestampMs = tapTimestamp + 60L,
                waitTimeoutMs = 380L
            ) ?: continue

            var shouldReturnFalse = false
            var shouldReturnTrue = false
            var retryCoords: Pair<Float, Float>? = null
            var retrySource = ""

            try {
                val scaleX = screenWidth / verifyBitmap.width
                val scaleY = screenHeight / verifyBitmap.height
                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(verifyBitmap, 0)))
                val lines = result.textBlocks.flatMap { it.lines }.filter { line ->
                    !isInsideOverlay(line.boundingBox, screenWidth, screenHeight, density, scaleX, scaleY) &&
                        !isLogOrOverlayText(line.text)
                }

                // 1. Rate-limit popup -> close and retry later without cooldown
                if (handleTryThroughDialogIfNeeded(lines, scaleX, scaleY, screenWidth, screenHeight, config)) {
                    cooldownUntilMillis = 0L
                    shouldReturnFalse = true
                } else if (isAlreadyPurchasedDialog(lines)) {
                    // 2. "Already purchased" popup -> dismiss and clear cooldown
                    dismissSystemDialogIfNeeded(lines, scaleX, scaleY, screenWidth, screenHeight, config)
                    BotLogs.addLog("⚠️ [ОШИБКА ПОКУПКИ] Лот уже куплен другим игроком. Кулдаун сброшен.")
                    cooldownUntilMillis = 0L
                    shouldReturnFalse = true
                } else if (isMailMarketBuyOfferDialog(lines)) {
                    // 3. Explicit success popup ("Mail / Market buy offer") -> dismiss and record purchase
                    dismissSystemDialogIfNeeded(lines, scaleX, scaleY, screenWidth, screenHeight, config)
                    recordConfirmedPurchase(targetTab, price, qty, db)
                    shouldReturnTrue = true
                } else if (isConfirmationDialogShowing(lines, scaleX, scaleY, screenWidth, screenHeight)) {
                    // 4. Confirmation modal is STILL open! Previous tap missed — invalidate cache & retry!
                    cachedBuyX = null
                    cachedBuyY = null

                    val found = findBuyButtonCoordinates(
                        verifyBitmap, lines, scaleX, scaleY, screenWidth, screenHeight,
                        config.copy(calibratedConfirmX = -1f, calibratedConfirmY = -1f),
                        allowCache = false
                    )
                    if (found != null) {
                        var candidateX = found.first.first
                        var candidateY = found.first.second
                        // If same point failed twice in a row and wasn't an exact OCR text hit, try nudging Y
                        if (!found.second.startsWith("OCR") &&
                            abs(candidateX - prevX) < 12f && abs(candidateY - prevY) < 12f
                        ) {
                            val nudge = when (attempt) {
                                1 -> -screenHeight * 0.035f
                                2 -> screenHeight * 0.035f
                                else -> -screenHeight * 0.07f
                            }
                            candidateY = (candidateY + nudge).coerceIn(screenHeight * 0.52f, screenHeight * 0.90f)
                        }
                        retryCoords = candidateX to candidateY
                        retrySource = found.second
                    } else {
                        val nudge = when (attempt) {
                            1 -> -screenHeight * 0.045f
                            2 -> screenHeight * 0.045f
                            else -> -screenHeight * 0.09f
                        }
                        retryCoords = (screenWidth * 0.50f) to (prevY + nudge).coerceIn(screenHeight * 0.55f, screenHeight * 0.88f)
                        retrySource = "Смещение Y"
                    }
                } else {
                    // 5. Confirmation modal is GONE and no failure popup appeared -> purchase succeeded!
                    recordConfirmedPurchase(targetTab, price, qty, db)
                    shouldReturnTrue = true
                }
            } catch (e: Exception) {
                BotLogs.addLog("⚠️ Ошибка проверки статуса покупки: ${e.message}")
            } finally {
                verifyBitmap.recycle()
            }

            if (shouldReturnFalse) return false
            if (shouldReturnTrue) return true

            if (retryCoords != null) {
                prevX = retryCoords.first
                prevY = retryCoords.second
                if (retrySource.startsWith("OCR") || retrySource == "OpenCV") {
                    cachedBuyX = prevX
                    cachedBuyY = prevY
                }
                BotLogs.addLog("🔄 [ПОВТОР $attempt/4] Окно подтверждения ещё открыто — кликаем [$retrySource] в (${prevX.toInt()}, ${prevY.toInt()})")
                tapTimestamp = System.currentTimeMillis()
                dispatchClickSuspend(prevX, prevY)
            }
        }

        cachedBuyX = null
        cachedBuyY = null
        BotLogs.addLog("❌ [ПОДТВЕРЖДЕНИЕ] Окно подтверждения не закрылось после 4 попыток. Кулдаун НЕ ставим.")
        return false
    }

    private suspend fun recordConfirmedPurchase(
        targetTab: MarketTab,
        price: Double,
        qty: Double,
        db: AppDatabase
    ) {
        val itemName = if (targetTab == MarketTab.PUZZLES) "Earth puzzles" else "Land plot"
        val details = "Куплено на вкладке ${targetTab.tabLabel} за $price MM"
        try {
            db.purchaseDao().insertPurchase(
                PurchaseRecord(
                    timestamp = System.currentTimeMillis(),
                    tabName = targetTab.tabLabel,
                    itemName = itemName,
                    price = price,
                    quantity = qty,
                    details = details
                )
            )
            purchasesThisCycleRun++
            BotLogs.addLog("🎉 $details")
        } catch (e: Exception) {
            BotLogs.addLog("⚠️ Ошибка сохранения покупки: ${e.message}")
        }

        cooldownUntilMillis = System.currentTimeMillis() + 15_000L
        lastPurchasedPrice = price
        lastPurchasedQuantity = qty
        lastPurchaseAttemptTime = System.currentTimeMillis()
        isStuckCheckPending = true
    }

    /**
     * Locates the "Buy" / "Confirm" button inside a confirmation dialog using:
     * 1) Calibrated Confirm coords (if set)
     * 2) ML Kit OCR button text in lower-central modal band (X 0.20..0.80, Y 0.54..0.90)
     * 3) OpenCV green/gold button contour detector
     * 4) Cached verified coordinates (only if [allowCache] is true)
     * 5) Relative Y offset below "You will pay" / "Price" line (without polluting cache)
     */
    private fun findBuyButtonCoordinates(
        bitmap: Bitmap,
        lines: List<Text.Line>,
        scaleX: Float,
        scaleY: Float,
        screenWidth: Float,
        screenHeight: Float,
        config: AppConfiguration,
        allowCache: Boolean
    ): Pair<Pair<Float, Float>, String>? {
        if (config.calibratedConfirmX != -1f && config.calibratedConfirmY != -1f) {
            return (config.calibratedConfirmX to config.calibratedConfirmY) to "Калибровка"
        }

        // 1. ML Kit OCR exact button text match (lines and individual elements)
        val ocrCandidates = mutableListOf<Pair<String, Rect>>()
        for (line in lines) {
            val b = line.boundingBox ?: continue
            val cX = (b.centerX() * scaleX) / screenWidth
            val cY = (b.centerY() * scaleY) / screenHeight
            if (cX in 0.20f..0.80f && cY in 0.54f..0.90f && isBuyConfirmationText(line.text)) {
                ocrCandidates.add(line.text to b)
            }
            for (elem in line.elements) {
                val eb = elem.boundingBox ?: continue
                val ecX = (eb.centerX() * scaleX) / screenWidth
                val ecY = (eb.centerY() * scaleY) / screenHeight
                if (ecX in 0.20f..0.80f && ecY in 0.54f..0.90f && isBuyConfirmationText(elem.text)) {
                    ocrCandidates.add(elem.text to eb)
                }
            }
        }
        val bestOcr = ocrCandidates.maxByOrNull { it.second.centerY() }
        if (bestOcr != null) {
            val bx = bestOcr.second.centerX() * scaleX
            val by = bestOcr.second.centerY() * scaleY
            return (bx to by) to "OCR: '${bestOcr.first.trim()}'"
        }

        // 2. OpenCV button contour (emerald-green or golden-bordered)
        val cvBtn = OpenCvVisionEngine.findConfirmationBuyButton(bitmap, screenWidth, screenHeight)
        if (cvBtn != null) {
            return cvBtn to "OpenCV"
        }

        // 3. Cached verified OCR/OpenCV point
        if (allowCache && cachedBuyX != null && cachedBuyY != null) {
            return (cachedBuyX!! to cachedBuyY!!) to "Кэш"
        }

        // 4. Relative geometric offset below "You will pay" / "Вы заплатите"
        val payLine = lines.firstOrNull { line ->
            val b = line.boundingBox ?: return@firstOrNull false
            val cY = (b.centerY() * scaleY) / screenHeight
            val t = line.text.lowercase()
            cY in 0.36f..0.72f && (t.contains("will pay") || t.contains("заплатит") || t.contains("pay"))
        }
        if (payLine?.boundingBox != null) {
            val payY = payLine.boundingBox!!.centerY() * scaleY
            val estY = (payY + screenHeight * 0.145f).coerceIn(screenHeight * 0.58f, screenHeight * 0.88f)
            return (screenWidth * 0.50f to estY) to "Смещение от оплаты"
        }

        return null
    }

    private fun isBuyConfirmationText(text: String): Boolean {
        val t = text.lowercase().trim()
        return t == "buy" || t.contains("buy") ||
            t.contains("3uy") || t.contains("13uy") ||
            t.contains("buv") || t.contains("bvy") ||
            t.contains("boy") || t.contains("bov") || t.contains("bup") ||
            t.contains("купить") || t.contains("выкупить") ||
            t.contains("kynut") || t.contains("kupit") || t.contains("kup") ||
            t.contains("confirm") || t.contains("conflrm") ||
            t.contains("conlirm") || t.contains("conirm") ||
            t.contains("contirm") || t.contains("cornfirm") ||
            t.contains("comfirm") || t.contains("confrm") ||
            t.contains("подтверд") || t.contains("noдтв")
    }

    private fun isConfirmOrOkButtonText(text: String): Boolean {
        val t = text.lowercase().trim()
        val words = t.split(Regex("[\\s,.:;!?-]+")).filter { it.isNotEmpty() }
        if (words.contains("ok") || words.contains("ок") || words.contains("yes") || words.contains("да")) {
            return true
        }
        return t.contains("confirm") || t.contains("conflrm") || t.contains("conlirm") ||
            t.contains("comfirm") || t.contains("confrm") ||
            t.contains("close") || t.contains("mark") || t.contains("read") ||
            t.contains("подтверд") || t.contains("закрыть") || t.contains("понятно")
    }

    private fun isAlreadyPurchasedDialog(lines: List<Text.Line>): Boolean {
        val fullText = lines.joinToString(" ") { it.text.lowercase() }
        return (fullText.contains("already") && fullText.contains("purchased")) ||
            fullText.contains("been purchased") ||
            fullText.contains("уже куплен") ||
            (fullText.contains("has") && fullText.contains("purchased"))
    }

    private fun isMailMarketBuyOfferDialog(lines: List<Text.Line>): Boolean {
        val fullText = lines.joinToString(" ") { it.text.lowercase() }
        return (fullText.contains("market") && fullText.contains("offer")) ||
            fullText.contains("buy offer") ||
            fullText.contains("buyoffer") ||
            fullText.contains("mark all") ||
            (fullText.contains("mark") && fullText.contains("read")) ||
            fullText.contains("рыночное предложение")
    }

    private fun isConfirmationDialogShowing(
        lines: List<Text.Line>,
        scaleX: Float,
        scaleY: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Boolean {
        if (screenHeight <= 0f) return false
        val fullText = lines.joinToString(" ") { it.text.lowercase() }

        // Exclude error / rate-limit / success mail popups
        if (isAlreadyPurchasedDialog(lines) ||
            isMailMarketBuyOfferDialog(lines) ||
            fullText.contains("try through") ||
            fullText.contains("try again") ||
            fullText.contains("too fast") ||
            fullText.contains("попробуйте через")
        ) {
            return false
        }

        val hasModalPhrase = fullText.contains("you will pay") ||
            fullText.contains("vou will pay") ||
            fullText.contains("you wil pay") ||
            fullText.contains("вы заплатите") ||
            fullText.contains("bы заплатите") ||
            fullText.contains("вы заплатит") ||
            fullText.contains("покупка") ||
            fullText.contains("confirm purchase") ||
            fullText.contains("are you sure") ||
            fullText.contains("вы уверены") ||
            fullText.contains("подтверждение") ||
            fullText.contains("подтвердите")

        if (hasModalPhrase) return true

        // Also match if there is an explicit Buy/Confirm/Купить button inside the lower-central modal band
        // (Safe because top "Buy" market tab sits around Y ~ 0.30f and listings have no "Buy" text).
        return lines.any { line ->
            val b = line.boundingBox ?: return@any false
            val cX = (b.centerX() * scaleX) / screenWidth
            val cY = (b.centerY() * scaleY) / screenHeight
            cX in 0.22f..0.78f && cY in 0.56f..0.89f && isBuyConfirmationText(line.text)
        }
    }

    private suspend fun handleTryThroughDialogIfNeeded(
        lines: List<Text.Line>,
        scaleX: Float,
        scaleY: Float,
        screenWidth: Float,
        screenHeight: Float,
        config: AppConfiguration
    ): Boolean {
        val fullText = lines.joinToString(" ") { it.text.lowercase() }
        val isRateLimit = fullText.contains("try through") ||
            fullText.contains("too fast") ||
            (fullText.contains("try") && fullText.contains("second")) ||
            fullText.contains("попробуйте через") ||
            fullText.contains("подождите")

        if (!isRateLimit || isAlreadyPurchasedDialog(lines) || isMailMarketBuyOfferDialog(lines)) {
            return false
        }

        val regex = Regex("(\\d+)")
        val secMatch = lines.firstNotNullOfOrNull { line ->
            val t = line.text.lowercase()
            if (t.contains("through") || t.contains("sec") || t.contains("через") || t.contains("try")) {
                regex.find(line.text)?.value?.toIntOrNull()?.takeIf { it in 1..120 }
            } else null
        } ?: 2

        val okLine = lines.firstOrNull { line ->
            val b = line.boundingBox ?: return@firstOrNull false
            val cY = (b.centerY() * scaleY) / screenHeight
            cY in 0.55f..0.92f && isConfirmOrOkButtonText(line.text)
        }

        val tapX = okLine?.boundingBox?.let { it.centerX() * scaleX }
            ?: if (config.calibratedConfirmX != -1f) config.calibratedConfirmX else screenWidth * 0.50f
        val tapY = okLine?.boundingBox?.let { it.centerY() * scaleY }
            ?: if (config.calibratedConfirmY != -1f) config.calibratedConfirmY else screenHeight * 0.68f

        val waitSec = secMatch + 2
        BotLogs.addLog("⏳ [ОГРАНИЧЕНИЕ] Окно 'Try through $secMatch sec'. Закрываем в (${tapX.toInt()}, ${tapY.toInt()}) и ждём ${waitSec}с.")
        dispatchClickSuspend(tapX, tapY)
        delay(waitSec * 1000L)
        return true
    }

    private suspend fun dismissSystemDialogIfNeeded(
        lines: List<Text.Line>,
        scaleX: Float,
        scaleY: Float,
        screenWidth: Float,
        screenHeight: Float,
        config: AppConfiguration
    ): Boolean {
        val fullText = lines.joinToString(" ") { it.text.lowercase() }
        val shouldDismiss = isAlreadyPurchasedDialog(lines) ||
            isMailMarketBuyOfferDialog(lines) ||
            fullText.contains("try again")
        if (!shouldDismiss) return false

        val okLine = lines.filter { line ->
            val b = line.boundingBox ?: return@filter false
            val cY = (b.centerY() * scaleY) / screenHeight
            val cX = (b.centerX() * scaleX) / screenWidth
            cY in 0.58f..0.93f && cX in 0.20f..0.80f && isConfirmOrOkButtonText(line.text)
        }.maxByOrNull { it.boundingBox?.centerY() ?: 0 }

        val tapX = okLine?.boundingBox?.let { it.centerX() * scaleX }
            ?: if (config.calibratedConfirmX != -1f) config.calibratedConfirmX else (screenWidth * 0.50f)
        val tapY = okLine?.boundingBox?.let { it.centerY() * scaleY }
            ?: if (config.calibratedConfirmY != -1f) config.calibratedConfirmY else (screenHeight * 0.78f)
        BotLogs.addLog("🧹 Закрываем системное окно игры в (${tapX.toInt()}, ${tapY.toInt()})")
        dispatchClickSuspend(tapX, tapY)
        delay(220L)
        return true
    }

    private suspend fun restartGameByReloadPage() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        dispatchClickSuspend(w * 0.94f, h * 0.085f)
        delay(450L)
        dispatchClickSuspend(w * 0.72f, h * 0.16f)
    }

    fun clickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 45L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private suspend fun dispatchClickSuspend(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        val path = Path().apply { moveTo(x.coerceAtLeast(1f), y.coerceAtLeast(1f)) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return withTimeoutOrNull(350L) {
            suspendCancellableCoroutine { cont ->
                val dispatched = dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null
                )
                if (!dispatched && cont.isActive) {
                    cont.resume(false)
                }
            }
        } ?: false
    }

    private fun randomizedCoords(x: Float, y: Float, config: AppConfiguration): Pair<Float, Float> {
        val radius = config.clickRandomizationRadiusPx.coerceAtLeast(0)
        if (radius == 0) return x to y
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        val dist = sqrt(Random.nextDouble(0.0, 1.0)) * radius
        val rx = (x + (cos(angle) * dist).toFloat()).coerceAtLeast(1f)
        val ry = (y + (sin(angle) * dist).toFloat()).coerceAtLeast(1f)
        return rx to ry
    }

    private fun clickAtWithRandomization(x: Float, y: Float, config: AppConfiguration) {
        val (rx, ry) = randomizedCoords(x, y, config)
        clickAt(rx, ry)
    }

    private suspend fun clickAtWithRandomizationSuspend(x: Float, y: Float, config: AppConfiguration) {
        val (rx, ry) = randomizedCoords(x, y, config)
        dispatchClickSuspend(rx, ry)
    }

    private fun isInsideOverlay(
        rect: Rect?,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        scaleX: Float,
        scaleY: Float
    ): Boolean {
        if (rect == null) return false
        val isPanelVisible = OverlayControlService.instance?.isControlPanelVisible() ?: false
        if (!isPanelVisible) return false

        val overlayWidth = 280 * density
        val overlayHeight = 360 * density
        val leftBound = (screenWidth - overlayWidth) / 2
        val rightBound = (screenWidth + overlayWidth) / 2
        val topBound = (screenHeight - overlayHeight) / 2
        val bottomBound = (screenHeight + overlayHeight) / 2

        val cx = rect.centerX() * scaleX
        val cy = rect.centerY() * scaleY
        return cx in leftBound..rightBound && cy in topBound..bottomBound
    }

    private fun isLogOrOverlayText(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.startsWith("[") ||
            lower.contains("live log") ||
            lower.contains("lot monitor") ||
            lower.contains("кликаем") ||
            lower.contains("порог")
    }
}
