package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppConfiguration
import com.example.data.MarketTab
import com.example.engine.BotLogs
import com.example.ui.components.BuyTargetSelector
import com.example.ui.components.LogsTextModalDialog
import com.example.ui.components.MarketTabsStripPreview
import com.example.ui.components.SectionCard
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun DashboardScreen(
    config: AppConfiguration,
    logs: List<String>,
    isAccessibilityEnabled: Boolean,
    isOverlayGranted: Boolean,
    isScreenCaptureAuthorized: Boolean,
    isOverlayRunning: Boolean,
    cooldownRemainingSec: Long,
    purchasesCount: Int,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestScreenCapture: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onToggleMonitoring: () -> Unit,
    onResetCooldown: () -> Unit,
    onToggleBuyTab: (MarketTab) -> Unit,
    onSelectBothBuyTabs: () -> Unit,
    onToggleTabSwitching: (Boolean) -> Unit,
    onToggleActualBuying: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogsTextDialog by remember { mutableStateOf(false) }

    if (showLogsTextDialog) {
        LogsTextModalDialog(
            logsText = BotLogs.getAllLogsText(),
            onDismiss = { showLogsTextDialog = false },
            onCopy = {
                val copied = BotLogs.copyToClipboard(context)
                Toast.makeText(
                    context,
                    if (copied) "✅ Логи скопированы в буфер" else "Выделите текст выше (Ctrl+A) и скопируйте",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onShare = {
                val text = BotLogs.getAllLogsText().ifBlank { "Логи пусты" }
                BotLogs.shareText(context, "Логи Earth Puzzles", text)
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_market_banner),
                        contentDescription = "Earth Puzzles Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (config.autoBuyEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (config.autoBuyEnabled) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.secondary
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (config.autoBuyEnabled) "БОТ АКТИВЕН" else "ОЖИДАНИЕ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Покупок: $purchasesCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Earth Puzzles & Lands",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Авто-покупка в игре: переключение Puzzles ↔ Lands",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // System Permissions Section
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Разрешения",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Разрешения для работы в игре",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    PermissionRow(
                        title = "Accessibility Service (Клики в игре)",
                        description = "Нужно для переключения вкладок Puzzles/Lands и кликов по лотам.",
                        isGranted = isAccessibilityEnabled,
                        onClick = onOpenAccessibilitySettings,
                        testTag = "perm_accessibility_row"
                    )

                    PermissionRow(
                        title = "Поверх других приложений (Кружок)",
                        description = "Отображает плавающий кружок управления поверх окна игры.",
                        isGranted = isOverlayGranted,
                        onClick = onOpenOverlaySettings,
                        testTag = "perm_overlay_row"
                    )

                    PermissionRow(
                        title = "Захват экрана (OCR Screen Capture)",
                        description = "Нужно для распознавания цен и вкладок на экране игры.",
                        isGranted = isScreenCaptureAuthorized,
                        onClick = onRequestScreenCapture,
                        testTag = "perm_capture_row"
                    )
                }
            }

            // Floating Bubble & Start/Stop Automation Controls
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Кружок",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Плавающий кружок и запуск",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (isOverlayRunning) "Кружок: ВКЛ" else "Скрыт",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverlayRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Короткое нажатие на кружок запускает/ставит на паузу бота в игре; долгое нажатие открывает панель логов.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onLaunchOverlay,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("launch_overlay_btn"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("ПОКАЗАТЬ КРУЖОК", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = onStopOverlay,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_overlay_btn"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("СКРЫТЬ КРУЖОК", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    if (cooldownRemainingSec > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Пауза после покупки: ${cooldownRemainingSec}с",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            OutlinedButton(onClick = onResetCooldown) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сброс")
                            }
                        }
                    }

                    Button(
                        onClick = onToggleMonitoring,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("toggle_monitor_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (config.autoBuyEnabled) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = if (config.autoBuyEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (config.autoBuyEnabled) "ОСТАНОВИТЬ МОНИТОРИНГ" else "ЗАПУСТИТЬ МОНИТОРИНГ",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Quick Tabs & Buy Target Configuration
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Что покупаем в игре: ${config.buyTargetSummary()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Можно выбрать Puzzles, Lands или сразу обе вкладки",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_settings_shortcut")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Настройки")
                        }
                    }

                    BuyTargetSelector(
                        buyPuzzles = config.buyPuzzles,
                        buyLands = config.buyLands,
                        onToggleTab = onToggleBuyTab,
                        onSelectBoth = onSelectBothBuyTabs
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onToggleTabSwitching(!config.switchBetweenTabs) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Переключение вкладок Puzzles ↔ Lands",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (config.buyPuzzles && config.buyLands) {
                                        "Поочерёдный скан и покупка на обеих вкладках (${config.tabSwitchIntervalMs} мс)"
                                    } else {
                                        "Обновляет рынок в игре (${config.tabSwitchIntervalMs} мс)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = config.switchBetweenTabs,
                            onCheckedChange = onToggleTabSwitching,
                            modifier = Modifier.testTag("dashboard_tab_switch_toggle")
                        )
                    }

                    MarketTabsStripPreview(
                        buyPuzzles = config.buyPuzzles,
                        buyLands = config.buyLands,
                        switchBetweenTabs = config.switchBetweenTabs
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onToggleActualBuying(!config.enableActualBuying) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = if (config.enableActualBuying) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Автоматическая покупка",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (config.enableActualBuying) {
                                        "ВКЛ — бот нажимает на лот и кнопку Buy"
                                    } else {
                                        "ВЫКЛ — только логирование без покупок"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = config.enableActualBuying,
                            onCheckedChange = onToggleActualBuying,
                            modifier = Modifier.testTag("dashboard_actual_buying_toggle")
                        )
                    }
                }
            }

            // Live Logs Terminal right on Dashboard
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Лог",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Лог работы (${logs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    val text = BotLogs.getAllLogsText().ifBlank { "Логи пусты" }
                                    BotLogs.shareText(context, "Логи Earth Puzzles", text)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dashboard_share_logs")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Поделиться")
                            }
                            IconButton(
                                onClick = onClearLogs,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dashboard_clear_logs")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Очистить")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val text = BotLogs.getAllLogsText()
                                if (text.isBlank()) {
                                    Toast.makeText(context, "Лог пока пуст", Toast.LENGTH_SHORT).show()
                                } else {
                                    val copied = BotLogs.copyToClipboard(context)
                                    if (copied) {
                                        Toast.makeText(context, "✅ Скопировано строк: ${logs.size}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Откройте окно текста для ручного копирования", Toast.LENGTH_SHORT).show()
                                        showLogsTextDialog = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_copy_logs"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("КОПИРОВАТЬ", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { showLogsTextDialog = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("dashboard_view_logs_text"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("ТЕКСТ", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = "Логи пока пусты. Запустите кружок или мониторинг.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = logs.takeLast(30).reversed().joinToString("\n"),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (isGranted) "Выдано" else "Требуется",
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
    }
}
