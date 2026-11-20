package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MarketTab(val tabLabel: String, val displayRu: String) {
    PUZZLES("Puzzles", "Пазлы земли (Puzzles)"),
    LANDS("Lands", "Земля (Lands)");

    companion object {
        fun fromString(value: String?): MarketTab {
            return if (value.equals("Lands", ignoreCase = true)) LANDS else PUZZLES
        }
    }
}

@Entity(tableName = "app_configuration")
data class AppConfiguration(
    @PrimaryKey val id: Int = 1,
    val buyTarget: String = MarketTab.PUZZLES.tabLabel,
    val switchBetweenTabs: Boolean = true,
    val autoBuyEnabled: Boolean = false,
    val isCalibrationEnabled: Boolean = false,
    val priceThreshold: Double = 100.0,
    val usePriceThreshold: Boolean = true,
    val isLessThanOperator: Boolean = true,
    val scanIntervalMs: Long = 350L,
    val tabSwitchIntervalMs: Long = 150L,
    val tabSwitchRandomizationMs: Int = 50,
    val clickRandomizationRadiusPx: Int = 10,
    val enableActualBuying: Boolean = false,
    val verboseOcrLogging: Boolean = false,
    val useSearchCycles: Boolean = false,
    val cycle1DurationMin: Int = 2,
    val cycle2DurationMin: Int = 1,
    val cycle3DurationMin: Int = 1,
    val cycle1RandomRangeSec: Int = 30,
    val cycle2RandomRangeSec: Int = 30,
    val cycle3RandomRangeSec: Int = 30,
    val calibratedPuzzlesX: Float = -1f,
    val calibratedPuzzlesY: Float = -1f,
    val calibratedLandsX: Float = -1f,
    val calibratedLandsY: Float = -1f,
    val calibratedConfirmX: Float = -1f,
    val calibratedConfirmY: Float = -1f
)
