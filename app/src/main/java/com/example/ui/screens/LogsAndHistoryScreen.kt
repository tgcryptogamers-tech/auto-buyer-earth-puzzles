package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.PurchaseRecord
import com.example.engine.BotLogs
import com.example.ui.components.LogsTextModalDialog
import com.example.ui.components.SectionCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsAndHistoryScreen(
    logs: List<String>,
    purchases: List<PurchaseRecord>,
    onClearLogs: () -> Unit,
    onClearPurchases: () -> Unit,
    onDeletePurchase: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dateFormatter = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
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
                val content = BotLogs.getAllLogsText().ifBlank { "Логи пусты" }
                BotLogs.shareText(context, "Экспорт логов Earth Puzzles", content)
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
            item {
                Text(
                    text = "Логи и история сделок",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Live Terminal Logs
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
                                contentDescription = "Терминал",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Журнал событий (${logs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row {
                            IconButton(
                                onClick = {
                                    val content = BotLogs.getAllLogsText().ifBlank { "Логи пусты" }
                                    BotLogs.shareText(context, "Экспорт логов Earth Puzzles", content)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("share_logs_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Поделиться логами")
                            }
                            IconButton(
                                onClick = onClearLogs,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("clear_logs_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Очистить логи")
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
                                .testTag("copy_logs_button"),
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
                                .testTag("view_logs_text_button"),
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
                                text = "Логи пока пусты. Запустите мониторинг или выберите скриншот на панели.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = logs.takeLast(60).reversed().joinToString("\n"),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Purchase History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "История",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "История покупок (${purchases.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (purchases.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearPurchases,
                            modifier = Modifier.testTag("clear_purchases_button")
                        ) {
                            Text("Очистить")
                        }
                    }
                }
            }

            if (purchases.isEmpty()) {
                item {
                    SectionCard {
                        Text(
                            text = "Сохранённых сделок пока нет",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Включите «Фиксировать сделки в историю» в Настройках и запустите мониторинг для проверки порогов на вкладках Puzzles / Lands.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(purchases, key = { it.id }) { record ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = record.tabName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = record.itemName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${String.format("%.4f", record.price)} MM • ${dateFormatter.format(Date(record.timestamp))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = record.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onDeletePurchase(record.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Удалить запись",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
