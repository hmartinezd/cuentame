package com.venkoi.cuentame.feature.production

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.venkoi.cuentame.feature.production.ui.ProductionEffectiveTimeEditor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ProductionBatchComposeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var originalLocale: Locale

    @org.junit.Before
    fun captureLocale() {
        originalLocale = Locale.getDefault()
    }

    @org.junit.After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun effectiveTimeEditor_dateSelection_updatesInstantCorrecty() {
        // Fix locale and zone for determinism
        Locale.setDefault(Locale.US)
        val zoneId = ZoneId.of("America/New_York")
        
        // Aug 3, 2026, 10:00 AM
        val initialInstant = LocalDateTime.of(2026, 8, 3, 10, 0)
            .atZone(zoneId).toInstant()
        
        var capturedInstant by mutableStateOf(initialInstant)

        composeTestRule.setContent {
            ProductionEffectiveTimeEditor(
                effectiveAt = capturedInstant,
                onEffectiveAtChanged = { capturedInstant = it },
                zoneId = zoneId
            )
        }

        // Open Date Picker
        composeTestRule.onNodeWithTag("production_effective_date_button").performClick()
        
        // Wait for dialog and select Aug 15, 2026
        composeTestRule.onNodeWithTag("production_effective_date_dialog").assertIsDisplayed()
        
        // Select Aug 15, 2026.
        composeTestRule.onNode(hasText("15", substring = true) and hasClickAction()).performClick()

        composeTestRule.onNodeWithTag("production_effective_date_confirm").performClick()
        
        // Verify captured Instant
        val expectedLocalDateTime = LocalDateTime.of(2026, 8, 15, 10, 0)
        val expectedInstant = expectedLocalDateTime.atZone(zoneId).toInstant()
        
        assertEquals("Date should be updated to 15th, time preserved", expectedInstant, capturedInstant)
        
        val resultZdt = capturedInstant.atZone(zoneId)
        assertEquals(2026, resultZdt.year)
        assertEquals(Month.AUGUST, resultZdt.month)
        assertEquals(15, resultZdt.dayOfMonth)
        assertEquals(10, resultZdt.hour)
        assertEquals(0, resultZdt.minute)
        assertEquals(0, resultZdt.second)
        assertEquals(0, resultZdt.nano)
    }

    @Test
    fun effectiveTimeEditor_timeSelection_updatesInstantCorrectly() {
        Locale.setDefault(Locale.US)
        val zoneId = ZoneId.of("America/New_York")
        
        // Aug 3, 2026, 10:00 AM
        val initialInstant = LocalDateTime.of(2026, 8, 3, 10, 0)
            .atZone(zoneId).toInstant()
        
        var capturedInstant by mutableStateOf(initialInstant)

        composeTestRule.setContent {
            ProductionEffectiveTimeEditor(
                effectiveAt = capturedInstant,
                onEffectiveAtChanged = { capturedInstant = it },
                zoneId = zoneId
            )
        }

        // Open Time Picker
        composeTestRule.onNodeWithTag("production_effective_time_button").performClick()
        
        composeTestRule.onNodeWithTag("production_effective_time_dialog").assertIsDisplayed()
        
        // We ensure confirm button clears seconds/nanos.
        // Note: Actual hour/minute change is verified in pure JVM tests.
        composeTestRule.onNodeWithTag("production_effective_time_confirm").performClick()
        
        val resultZdt = capturedInstant.atZone(zoneId)
        assertEquals(3, resultZdt.dayOfMonth)
        assertEquals(2026, resultZdt.year)
        assertEquals(Month.AUGUST, resultZdt.month)
        
        assertEquals(0, resultZdt.second)
        assertEquals(0, resultZdt.nano)
    }
}
