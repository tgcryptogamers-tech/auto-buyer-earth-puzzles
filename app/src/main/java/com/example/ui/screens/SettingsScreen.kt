package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AppConfiguration
import com.example.data.MarketTab
import com.example.ui.components.BuyTargetSelector
import com.example.ui.components.MarketTabsStripPreview
import com.example.ui.components.SectionCard
import kotlin.math.abs

@Composable
fun SettingsScreen(
    config: AppConfiguration,
    onUpdateConfig: ((AppConfiguration) -> AppConfiguration) -> Unit,
    onToggleBuyTab: (MarketTab) -> Unit,
    onSelectBothBuyTabs: () -> Unit,
    onToggleCalibration: (Boolean) -> Unit,
    onResetCoordinates: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tabSwitchSlider by remember {
        mutableFloatStateOf(config.tabSwitchIntervalMs.toFloat().coerceIn(40f, 1500f))
    }
    var isDraggingTabSlider by remember { mutableStateOf(false) }

    var scanIntervalSlider by remember {
        mutableFloatStateOf(config.scanIntervalMs.toFloat().coerceIn(0f, 1500f))
    }
    var isDraggingScanSlider by remember { mutableStateOf(false) }

    LaunchedEffect(config.tabSwitchIntervalMs) {
        if (!isDraggingTabSlider) {
            val target = config.tabSwitchIntervalMs.toFloat().coerceIn(40f, 1500f)
            if (abs(tabSwitchSlider - target) >= 1f) {
                tabSwitchSlider = target
            }
        }
    }

    LaunchedEffect(tabSwitchSlider) {
        val desired = tabSwitchSlider.toLong().coerceIn(40L, 1500L)
        if (desired != config.tabSwitchIntervalMs) {
            kotlinx.coroutines.delay(90L)
            onUpdateConfig { it.copy(tabSwitchIntervalMs = desired) }
        }
    }

    LaunchedEffect(config.scanIntervalMs) {
        if (!isDraggingScanSlider) {
            val target = config.scanIntervalMs.toFloat().coerceIn(0f, 1500f)
            if (abs(scanIntervalSlider - target) >= 1f) {
                scanIntervalSlider = target
            }
        }
    }

    LaunchedEffect(scanIntervalSlider) {
        val desired = scanIntervalSlider.toLong().coerceIn(0L, 1500L)
        if (desired != config.scanIntervalMs) {
            kotlinx.coroutines.delay(90L)
            onUpdateConfig { it.copy(scanIntervalMs = desired) }
        }
    }

    var thresholdInput by remember(config.priceThreshold) {
        mutableStateOf(config.priceThreshold.toString())
    }
    var tabRandInput by remember(config.tabSwitchRandomizationMs) {
        mutableStateOf(config.tabSwitchRandomizationMs.toString())
    }
    var clickRandInput by remember(config.clickRandomizationRadiusPx) {
        mutableStateOf(config.clickRandomizationRadiusPx.toString())
    }
    var cycle1Min by remember(config.cycle1DurationMin) { mutableStateOf(config.cycle1DurationMin.toString()) }
    var cycle2Min by remember(config.cycle2DurationMin) { mutableStateOf(config.cycle2DurationMin.toString()) }
    var cycle3Min by remember(config.cycle3DurationMin) { mutableStateOf(config.cycle3DurationMin.toString()) }
    var cycle1Rand by remember(config.cycle1RandomRangeSec) { mutableStateOf(config.cycle1RandomRangeSec.toString()) }
    var cycle2Rand by remember(config.cycle2RandomRangeSec) { mutableStateOf(config.cycle2RandomRangeSec.toString()) }
    var cycle3Rand by remember(config.cycle3RandomRangeSec) { mutableStateOf(config.cycle3RandomRangeSec.toString()) }

    LaunchedEffect(config.priceThreshold) {
        val parsed = thresholdInput.toDoubleOrNull()
        if (parsed != config.priceThreshold) {
            thresholdInput = config.priceThreshold.toString()
        }
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
            item {
                Text(
                    text = "Настройки бота",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // 1. Tabs & Buy Target (Puzzles vs Lands)
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Вкладки",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Вкладки игры: Puzzles и Lands",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Можно выбрать только Puzzles, только Lands или сразу обе вкладки для параллельного выкупа.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    BuyTargetSelector(
                        buyPuzzles = config.buyPuzzles,
                        buyLands = config.buyLands,
                        onToggleTab = onToggleBuyTab,
                        onSelectBoth = onSelectBothBuyTabs
                    )

                    SettingsSwitchRow(
                        title = "Переключение вкладок Puzzles ↔ Lands в игре",
                        subtitle = when {
                            config.buyPuzzles && config.buyLands ->
                                "Бот поочерёдно переключает Puzzles и Lands и покупает подходящие лоты на обеих вкладках"
                            config.switchBetweenTabs ->
                                "Бот переключается между Puzzles и Lands для обновления рынка, но покупает ${config.buyTargetSummary()}"
                            else ->
                                "Оставаться только на вкладке ${if (config.buyLands) "Lands" else "Puzzles"}"
                        },
                        checked = config.switchBetweenTabs,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(switchBetweenTabs = checked) }
                        },
                        testTag = "settings_tab_switch_toggle"
                    )

                    MarketTabsStripPreview(
                        buyPuzzles = config.buyPuzzles,
                        buyLands = config.buyLands,
                        switchBetweenTabs = config.switchBetweenTabs,
                        onToggleTabSwitching = { checked ->
                            onUpdateConfig { it.copy(switchBetweenTabs = checked) }
                        }
                    )
                }
            }

            // 2. On-Screen Game Calibration via Floating Bubble
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Калибровка",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Калибровка вкладок в игре",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Включите, сверните бот в игру и нажмите на жёлтый кружок, чтобы указать Puzzles и Lands прямо в окне игры.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "Режим калибровки кружком",
                        subtitle = if (config.isCalibrationEnabled) {
                            "ГОТОВ! Перейдите в игру и нажмите жёлтый кружок"
                        } else {
                            "Нажмите, чтобы задать координаты Puzzles и Lands поверх игры"
                        },
                        checked = config.isCalibrationEnabled,
                        onCheckedChange = onToggleCalibration,
                        testTag = "settings_calibration_toggle"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatCoord("Вкладка Puzzles", config.calibratedPuzzlesX, config.calibratedPuzzlesY),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatCoord("Вкладка Lands", config.calibratedLandsX, config.calibratedLandsY),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatCoord("Кнопка Buy/Confirm", config.calibratedConfirmX, config.calibratedConfirmY),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedButton(
                        onClick = onResetCoordinates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_coordinates_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Сбросить координаты (авто-OCR)")
                    }
                }
            }

            // 3. Price Threshold & Buy Execution Mode
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = "Порог цены",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Порог цены и реальная покупка",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SettingsSwitchRow(
                        title = "Выполнять автоматическую покупку",
                        subtitle = if (config.enableActualBuying) "Режим ПОКУПКИ (клики по лоту и Buy активны)" else "Режим МОНИТОРИНГА (только логирование без кликов покупки)",
                        checked = config.enableActualBuying,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(enableActualBuying = checked) }
                        },
                        testTag = "settings_enable_buying_toggle"
                    )

                    SettingsSwitchRow(
                        title = "Фильтр по порогу цены",
                        subtitle = if (config.usePriceThreshold) "Покупать только по заданному порогу" else "Покупать любой доступный лот",
                        checked = config.usePriceThreshold,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(usePriceThreshold = checked) }
                        },
                        testTag = "settings_use_price_threshold"
                    )

                    if (config.usePriceThreshold) {
                        OutlinedTextField(
                            value = thresholdInput,
                            onValueChange = { raw ->
                                thresholdInput = raw
                                raw.replace(",", ".").toDoubleOrNull()?.let { value ->
                                    onUpdateConfig { it.copy(priceThreshold = value) }
                                }
                            },
                            label = { Text("Целевой порог цены (MM)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_price_threshold_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OperatorChip(
                                label = "≤ Ниже или равно",
                                selected = config.isLessThanOperator,
                                onClick = { onUpdateConfig { it.copy(isLessThanOperator = true) } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_operator_lte")
                            )
                            OperatorChip(
                                label = "≥ Выше или равно",
                                selected = !config.isLessThanOperator,
                                onClick = { onUpdateConfig { it.copy(isLessThanOperator = false) } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_operator_gte")
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "Подробный лог OCR (Отладка)",
                        subtitle = if (config.verboseOcrLogging) "Показывать все распознанные строки" else "Только важные события",
                        checked = config.verboseOcrLogging,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(verboseOcrLogging = checked) }
                        },
                        testTag = "settings_verbose_logging_toggle"
                    )
                }
            }

            // 4. Timings & Randomization
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Тайминги",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Интервалы и рандомизация",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Задержка после переключения вкладки: ${tabSwitchSlider.toLong()} мс",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Как в рабочем боте: оптимально ~400–450 мс (слишком низкие значения < 150 мс не дают игре прогрузить лоты)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = tabSwitchSlider,
                            onValueChange = { v ->
                                isDraggingTabSlider = true
                                tabSwitchSlider = v
                            },
                            onValueChangeFinished = {
                                isDraggingTabSlider = false
                                val ms = tabSwitchSlider.toLong().coerceIn(40L, 1500L)
                                onUpdateConfig { it.copy(tabSwitchIntervalMs = ms) }
                            },
                            valueRange = 40f..1500f,
                            modifier = Modifier.testTag("settings_tab_switch_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val ms = (tabSwitchSlider.toLong() - 50L).coerceIn(40L, 1500L)
                                    tabSwitchSlider = ms.toFloat()
                                    onUpdateConfig { it.copy(tabSwitchIntervalMs = ms) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("tab_switch_minus_50")
                            ) {
                                Text("−50 мс")
                            }
                            OutlinedButton(
                                onClick = {
                                    tabSwitchSlider = 430f
                                    onUpdateConfig { it.copy(tabSwitchIntervalMs = 430L) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("tab_switch_preset_430")
                            ) {
                                Text("430 мс")
                            }
                            OutlinedButton(
                                onClick = {
                                    val ms = (tabSwitchSlider.toLong() + 50L).coerceIn(40L, 1500L)
                                    tabSwitchSlider = ms.toFloat()
                                    onUpdateConfig { it.copy(tabSwitchIntervalMs = ms) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("tab_switch_plus_50")
                            ) {
                                Text("+50 мс")
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Доп. пауза между циклами: ${scanIntervalSlider.toLong()} мс",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = scanIntervalSlider,
                            onValueChange = { v ->
                                isDraggingScanSlider = true
                                scanIntervalSlider = v
                            },
                            onValueChangeFinished = {
                                isDraggingScanSlider = false
                                val ms = scanIntervalSlider.toLong().coerceIn(0L, 1500L)
                                onUpdateConfig { it.copy(scanIntervalMs = ms) }
                            },
                            valueRange = 0f..1500f,
                            modifier = Modifier.testTag("settings_scan_interval_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val ms = (scanIntervalSlider.toLong() - 50L).coerceIn(0L, 1500L)
                                    scanIntervalSlider = ms.toFloat()
                                    onUpdateConfig { it.copy(scanIntervalMs = ms) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_interval_minus_50")
                            ) {
                                Text("−50 мс")
                            }
                            OutlinedButton(
                                onClick = {
                                    scanIntervalSlider = 0f
                                    onUpdateConfig { it.copy(scanIntervalMs = 0L) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_interval_preset_0")
                            ) {
                                Text("0 мс")
                            }
                            OutlinedButton(
                                onClick = {
                                    val ms = (scanIntervalSlider.toLong() + 50L).coerceIn(0L, 1500L)
                                    scanIntervalSlider = ms.toFloat()
                                    onUpdateConfig { it.copy(scanIntervalMs = ms) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_interval_plus_50")
                            ) {
                                Text("+50 мс")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tabRandInput,
                            onValueChange = { raw ->
                                tabRandInput = raw
                                raw.toIntOrNull()?.let { value ->
                                    onUpdateConfig { it.copy(tabSwitchRandomizationMs = value.coerceIn(0, 1000)) }
                                }
                            },
                            label = { Text("Разброс вкладок ±мс") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = clickRandInput,
                            onValueChange = { raw ->
                                clickRandInput = raw
                                raw.toIntOrNull()?.let { value ->
                                    onUpdateConfig { it.copy(clickRandomizationRadiusPx = value.coerceIn(0, 60)) }
                                }
                            },
                            label = { Text("Радиус клика (px)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 5. Search Cycles
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Циклы",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Циклический поиск (Cycles 1–3)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SettingsSwitchRow(
                        title = "Использовать циклы с перезагрузкой",
                        subtitle = if (config.useSearchCycles) "Автоперезагрузка страницы при отсутствии покупок" else "Непрерывное сканирование",
                        checked = config.useSearchCycles,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(useSearchCycles = checked) }
                        },
                        testTag = "settings_use_cycles_toggle"
                    )

                    if (config.useSearchCycles) {
                        CycleRow(
                            cycleNumber = 1,
                            durationText = cycle1Min,
                            randText = cycle1Rand,
                            onDurationChange = { raw ->
                                cycle1Min = raw
                                raw.toIntOrNull()?.let { v ->
                                    onUpdateConfig { it.copy(cycle1DurationMin = v.coerceAtLeast(1)) }
                                }
                            },
                            onRandChange = { raw ->
                                cycle1Rand = raw
                                raw.toIntOrNull()?.let { v ->
                                    onUpdateConfig { it.copy(cycle1RandomRangeSec = abs(v)) }
                                }
                            }
                        )
                        CycleRow(
                            cycleNumber = 2,
                            durationText = cycle2Min,
                            randText = cycle2Rand,
                            onDurationChange = { raw ->
                                cycle2Min = raw
                                raw.toIntOrNull()?.let { v ->
                                    onUpdateConfig { it.copy(cycle2DurationMin = v.coerceAtLeast(1)) }
                                }
                            },
                            onRandChange = { raw ->
                                cycle2Rand = raw
                                raw.toIntOrNull()?.let { v ->
                                    onUpdateConfig { it.copy(cycle2RandomRangeSec = abs(v)) }
                                }
                            }
                        )
                        CycleRow(
                            cycleNumber = 3,
                            durationText = cycle3Min,
                            randText = cycle3Rand,
                            onDurationChange = { raw ->
                                cycle3Min = raw
                                raw.toIntOrNull()?.let { v ->
                                    onUpdateConfig { it.copy(cycle3DurationMin = v.coerceAtLeast(1)) }
                                }
                            },
                            onRandChange = { raw ->
                                cycle3Rand = raw
                                raw.toIntOrNull()?.let { v ->
                                    onUpdateConfig { it.copy(cycle3RandomRangeSec = abs(v)) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatCoord(label: String, x: Float, y: Float): String {
    return if (x >= 0f && y >= 0f) {
        "• $label: (${x.toInt()}, ${y.toInt()})"
    } else {
        "• $label: авто (OCR по скриншоту)"
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun OperatorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CycleRow(
    cycleNumber: Int,
    durationText: String,
    randText: String,
    onDurationChange: (String) -> Unit,
    onRandChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = durationText,
            onValueChange = onDurationChange,
            label = { Text("Цикл $cycleNumber (мин)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = randText,
            onValueChange = onRandChange,
            label = { Text("Разброс ±сек") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}
