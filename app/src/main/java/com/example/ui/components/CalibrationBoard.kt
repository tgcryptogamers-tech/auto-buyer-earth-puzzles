package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.AppConfiguration
import kotlin.math.roundToInt

enum class CalibrationTarget(val label: String) {
    PUZZLES("Вкладка Puzzles"),
    LANDS("Вкладка Lands"),
    CONFIRM("Кнопка Confirm")
}

@Composable
fun CalibrationBoardCard(
    config: AppConfiguration,
    onCalibrateCoordinate: (CalibrationTarget, Float, Float) -> Unit,
    onResetCoordinates: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTarget by remember { mutableStateOf(CalibrationTarget.PUZZLES) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Калибровка",
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Калибровка координат (Puzzles / Lands)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Выберите элемент и коснитесь макета рынка для привязки точек нажатия",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CalibrationTarget.entries.forEach { target ->
                val selected = activeTarget == target
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { activeTarget = target }
                        .testTag("calibrate_select_${target.name.lowercase()}"),
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(
                            text = target.label.replace("Вкладка ", "").replace("Кнопка ", ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Interactive MoneyMining market calibration canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .onSizeChanged { boardSize = it }
                .pointerInput(activeTarget, boardSize) {
                    detectTapGestures { offset ->
                        if (boardSize.width > 0 && boardSize.height > 0) {
                            // Store normalized 0..1080 x 0..2400 coordinates matching screen space
                            val mappedX = (offset.x / boardSize.width) * 1080f
                            val mappedY = (offset.y / boardSize.height) * 2400f
                            onCalibrateCoordinate(activeTarget, mappedX, mappedY)
                        }
                    }
                }
                .testTag("calibration_canvas")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CanvasChip("Buy", highlighted = true, modifier = Modifier.weight(1f))
                    CanvasChip("Sell", highlighted = false, modifier = Modifier.weight(1f))
                    CanvasChip("On Sale", highlighted = false, modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CanvasChip(
                        "Puzzles",
                        highlighted = activeTarget == CalibrationTarget.PUZZLES,
                        modifier = Modifier.weight(1f)
                    )
                    CanvasChip(
                        "Lands",
                        highlighted = activeTarget == CalibrationTarget.LANDS,
                        modifier = Modifier.weight(1f)
                    )
                    CanvasChip("Bricks", highlighted = false, modifier = Modifier.weight(1f))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (activeTarget == CalibrationTarget.CONFIRM) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeTarget == CalibrationTarget.CONFIRM) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Text(
                            text = "Confirm Buy",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Draw saved markers
            if (boardSize.width > 0 && boardSize.height > 0) {
                CalibrationPin(config.calibratedPuzzlesX, config.calibratedPuzzlesY, "P", boardSize, MaterialTheme.colorScheme.primary)
                CalibrationPin(config.calibratedLandsX, config.calibratedLandsY, "L", boardSize, MaterialTheme.colorScheme.secondary)
                CalibrationPin(config.calibratedConfirmX, config.calibratedConfirmY, "C", boardSize, MaterialTheme.colorScheme.tertiary)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = formatCoord("Puzzles", config.calibratedPuzzlesX, config.calibratedPuzzlesY),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatCoord("Lands", config.calibratedLandsX, config.calibratedLandsY),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatCoord("Confirm", config.calibratedConfirmX, config.calibratedConfirmY),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = onResetCoordinates,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_coordinates_button"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Сбросить координаты")
        }
    }
}

@Composable
private fun CanvasChip(
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun CalibrationPin(
    x: Float,
    y: Float,
    badge: String,
    boardSize: IntSize,
    color: androidx.compose.ui.graphics.Color
) {
    if (x < 0f || y < 0f) return
    val px = ((x / 1080f).coerceIn(0f, 1f) * boardSize.width).roundToInt() - 12
    val py = ((y / 2400f).coerceIn(0f, 1f) * boardSize.height).roundToInt() - 12
    Box(
        modifier = Modifier
            .offset { IntOffset(px.coerceIn(0, boardSize.width - 24), py.coerceIn(0, boardSize.height - 24)) }
            .size(24.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

private fun formatCoord(label: String, x: Float, y: Float): String {
    return if (x >= 0f && y >= 0f) {
        "• $label: (${x.toInt()}, ${y.toInt()})"
    } else {
        "• $label: авто (OCR)"
    }
}
