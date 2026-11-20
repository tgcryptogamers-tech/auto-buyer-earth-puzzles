package com.example

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.MediaProjectionHelper
import com.example.service.OverlayControlService
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogsAndHistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

private enum class AppDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD("Панель", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    SETTINGS("Настройки", Icons.Filled.Tune, Icons.Outlined.Tune, "nav_settings"),
    LOGS("Логи", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_logs")
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, OverlayControlService::class.java).apply {
                action = OverlayControlService.ACTION_START_PROJECTION
                putExtra(OverlayControlService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(OverlayControlService.EXTRA_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            try {
                val projection = mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
                if (projection != null) {
                    MediaProjectionHelper.initProjection(projection, applicationContext)
                    viewModel.checkPermissions()
                    Toast.makeText(this, "Захват экрана для OCR включён!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка MediaProjection: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Для чтения вкладок и цен в игре нужен захват экрана.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                EarthPuzzlesApp(
                    viewModel = viewModel,
                    onOpenAccessibilitySettings = {
                        try {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            Toast.makeText(this, "Найдите 'Earth Puzzles' и включите его", Toast.LENGTH_LONG).show()
                        } catch (_: Exception) {
                            Toast.makeText(this, "Не удалось открыть настройки Accessibility", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenOverlaySettings = {
                        try {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        } catch (_: Exception) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    },
                    onRequestScreenCapture = {
                        val intent = mediaProjectionManager.createScreenCaptureIntent()
                        screenCaptureLauncher.launch(intent)
                    },
                    onLaunchOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            Toast.makeText(this, "Сначала выдайте разрешение Overlay!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.startOverlayService()
                        }
                    },
                    onToggleMonitoring = {
                        val message = viewModel.toggleMonitoringAndOverlay()
                        if (message != null) {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissions()
    }
}

@Composable
private fun EarthPuzzlesApp(
    viewModel: MainViewModel,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onToggleMonitoring: () -> Unit
) {
    val config by viewModel.configuration.collectAsStateWithLifecycle()
    val isAccessibilityEnabled by viewModel.accessibilityEnabled.collectAsStateWithLifecycle()
    val isOverlayGranted by viewModel.overlayGranted.collectAsStateWithLifecycle()
    val isScreenCaptureAuthorized by viewModel.screenCaptureAuthorized.collectAsStateWithLifecycle()
    val isOverlayRunning by viewModel.overlayRunning.collectAsStateWithLifecycle()
    val cooldownRemainingSec by viewModel.cooldownRemainingSec.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.DASHBOARD) }

    if (currentDestination != AppDestination.DASHBOARD) {
        BackHandler {
            currentDestination = AppDestination.DASHBOARD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        AppDestination.entries.forEach { dest ->
                            val selected = currentDestination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                when (currentDestination) {
                    AppDestination.DASHBOARD -> {
                        DashboardScreen(
                            config = config,
                            logs = logs,
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            isOverlayGranted = isOverlayGranted,
                            isScreenCaptureAuthorized = isScreenCaptureAuthorized,
                            isOverlayRunning = isOverlayRunning,
                            cooldownRemainingSec = cooldownRemainingSec,
                            purchasesCount = purchases.size,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onOpenOverlaySettings = onOpenOverlaySettings,
                            onRequestScreenCapture = onRequestScreenCapture,
                            onLaunchOverlay = onLaunchOverlay,
                            onStopOverlay = viewModel::stopOverlayService,
                            onToggleMonitoring = onToggleMonitoring,
                            onResetCooldown = viewModel::resetCooldown,
                            onToggleBuyTab = viewModel::toggleBuyTab,
                            onSelectBothBuyTabs = viewModel::selectBothBuyTabs,
                            onToggleTabSwitching = viewModel::setTabSwitchingEnabled,
                            onToggleActualBuying = { enabled ->
                                viewModel.updateConfiguration { it.copy(enableActualBuying = enabled) }
                            },
                            onClearLogs = viewModel::clearLogs,
                            onOpenSettings = { currentDestination = AppDestination.SETTINGS }
                        )
                    }

                    AppDestination.SETTINGS -> {
                        SettingsScreen(
                            config = config,
                            onUpdateConfig = viewModel::updateConfiguration,
                            onToggleBuyTab = viewModel::toggleBuyTab,
                            onSelectBothBuyTabs = viewModel::selectBothBuyTabs,
                            onToggleCalibration = viewModel::setCalibrationEnabled,
                            onResetCoordinates = viewModel::resetCoordinates
                        )
                    }

                    AppDestination.LOGS -> {
                        LogsAndHistoryScreen(
                            logs = logs,
                            purchases = purchases,
                            onClearLogs = viewModel::clearLogs,
                            onClearPurchases = viewModel::clearPurchases,
                            onDeletePurchase = viewModel::deletePurchase
                        )
                    }
                }
            }
        }
    }
}
