package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppConfiguration
import com.example.data.AppDatabase
import com.example.data.MarketTab
import com.example.data.PurchaseRecord
import com.example.engine.BotLogs
import com.example.engine.MarketMonitorEngine
import com.example.engine.MarketMonitorState
import com.example.engine.ScreenshotAnalysisResult
import com.example.engine.ScreenshotOcrAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OcrAnalysisUiState {
    data object Idle : OcrAnalysisUiState
    data object Analyzing : OcrAnalysisUiState
    data class Success(val result: ScreenshotAnalysisResult) : OcrAnalysisUiState
    data class Error(val message: String) : OcrAnalysisUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val configDao = db.configurationDao()
    private val purchaseDao = db.purchaseDao()

    private val monitorEngine = MarketMonitorEngine(configDao, purchaseDao)

    val configuration: StateFlow<AppConfiguration> = configDao.getConfigurationFlow()
        .map { it ?: AppConfiguration() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppConfiguration()
        )

    val purchases: StateFlow<List<PurchaseRecord>> = purchaseDao.getAllPurchasesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val monitorState: StateFlow<MarketMonitorState> = monitorEngine.state
    val logs: StateFlow<List<String>> = BotLogs.logs

    private val _ocrState = MutableStateFlow<OcrAnalysisUiState>(OcrAnalysisUiState.Idle)
    val ocrState: StateFlow<OcrAnalysisUiState> = _ocrState.asStateFlow()

    init {
        viewModelScope.launch {
            if (configDao.getConfiguration() == null) {
                configDao.saveConfiguration(AppConfiguration())
            }
            BotLogs.addLog("⚙️ Конфигурация загружена: вкладки Puzzles ↔ Lands готовы")
        }
    }

    fun updateConfiguration(transform: (AppConfiguration) -> AppConfiguration) {
        viewModelScope.launch {
            val current = configDao.getConfiguration() ?: AppConfiguration()
            val updated = transform(current)
            configDao.saveConfiguration(updated)
        }
    }

    fun setBuyTarget(target: MarketTab) {
        updateConfiguration { it.copy(buyTarget = target.tabLabel) }
        BotLogs.addLog("🎯 Выбрана цель покупки: только ${target.tabLabel}")
    }

    fun setTabSwitchingEnabled(enabled: Boolean) {
        updateConfiguration { it.copy(switchBetweenTabs = enabled) }
        BotLogs.addLog("🔀 Переключение между Puzzles и Lands: ${if (enabled) "ВКЛ" else "ВЫКЛ"}")
    }

    fun toggleMonitor() {
        if (monitorState.value.isRunning) {
            monitorEngine.stop()
        } else {
            monitorEngine.start(configuration.value)
        }
    }

    fun analyzeScreenshot(uri: Uri) {
        viewModelScope.launch {
            _ocrState.value = OcrAnalysisUiState.Analyzing
            BotLogs.addLog("🖼 Запуск ML Kit OCR анализа выбранного скриншота...")
            val result = ScreenshotOcrAnalyzer.analyzeUri(
                context = getApplication(),
                uri = uri,
                config = configuration.value
            )
            result.onSuccess { analysis ->
                _ocrState.value = OcrAnalysisUiState.Success(analysis)
                val tabsSummary = analysis.detectedTabs.joinToString(", ") {
                    "${it.tab.tabLabel}(${it.centerX.toInt()},${it.centerY.toInt()})"
                }.ifEmpty { "не найдены" }
                BotLogs.addLog("✅ OCR завершен: вкладки [$tabsSummary], найдено цен: ${analysis.lotCandidates.size}")
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Ошибка распознавания текста"
                _ocrState.value = OcrAnalysisUiState.Error(msg)
                BotLogs.addLog("❌ Ошибка OCR: $msg")
            }
        }
    }

    fun applyOcrCoordinates(analysis: ScreenshotAnalysisResult) {
        val puzzles = analysis.detectedTabs.firstOrNull { it.tab == MarketTab.PUZZLES }
        val lands = analysis.detectedTabs.firstOrNull { it.tab == MarketTab.LANDS }
        updateConfiguration { cfg ->
            cfg.copy(
                calibratedPuzzlesX = puzzles?.centerX ?: cfg.calibratedPuzzlesX,
                calibratedPuzzlesY = puzzles?.centerY ?: cfg.calibratedPuzzlesY,
                calibratedLandsX = lands?.centerX ?: cfg.calibratedLandsX,
                calibratedLandsY = lands?.centerY ?: cfg.calibratedLandsY
            )
        }
        BotLogs.addLog("📍 Координаты вкладок Puzzles/Lands обновлены из скриншота")
    }

    fun clearOcrState() {
        _ocrState.value = OcrAnalysisUiState.Idle
    }

    fun resetCoordinates() {
        updateConfiguration {
            it.copy(
                calibratedPuzzlesX = -1f,
                calibratedPuzzlesY = -1f,
                calibratedLandsX = -1f,
                calibratedLandsY = -1f,
                calibratedConfirmX = -1f,
                calibratedConfirmY = -1f
            )
        }
        BotLogs.addLog("🧹 Координаты калибровки сброшены")
    }

    fun clearPurchases() {
        viewModelScope.launch {
            purchaseDao.clearAllPurchases()
            BotLogs.addLog("🗑 История покупок очищена")
        }
    }

    fun deletePurchase(id: Long) {
        viewModelScope.launch {
            purchaseDao.deletePurchaseById(id)
        }
    }

    fun clearLogs() {
        BotLogs.clearLogs()
    }

    override fun onCleared() {
        monitorEngine.stop()
        super.onCleared()
    }
}
