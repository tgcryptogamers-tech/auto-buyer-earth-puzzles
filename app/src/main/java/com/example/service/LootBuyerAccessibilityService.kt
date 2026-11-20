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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        const val CONFIRMATION_DIALOG_DELAY_MS = 180L

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

        var targetCoords = getTabCoordinates(targetTab, config)
        var altCoords = getTabCoordinates(alternateTab, config)

        // Step 1: Discover tab coordinates via OCR if not yet known
        if (targetCoords == null || (config.switchBetweenTabs && altCoords == null)) {
            val initialBitmap = MediaProjectionHelper.getLatestScreenshot()
            if (initialBitmap == null) {
                BotLogs.addLog("🔍 [ОЖИДАНИЕ] Включите захват экрана (Screen Capture) и откройте рынок в игре.")
                return@withContext
            }

            try {
                val initScaleX = screenWidth / initialBitmap.width
                val initScaleY = screenHeight / initialBitmap.height
                val result = Tasks.await(recognizer.process(InputImage.fromBitmap(initialBitmap, 0)))
                scanTabsFromOcrResults(result.textBlocks, initScaleX, initScaleY, screenHeight)
            } catch (e: Exception) {
                BotLogs.addLog("⚠️ Ошибка OCR поиска вкладок: ${e.message}")
            } finally {
                initialBitmap.recycle()
            }

            targetCoords = getTabCoordinates(targetTab, config)
            altCoords = getTabCoordinates(alternateTab, config)
        }

        if (targetCoords == null) {
            BotLogs.addLog("⚠️ Вкладка '${targetTab.tabLabel}' не найдена на экране. Включите калибровку в настройках и укажите вкладки вручную.")
            return@withContext
        }

        // Step 2: Switch between Puzzles and Lands in the game to force market refresh
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
        val freshBitmap = MediaProjectionHelper.getLatestScreenshot() ?: return@withContext
        try {
            val scaleX = screenWidth / freshBitmap.width
            val scaleY = screenHeight / freshBitmap.height
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(freshBitmap, 0)))

            val allLines = result.textBlocks.flatMap { it.lines }
            val filteredLines = allLines.filter { line ->
                !isInsideOverlay(line.boundingBox, screenWidth, screenHeight, density, scaleX, scaleY) &&
                    !isLogOrOverlayText(line.text)
            }

            // Dismiss "already purchased" / "market offer" modal if present
            if (dismissLotAlreadyPurchasedDialogIfNeeded(filteredLines, scaleX, scaleY, screenWidth, screenHeight)) {
                return@withContext
            }

            // Complete open confirmation dialog if already on screen
            if (config.enableActualBuying && confirmPurchaseDialogIfNeeded(
                    filteredLines, scaleX, scaleY, screenWidth, screenHeight, config, db, freshBitmap, targetTab
                )
            ) {
                return@withContext
            }

            if (config.verboseOcrLogging) {
                BotLogs.addLog("👁️ [${targetTab.tabLabel}] Строк OCR: ${filteredLines.size}")
            }

            // OpenCV handshake fallback detection
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
                    clickAtWithRandomization(clickX, clickY, config)

                    executeBuyConfirmation(targetTab, price, qty, config, screenWidth, screenHeight, db)
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
                                clickAtWithRandomization(card.handshakeCenterX, card.handshakeCenterY, config)
                                executeBuyConfirmation(targetTab, price, 1.0, config, screenWidth, screenHeight, db)
                                break
                            }
                        }
                    } finally {
                        cropBmp.recycle()
                    }
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
        db: AppDatabase
    ): Boolean {
        delay(CONFIRMATION_DIALOG_DELAY_MS)

        var buyClickX = if (config.calibratedConfirmX != -1f) config.calibratedConfirmX else screenWidth * 0.50f
        var buyClickY = if (config.calibratedConfirmY != -1f) config.calibratedConfirmY else screenHeight * 0.746f

        if (config.calibratedConfirmX == -1f && cachedBuyX != null && cachedBuyY != null) {
            buyClickX = cachedBuyX!!
            buyClickY = cachedBuyY!!
        } else if (config.calibratedConfirmX == -1f) {
            val dialogBitmap = MediaProjectionHelper.getLatestScreenshot()
            if (dialogBitmap != null) {
                try {
                    val cvBtn = OpenCvVisionEngine.findConfirmationBuyButton(dialogBitmap, screenWidth, screenHeight)
                    if (cvBtn != null) {
                        buyClickX = cvBtn.first
                        buyClickY = cvBtn.second
                    }
                } finally {
                    dialogBitmap.recycle()
                }
            }
        }

        cachedBuyX = buyClickX
        cachedBuyY = buyClickY
        BotLogs.addLog("⚡ [ПОДТВЕРЖДЕНИЕ] Кликаем Buy/Confirm в (${buyClickX.toInt()}, ${buyClickY.toInt()})")
        clickAt(buyClickX, buyClickY)
        delay(70L)
        clickAt(buyClickX, buyClickY)

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
        return true
    }

    private suspend fun confirmPurchaseDialogIfNeeded(
        lines: List<Text.Line>,
        scaleX: Float,
        scaleY: Float,
        screenWidth: Float,
        screenHeight: Float,
        config: AppConfiguration,
        db: AppDatabase,
        bitmap: Bitmap,
        targetTab: MarketTab
    ): Boolean {
        val fullText = lines.joinToString(" ") { it.text.lowercase() }
        val isOtherDialog = (fullText.contains("already") && fullText.contains("purchased")) ||
            fullText.contains("been purchased") ||
            fullText.contains("уже куплен") ||
            (fullText.contains("market") && fullText.contains("offer")) ||
            fullText.contains("buy offer") ||
            fullText.contains("try through") ||
            fullText.contains("too fast")
        if (isOtherDialog) return false

        val isConfirmOpen = fullText.contains("you will pay") ||
            fullText.contains("вы заплатите") ||
            fullText.contains("покупка") ||
            fullText.contains("купить")

        if (!isConfirmOpen) return false

        var buyX = cachedBuyX ?: (screenWidth * 0.50f)
        var buyY = cachedBuyY ?: (screenHeight * 0.746f)

        val cvBtn = OpenCvVisionEngine.findConfirmationBuyButton(bitmap, screenWidth, screenHeight)
        if (cvBtn != null) {
            buyX = cvBtn.first
            buyY = cvBtn.second
        } else {
            val buyLine = lines.firstOrNull { line ->
                val b = line.boundingBox ?: return@firstOrNull false
                val cY = (b.centerY() * scaleY) / screenHeight
                val cX = (b.centerX() * scaleX) / screenWidth
                val t = line.text.lowercase()
                cX in 0.25f..0.75f && cY in 0.66f..0.84f &&
                    (t.contains("buy") || t.contains("купить") || t.contains("confirm"))
            }
            if (buyLine?.boundingBox != null) {
                buyX = buyLine.boundingBox!!.centerX() * scaleX
                buyY = buyLine.boundingBox!!.centerY() * scaleY
            }
        }

        cachedBuyX = buyX
        cachedBuyY = buyY
        clickAt(buyX, buyY)
        delay(60L)
        clickAt(buyX, buyY)

        val detectedPrice = lines.firstNotNullOfOrNull { ScreenshotOcrAnalyzer.extractPrice(it.text) } ?: 0.0
        val itemName = if (targetTab == MarketTab.PUZZLES) "Earth puzzles" else "Land plot"
        db.purchaseDao().insertPurchase(
            PurchaseRecord(
                timestamp = System.currentTimeMillis(),
                tabName = targetTab.tabLabel,
                itemName = itemName,
                price = detectedPrice,
                quantity = 1.0,
                details = "Подтверждена покупка на вкладке ${targetTab.tabLabel} за $detectedPrice MM"
            )
        )
        purchasesThisCycleRun++
        cooldownUntilMillis = System.currentTimeMillis() + 15_000L
        return true
    }

    private suspend fun dismissLotAlreadyPurchasedDialogIfNeeded(
        lines: List<Text.Line>,
        scaleX: Float,
        scaleY: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Boolean {
        val fullText = lines.joinToString(" ") { it.text.lowercase() }
        val shouldDismiss = (fullText.contains("already") && fullText.contains("purchased")) ||
            fullText.contains("been purchased") ||
            fullText.contains("уже куплен") ||
            (fullText.contains("market") && fullText.contains("offer")) ||
            fullText.contains("buy offer") ||
            fullText.contains("mark all") ||
            fullText.contains("try again")
        if (!shouldDismiss) return false

        val okLine = lines.firstOrNull { line ->
            val b = line.boundingBox ?: return@firstOrNull false
            val cY = (b.centerY() * scaleY) / screenHeight
            val cX = (b.centerX() * scaleX) / screenWidth
            val t = line.text.lowercase()
            cY in 0.65f..0.92f && cX in 0.25f..0.75f &&
                (t.contains("ok") || t.contains("close") || t.contains("mark") || t.contains("read") || t.contains("закрыть"))
        }

        val tapX = okLine?.boundingBox?.let { it.centerX() * scaleX } ?: (screenWidth * 0.50f)
        val tapY = okLine?.boundingBox?.let { it.centerY() * scaleY } ?: (screenHeight * 0.80f)
        BotLogs.addLog("🧹 Закрываем системное окно игры в (${tapX.toInt()}, ${tapY.toInt()})")
        clickAt(tapX, tapY)
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
        // Nicegram / Telegram MiniApp top-right 3-dots menu -> Reload Page area
        clickAt(w * 0.94f, h * 0.085f)
        delay(450L)
        clickAt(w * 0.72f, h * 0.16f)
    }

    fun clickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 45L))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun clickAtWithRandomization(x: Float, y: Float, config: AppConfiguration) {
        val radius = config.clickRandomizationRadiusPx.coerceAtLeast(0)
        if (radius == 0) {
            clickAt(x, y)
            return
        }
        val angle = Random.nextDouble(0.0, Math.PI * 2.0)
        val dist = sqrt(Random.nextDouble(0.0, 1.0)) * radius
        val rx = (x + (cos(angle) * dist).toFloat()).coerceAtLeast(1f)
        val ry = (y + (sin(angle) * dist).toFloat()).coerceAtLeast(1f)
        clickAt(rx, ry)
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
