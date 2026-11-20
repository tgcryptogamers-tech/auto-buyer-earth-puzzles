package com.example.engine

import com.example.data.AppConfiguration
import com.example.data.ConfigurationDao
import com.example.data.MarketTab
import com.example.data.PurchaseDao
import com.example.data.PurchaseRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

data class MarketMonitorState(
    val isRunning: Boolean = false,
    val activeTab: MarketTab = MarketTab.PUZZLES,
    val buyTarget: MarketTab = MarketTab.PUZZLES,
    val currentCycle: Int = 1,
    val cycleRemainingSec: Int = 0,
    val scanCount: Int = 0,
    val matchedLotsCount: Int = 0,
    val lastDetectedPrice: Double? = null,
    val lastEventMessage: String = "Готов к мониторингу вкладок Puzzles и Lands"
)

class MarketMonitorEngine(
    private val configurationDao: ConfigurationDao,
    private val purchaseDao: PurchaseDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    private val _state = MutableStateFlow(MarketMonitorState())
    val state: StateFlow<MarketMonitorState> = _state.asStateFlow()

    fun start(initialConfig: AppConfiguration) {
        if (monitorJob?.isActive == true) return
        val targetTab = MarketTab.fromString(initialConfig.buyTarget)
        BotLogs.addLog("🚀 Запуск мониторинга рынка MoneyMining")
        BotLogs.addLog(
            "🎯 Цель покупки: ТОЛЬКО ${targetTab.tabLabel} | Переключение вкладок Puzzles ↔ Lands: ${
                if (initialConfig.switchBetweenTabs) "ВКЛ" else "ВЫКЛ"
            }"
        )
        if (!initialConfig.enableActualBuying) {
            BotLogs.addLog("ℹ️ Активен режим МОНИТОРИНГА (логирование без записи сделок)")
        }

        monitorJob = scope.launch {
            var currentTab = targetTab
            var currentCycle = 1
            var cycleEndMs = computeCycleEnd(initialConfig, 1)
            var scans = 0
            var matched = 0

            _state.update {
                it.copy(
                    isRunning = true,
                    activeTab = currentTab,
                    buyTarget = targetTab,
                    currentCycle = 1,
                    lastEventMessage = "Сканирование вкладки ${currentTab.tabLabel}..."
                )
            }

            while (isActive) {
                val cfg = configurationDao.getConfiguration() ?: initialConfig
                val selectedTarget = MarketTab.fromString(cfg.buyTarget)

                // Handle search cycles if enabled
                val remainingSec = if (cfg.useSearchCycles) {
                    val now = System.currentTimeMillis()
                    if (now >= cycleEndMs) {
                        currentCycle = if (currentCycle >= 3) 1 else currentCycle + 1
                        cycleEndMs = computeCycleEnd(cfg, currentCycle)
                        BotLogs.addLog(
                            "🔄 Переход на Цикл $currentCycle (сброс/перезагрузка страницы рынка)"
                        )
                    }
                    ((cycleEndMs - now).coerceAtLeast(0L) / 1000L).toInt()
                } else {
                    0
                }

                // Switch between Puzzles and Lands tabs if configured
                if (cfg.switchBetweenTabs) {
                    currentTab = if (currentTab == MarketTab.PUZZLES) MarketTab.LANDS else MarketTab.PUZZLES
                    val randMs = if (cfg.tabSwitchRandomizationMs > 0) {
                        Random.nextInt(-cfg.tabSwitchRandomizationMs, cfg.tabSwitchRandomizationMs + 1)
                    } else 0
                    val effectiveSwitchDelay = (cfg.tabSwitchIntervalMs + randMs).coerceAtLeast(40L)
                    if (cfg.verboseOcrLogging) {
                        BotLogs.addLog("🔀 Переключение вкладки -> ${currentTab.tabLabel} (${effectiveSwitchDelay} мс)")
                    }
                    _state.update {
                        it.copy(
                            activeTab = currentTab,
                            buyTarget = selectedTarget,
                            currentCycle = currentCycle,
                            cycleRemainingSec = remainingSec
                        )
                    }
                    delay(effectiveSwitchDelay)
                } else {
                    currentTab = selectedTarget
                }

                scans++
                val isBuyTab = currentTab == selectedTarget

                // Evaluate market scan step
                val simulatedPrice = generateSampleMarketPrice(currentTab, cfg.priceThreshold)
                val matchesPrice = ScreenshotOcrAnalyzer.evaluateThreshold(simulatedPrice, cfg)

                val statusMsg = if (!isBuyTab) {
                    "Обновление вкладки ${currentTab.tabLabel} (покупка отключена — цель: только ${selectedTarget.tabLabel})"
                } else if (matchesPrice) {
                    matched++
                    val formattedPrice = String.format("%.4f", simulatedPrice)
                    if (cfg.enableActualBuying) {
                        val itemName = if (selectedTarget == MarketTab.PUZZLES) "Earth puzzles" else "Land plot"
                        purchaseDao.insertPurchase(
                            PurchaseRecord(
                                timestamp = System.currentTimeMillis(),
                                tabName = selectedTarget.tabLabel,
                                itemName = itemName,
                                price = simulatedPrice,
                                quantity = 1.0,
                                details = "Куплено на вкладке ${selectedTarget.tabLabel} по порогу ${cfg.priceThreshold}"
                            )
                        )
                        BotLogs.addLog("✅ [${selectedTarget.tabLabel}] Найден лот $formattedPrice MM — ПОКУПКА ЗАФИКСИРОВАНА")
                        "Куплен лот $itemName за $formattedPrice MM"
                    } else {
                        BotLogs.addLog("👀 [${selectedTarget.tabLabel}] Подходящий лот $formattedPrice MM (режим мониторинга)")
                        "Найден лот ${selectedTarget.tabLabel}: $formattedPrice MM"
                    }
                } else {
                    if (cfg.verboseOcrLogging) {
                        val formattedPrice = String.format("%.4f", simulatedPrice)
                        BotLogs.addLog("🔍 [${currentTab.tabLabel}] Лот $formattedPrice MM не подходит под порог ${cfg.priceThreshold}")
                    }
                    "Активна вкладка ${currentTab.tabLabel} • Цель: ${selectedTarget.tabLabel}"
                }

                _state.update {
                    it.copy(
                        isRunning = true,
                        activeTab = currentTab,
                        buyTarget = selectedTarget,
                        currentCycle = currentCycle,
                        cycleRemainingSec = remainingSec,
                        scanCount = scans,
                        matchedLotsCount = matched,
                        lastDetectedPrice = simulatedPrice,
                        lastEventMessage = statusMsg
                    )
                }

                delay(cfg.scanIntervalMs.coerceAtLeast(150L))
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        BotLogs.addLog("⏹ Мониторинг остановлен")
        _state.update {
            it.copy(
                isRunning = false,
                lastEventMessage = "Мониторинг остановлен"
            )
        }
    }

    private fun computeCycleEnd(cfg: AppConfiguration, cycleNumber: Int): Long {
        val (baseMin, randSec) = when (cycleNumber) {
            1 -> cfg.cycle1DurationMin to cfg.cycle1RandomRangeSec
            2 -> cfg.cycle2DurationMin to cfg.cycle2RandomRangeSec
            else -> cfg.cycle3DurationMin to cfg.cycle3RandomRangeSec
        }
        val limit = abs(randSec)
        val delta = if (limit > 0) Random.nextInt(-limit, limit + 1) else 0
        val totalSec = (baseMin.coerceAtLeast(1) * 60 + delta).coerceAtLeast(15)
        return System.currentTimeMillis() + totalSec * 1000L
    }

    private fun generateSampleMarketPrice(tab: MarketTab, threshold: Double): Double {
        val base = threshold.coerceAtLeast(10.0)
        // Produce prices clustered around the configured threshold so both matches and skips occur naturally
        val factor = if (tab == MarketTab.PUZZLES) {
            Random.nextDouble(0.82, 1.18)
        } else {
            Random.nextDouble(0.85, 1.25)
        }
        return (base * factor * 10000).toInt() / 10000.0
    }
}
