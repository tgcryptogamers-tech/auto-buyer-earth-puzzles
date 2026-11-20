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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.components.CalibrationBoardCard
import com.example.ui.components.CalibrationTarget
import com.example.ui.components.MarketTabsStripPreview
import com.example.ui.components.SectionCard
import kotlin.math.abs

@Composable
fun SettingsScreen(
    config: AppConfiguration,
    onUpdateConfig: ((AppConfiguration) -> AppConfiguration) -> Unit,
    onResetCoordinates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTarget = MarketTab.fromString(config.buyTarget)

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
                                text = "Вкладки и цель покупки",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Две рабочие вкладки: Puzzles и Lands. Выберите, что именно покупать.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    BuyTargetSelector(
                        selectedTarget = selectedTarget,
                        onSelectTarget = { target ->
                            onUpdateConfig { it.copy(buyTarget = target.tabLabel) }
                        }
                    )

                    SettingsSwitchRow(
                        title = "Переключение между Puzzles и Lands",
                        subtitle = if (config.switchBetweenTabs) {
                            "Бот переключается между вкладками Puzzles ↔ Lands, но покупает только ${selectedTarget.tabLabel}"
                        } else {
                            "Оставаться только на вкладке ${selectedTarget.tabLabel}"
                        },
                        checked = config.switchBetweenTabs,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(switchBetweenTabs = checked) }
                        },
                        testTag = "settings_tab_switch_toggle"
                    )

                    MarketTabsStripPreview(
                        activeTab = selectedTarget,
                        buyTarget = selectedTarget,
                        switchBetweenTabs = config.switchBetweenTabs
                    )
                }
            }

            // 2. Price Threshold & Buy Execution Mode
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
                            text = "Порог цены и режим сделки",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SettingsSwitchRow(
                        title = "Фильтр по порогу цены",
                        subtitle = if (config.usePriceThreshold) "Покупать только при совпадении условия цены" else "Покупать любой лот на выбранной вкладке",
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
                        title = "Фиксировать сделки в историю",
                        subtitle = if (config.enableActualBuying) "Активный режим (сохраняет найденные лоты как покупки)" else "Режим мониторинга (только логирование)",
                        checked = config.enableActualBuying,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(enableActualBuying = checked) }
                        },
                        testTag = "settings_enable_buying_toggle"
                    )

                    SettingsSwitchRow(
                        title = "Подробный лог сканирования",
                        subtitle = if (config.verboseOcrLogging) "Логировать каждое переключение и отклонённые цены" else "Только ключевые события и совпадения",
                        checked = config.verboseOcrLogging,
                        onCheckedChange = { checked ->
                            onUpdateConfig { it.copy(verboseOcrLogging = checked) }
                        },
                        testTag = "settings_verbose_logging_toggle"
                    )
                }
            }

            // 3. Timings & Randomization
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

                    Column {
                        Text(
                            text = "Интервал переключения вкладок: ${config.tabSwitchIntervalMs} мс",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = config.tabSwitchIntervalMs.toFloat().coerceIn(50f, 1500f),
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(tabSwitchIntervalMs = v.toLong()) }
                            },
                            valueRange = 50f..1500f,
                            modifier = Modifier.testTag("settings_tab_switch_slider")
                        )
                    }

                    Column {
                        Text(
                            text = "Интервал сканирования лотов: ${config.scanIntervalMs} мс",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Slider(
                            value = config.scanIntervalMs.toFloat().coerceIn(150f, 2000f),
                            onValueChange = { v ->
                                onUpdateConfig { it.copy(scanIntervalMs = v.toLong()) }
                            },
                            valueRange = 150f..2000f,
                            modifier = Modifier.testTag("settings_scan_interval_slider")
                        )
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

            // 4. Search Cycles (Циклы 1..3)
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
                        title = "Использовать 3 цикла поиска",
                        subtitle = if (config.useSearchCycles) "Автоматический переход между циклами со случайным сдвигом" else "Непрерывное сканирование",
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

            // 5. Coordinate Calibration for Puzzles, Lands & Confirm
            item {
                CalibrationBoardCard(
                    config = config,
                    onCalibrateCoordinate = { target, x, y ->
                        onUpdateConfig { cfg ->
                            when (target) {
                                CalibrationTarget.PUZZLES -> cfg.copy(calibratedPuzzlesX = x, calibratedPuzzlesY = y)
                                CalibrationTarget.LANDS -> cfg.copy(calibratedLandsX = x, calibratedLandsY = y)
                                CalibrationTarget.CONFIRM -> cfg.copy(calibratedConfirmX = x, calibratedConfirmY = y)
                            }
                        }
                    },
                    onResetCoordinates = onResetCoordinates
                )
            }
        }
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
