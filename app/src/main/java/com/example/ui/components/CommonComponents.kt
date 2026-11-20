package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MarketTab
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun BuyTargetSelector(
    buyPuzzles: Boolean,
    buyLands: Boolean,
    onToggleTab: (MarketTab) -> Unit,
    onSelectBoth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bothSelected = buyPuzzles && buyLands
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TargetOptionChip(
                tab = MarketTab.PUZZLES,
                title = "Puzzles",
                subtitle = if (buyPuzzles) "Пазлы земли: ВКЛ" else "Пазлы земли: ВЫКЛ",
                selected = buyPuzzles,
                onClick = { onToggleTab(MarketTab.PUZZLES) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("select_buy_puzzles")
            )
            TargetOptionChip(
                tab = MarketTab.LANDS,
                title = "Lands",
                subtitle = if (buyLands) "Земля: ВКЛ" else "Земля: ВЫКЛ",
                selected = buyLands,
                onClick = { onToggleTab(MarketTab.LANDS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("select_buy_lands")
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onSelectBoth)
                .testTag("select_buy_both"),
            shape = MaterialTheme.shapes.small,
            color = if (bothSelected) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = BorderStroke(
                width = if (bothSelected) 1.5.dp else 1.dp,
                color = if (bothSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (bothSelected) {
                        "✓ Покупать одновременно: Puzzles и Lands"
                    } else {
                        "Выбрать обе вкладки сразу (Puzzles + Lands)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (bothSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (bothSelected) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetOptionChip(
    tab: MarketTab,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            if (tab == MarketTab.PUZZLES) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "targetBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            if (tab == MarketTab.PUZZLES) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            }
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "targetBorder"
    )

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (tab == MarketTab.PUZZLES) Icons.Default.Extension else Icons.Default.Landscape,
                contentDescription = tab.tabLabel,
                tint = if (selected) {
                    if (tab == MarketTab.PUZZLES) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Выбрано",
                    tint = if (tab == MarketTab.PUZZLES) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MarketTabsStripPreview(
    buyPuzzles: Boolean,
    buyLands: Boolean,
    switchBetweenTabs: Boolean,
    onToggleTabSwitching: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeSummary = when {
        buyPuzzles && buyLands -> "Переключение: Puzzles ↔ Lands (обе покупаются)"
        switchBetweenTabs -> "Переключение: Puzzles ↔ Lands (обновление рынка)"
        buyLands -> "Фикс: только Lands"
        else -> "Фикс: только Puzzles"
    }

    val puzzlesSwitchOnly = !buyPuzzles && switchBetweenTabs
    val landsSwitchOnly = !buyLands && switchBetweenTabs

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Вкладки в игре",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = if (switchBetweenTabs || (buyPuzzles && buyLands)) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (switchBetweenTabs || (buyPuzzles && buyLands)) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = if (onToggleTabSwitching != null && !(buyPuzzles && buyLands)) {
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onToggleTabSwitching(!switchBetweenTabs) }
                        .testTag("strip_switch_badge")
                } else {
                    Modifier
                }
            ) {
                Text(
                    text = activeSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (switchBetweenTabs || (buyPuzzles && buyLands)) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MarketStripTabItem(
                label = "Puzzles",
                roleHint = when {
                    buyPuzzles -> "Покупка"
                    puzzlesSwitchOnly -> "⇄ Перекл."
                    else -> "Выкл"
                },
                isBuying = buyPuzzles,
                isSwitchPartner = puzzlesSwitchOnly,
                onClick = if (onToggleTabSwitching != null && !buyPuzzles) {
                    { onToggleTabSwitching(!switchBetweenTabs) }
                } else {
                    null
                },
                modifier = Modifier.weight(1f)
            )
            MarketStripTabItem(
                label = "Lands",
                roleHint = when {
                    buyLands -> "Покупка"
                    landsSwitchOnly -> "⇄ Перекл."
                    else -> "Выкл"
                },
                isBuying = buyLands,
                isSwitchPartner = landsSwitchOnly,
                onClick = if (onToggleTabSwitching != null && !buyLands) {
                    { onToggleTabSwitching(!switchBetweenTabs) }
                } else {
                    null
                },
                modifier = Modifier.weight(1f)
            )
            MarketStripTabItem(
                label = "Bricks",
                roleHint = "Не исп.",
                isBuying = false,
                isSwitchPartner = false,
                enabled = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MarketStripTabItem(
    label: String,
    roleHint: String,
    isBuying: Boolean,
    isSwitchPartner: Boolean,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val brush = when {
        isBuying -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.primaryContainer
            )
        )
        isSwitchPartner -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            )
        )
        else -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface
            )
        )
    }

    val borderStroke = when {
        isBuying -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isSwitchPartner -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }

    Surface(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        border = borderStroke,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isBuying) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                            isBuying || isSwitchPartner -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                        }
                    )
                }
                Text(
                    text = roleHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        isBuying -> MaterialTheme.colorScheme.onPrimaryContainer
                        isSwitchPartner -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun LogsTextModalDialog(
    logsText: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Текст лога (Ctrl+A / Выбрать всё)",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            OutlinedTextField(
                value = logsText.ifBlank { "Логи пока пусты." },
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp)
                    .testTag("logs_modal_textfield")
            )
        },
        confirmButton = {
            Button(
                onClick = onCopy,
                modifier = Modifier.testTag("logs_modal_copy_btn")
            ) {
                Text("КОПИРОВАТЬ")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("logs_modal_share_btn")
                ) {
                    Text("ОТПРАВИТЬ")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("logs_modal_close_btn")
                ) {
                    Text("ЗАКРЫТЬ")
                }
            }
        }
    )
}
