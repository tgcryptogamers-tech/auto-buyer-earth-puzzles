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
    modifier: Modifier = Modifier
) {
    val activeSummary = when {
        buyPuzzles && buyLands -> "Покупка: Puzzles + Lands"
        switchBetweenTabs -> "Puzzles ↔ Lands"
        buyLands -> "Фикс: Lands"
        else -> "Фикс: Puzzles"
    }

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
                text = "Вкладки рынка MoneyMining",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = activeSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MarketStripTabItem(
                label = "Puzzles",
                isActive = buyPuzzles,
                isTarget = buyPuzzles,
                modifier = Modifier.weight(1f)
            )
            MarketStripTabItem(
                label = "Lands",
                isActive = buyLands,
                isTarget = buyLands,
                modifier = Modifier.weight(1f)
            )
            MarketStripTabItem(
                label = "Bricks",
                isActive = false,
                isTarget = false,
                enabled = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MarketStripTabItem(
    label: String,
    isActive: Boolean,
    isTarget: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val brush = if (isActive) {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.primaryContainer
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface
            )
        )
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isTarget) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    isActive -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f)
                }
            )
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
