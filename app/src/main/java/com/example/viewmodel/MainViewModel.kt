package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppConfiguration
import com.example.data.AppDatabase
import com.example.data.MarketTab
import com.example.data.PurchaseRecord
import com.example.engine.BotLogs
import com.example.service.LootBuyerAccessibilityService
import com.example.service.MediaProjectionHelper
import com.example.service.OverlayControlService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val configDao = db.configurationDao()
    private val purchaseDao = db.purchaseDao()

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

    val logs: StateFlow<List<String>> = BotLogs.logs

    private val _accessibilityEnabled = MutableStateFlow(false)
    val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

    private val _overlayGranted = MutableStateFlow(false)
    val overlayGranted: StateFlow<Boolean> = _overlayGranted.asStateFlow()

    private val _screenCaptureAuthorized = MutableStateFlow(false)
    val screenCaptureAuthorized: StateFlow<Boolean> = _screenCaptureAuthorized.asStateFlow()

    private val _overlayRunning = MutableStateFlow(false)
    val overlayRunning: StateFlow<Boolean> = _overlayRunning.asStateFlow()

    private val _cooldownRemainingSec = MutableStateFlow(0L)
    val cooldownRemainingSec: StateFlow<Long> = _cooldownRemainingSec.asStateFlow()

    init {
        viewModelScope.launch {
            if (configDao.getConfiguration() == null) {
                configDao.saveConfiguration(AppConfiguration())
            }
            checkPermissions()
            BotLogs.addLog("⚙️ Конфигурация готова: вкладки Puzzles ↔ Lands.")
        }

        viewModelScope.launch {
            while (isActive) {
                _overlayRunning.value = OverlayControlService.instance != null
                _screenCaptureAuthorized.value = MediaProjectionHelper.hasProjection()
                _cooldownRemainingSec.value = LootBuyerAccessibilityService.getCooldownRemainingMs() / 1000L
                delay(700L)
            }
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        _overlayGranted.value = Settings.canDrawOverlays(context)
        _screenCaptureAuthorized.value = MediaProjectionHelper.hasProjection()
        _overlayRunning.value = OverlayControlService.instance != null

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val expectedComponent = ComponentName(context, LootBuyerAccessibilityService::class.java).flattenToString()
        val shortName = "${context.packageName}/.service.LootBuyerAccessibilityService"
        _accessibilityEnabled.value = enabledServices.contains(expectedComponent) ||
            enabledServices.contains(shortName) ||
            LootBuyerAccessibilityService.isServiceRunning
    }

    fun updateConfiguration(transform: (AppConfiguration) -> AppConfiguration) {
        viewModelScope.launch {
            val current = configDao.getConfiguration() ?: AppConfiguration()
            configDao.saveConfiguration(transform(current))
        }
    }

    fun setBuyTarget(target: MarketTab) {
        updateConfiguration { it.copy(buyTarget = target.tabLabel) }
        BotLogs.addLog("🎯 Выбрана цель покупки: ТОЛЬКО ${target.tabLabel}")
    }

    fun setTabSwitchingEnabled(enabled: Boolean) {
        updateConfiguration { it.copy(switchBetweenTabs = enabled) }
        BotLogs.addLog("🔀 Переключение вкладок Puzzles ↔ Lands: ${if (enabled) "ВКЛ" else "ВЫКЛ"}")
    }

    fun setCalibrationEnabled(enabled: Boolean) {
        updateConfiguration { it.copy(isCalibrationEnabled = enabled) }
    }

    fun startOverlayService(): Boolean {
        val context = getApplication<Application>()
        if (!Settings.canDrawOverlays(context)) return false
        val intent = Intent(context, OverlayControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _overlayRunning.value = true
        return true
    }

    fun stopOverlayService() {
        val context = getApplication<Application>()
        context.stopService(Intent(context, OverlayControlService::class.java))
        _overlayRunning.value = false
    }

    fun toggleMonitoringAndOverlay(): String? {
        val context = getApplication<Application>()
        val current = configuration.value
        val nextState = !current.autoBuyEnabled

        if (nextState) {
            if (!Settings.canDrawOverlays(context)) {
                return "Сначала выдайте разрешение «Поверх других приложений» (Overlay)!"
            }
            startOverlayService()

            val accService = LootBuyerAccessibilityService.instance
            if (accService == null) {
                return "Кружок запущен! Включите Accessibility Service 'Earth Puzzles' для кликов в игре."
            }

            viewModelScope.launch {
                val cur = configDao.getConfiguration() ?: AppConfiguration()
                configDao.saveConfiguration(cur.copy(autoBuyEnabled = true))
                accService.startAutomation()
            }
            return null
        } else {
            viewModelScope.launch {
                val cur = configDao.getConfiguration() ?: AppConfiguration()
                configDao.saveConfiguration(cur.copy(autoBuyEnabled = false))
                LootBuyerAccessibilityService.instance?.stopAutomation()
            }
            return null
        }
    }

    fun resetCooldown() {
        LootBuyerAccessibilityService.resetCooldown()
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
}
