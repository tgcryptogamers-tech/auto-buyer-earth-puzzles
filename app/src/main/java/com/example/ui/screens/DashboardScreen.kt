package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppConfiguration
import com.example.data.MarketTab
import com.example.engine.MarketMonitorState
import com.example.ui.components.BuyTargetSelector
import com.example.ui.components.MarketTabsStripPreview
import com.example.ui.components.SectionCard
import com.example.viewmodel.OcrAnalysisUiState

@Composable
fun DashboardScreen(
    config: AppConfiguration,
    monitorState: MarketMonitorState,
    ocrState: OcrAnalysisUiState,
    purchasesCount: Int,
    onSelectBuyTarget: (MarketTab) -> Unit,
    onToggleTabSwitching: (Boolean) -> Unit,
    onToggleMonitor: () -> Unit,
    onPickScreenshot: () -> Unit,
    onApplyOcrCoords: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTarget = MarketTab.fromString(config.buyTarget)

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
                        .height(164.dp)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_market_banner),
                        contentDescription = "Earth Puzzles Market Banner",
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
                                color = if (monitorState.isRunning) {
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
                                                if (monitorState.isRunning) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.secondary
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (monitorState.isRunning) "МОНИТОРИНГ АКТИВЕН" else "ОЖИДАНИЕ",
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
                                    text = "Сделок: $purchasesCount",
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
                                text = "Бот-конфигуратор и OCR-монитор вкладок Puzzles ↔ Lands",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Tabs & Buy Target Configuration Card
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Выбор цели покупки",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Покупать только пазлы (Puzzles) или только землю (Lands)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_settings_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Открыть настройки",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Все", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    BuyTargetSelector(
                        selectedTarget = selectedTarget,
                        onSelectTarget = onSelectBuyTarget
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                contentDescription = "Переключение вкладок",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Переключение Puzzles ↔ Lands",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Обновляет рынок каждые ${config.tabSwitchIntervalMs} мс (±${config.tabSwitchRandomizationMs} мс)",
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
                        activeTab = if (monitorState.isRunning) monitorState.activeTab else selectedTarget,
                        buyTarget = selectedTarget,
                        switchBetweenTabs = config.switchBetweenTabs
                    )
                }
            }

            // Live Monitor Runner & Metrics
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Мониторинг и циклы",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (config.usePriceThreshold) {
                                val op = if (config.isLessThanOperator) "≤" else "≥"
                                "Порог: $op ${config.priceThreshold} MM"
                            } else {
                                "Порог: отключен"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "Сканов",
                            value = monitorState.scanCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Найдено",
                            value = monitorState.matchedLotsCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = if (config.useSearchCycles) "Цикл ${monitorState.currentCycle}" else "Режим",
                            value = if (config.useSearchCycles && monitorState.isRunning) {
                                "${monitorState.cycleRemainingSec}с"
                            } else if (config.enableActualBuying) {
                                "Покупка"
                            } else {
                                "Лог"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = monitorState.lastEventMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onToggleMonitor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("toggle_monitor_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (monitorState.isRunning) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = if (monitorState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (monitorState.isRunning) "ОСТАНОВИТЬ МОНИТОРИНГ" else "ЗАПУСТИТЬ МОНИТОРИНГ",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Zero-permission Photo Picker OCR Scanner
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "OCR анализ",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OCR-анализ скриншота рынка",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Распознает вкладки Puzzles и Lands, координаты и цены лотов (ML Kit)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onPickScreenshot,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("pick_screenshot_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выбрать скриншот игры", style = MaterialTheme.typography.labelLarge)
                    }

                    when (ocrState) {
                        is OcrAnalysisUiState.Idle -> Unit
                        is OcrAnalysisUiState.Analyzing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Распознавание вкладок и цен...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        is OcrAnalysisUiState.Error -> {
                            Text(
                                text = "Ошибка OCR: ${ocrState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        is OcrAnalysisUiState.Success -> {
                            val res = ocrState.result
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Размер: ${res.imageWidth}×${res.imageHeight} | Активная вкладка: ${res.activeTabDetected?.tabLabel ?: "не определена"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                res.detectedTabs.forEach { tab ->
                                    Text(
                                        text = "• ${tab.tab.tabLabel}: (${tab.centerX.toInt()}, ${tab.centerY.toInt()})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (res.showedSummary != null) {
                                    Text(
                                        text = "Статус экрана: ${res.showedSummary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Text(
                                    text = "Найдено числовых цен: ${res.lotCandidates.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                res.lotCandidates.take(4).forEach { lot ->
                                    Text(
                                        text = "  ${String.format("%.4f", lot.parsedPrice)} MM (${if (lot.matchesThreshold) "подходит" else "выше/ниже порога"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lot.matchesThreshold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (res.detectedTabs.isNotEmpty()) {
                                    Button(
                                        onClick = onApplyOcrCoords,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("apply_ocr_coords_button"),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text("Сохранить координаты Puzzles / Lands")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Creator Program Compliance Note
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сборка соответствует Google Play Creator Program (без AccessibilityService и системных оверлеев поверх сторонних приложений).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}
