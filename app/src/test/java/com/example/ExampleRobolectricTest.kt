package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppConfiguration
import com.example.data.MarketTab
import com.example.engine.ScreenshotOcrAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Earth Puzzles", appName)
    }

    @Test
    fun `parse market tab defaults to Puzzles and recognizes Lands`() {
        assertEquals(MarketTab.PUZZLES, MarketTab.fromString("Puzzles"))
        assertEquals(MarketTab.LANDS, MarketTab.fromString("Lands"))
        assertEquals(MarketTab.PUZZLES, MarketTab.fromString(null))
    }

    @Test
    fun `ocr price extraction handles 4-decimal recovery and threshold check`() {
        assertEquals(20191.3135, ScreenshotOcrAnalyzer.extractPrice("20191.3135")!!, 0.0001)
        assertEquals(100.0, ScreenshotOcrAnalyzer.extractPrice("1000000")!!, 0.0001)
        assertEquals(0.5, ScreenshotOcrAnalyzer.extractPrice("0 .5000")!!, 0.0001)
        assertEquals(5.25, ScreenshotOcrAnalyzer.extractPrice("5. 2500")!!, 0.0001)
        assertEquals(null, ScreenshotOcrAnalyzer.extractPrice("0"))
        val cfg = AppConfiguration(priceThreshold = 100.0, isLessThanOperator = true, usePriceThreshold = true)
        assertTrue(ScreenshotOcrAnalyzer.evaluateThreshold(95.5, cfg))
        assertFalse(ScreenshotOcrAnalyzer.evaluateThreshold(120.0, cfg))
    }

    @Test
    fun `activeBuyTabs supports puzzles only, lands only, and both tabs`() {
        val puzzlesOnly = AppConfiguration(buyPuzzles = true, buyLands = false)
        assertEquals(listOf(MarketTab.PUZZLES), puzzlesOnly.activeBuyTabs())
        assertEquals("Только Puzzles", puzzlesOnly.buyTargetSummary())

        val landsOnly = AppConfiguration(buyPuzzles = false, buyLands = true)
        assertEquals(listOf(MarketTab.LANDS), landsOnly.activeBuyTabs())
        assertEquals("Только Lands", landsOnly.buyTargetSummary())

        val both = AppConfiguration(buyPuzzles = true, buyLands = true)
        assertEquals(listOf(MarketTab.PUZZLES, MarketTab.LANDS), both.activeBuyTabs())
        assertEquals("Puzzles + Lands", both.buyTargetSummary())
    }
}
