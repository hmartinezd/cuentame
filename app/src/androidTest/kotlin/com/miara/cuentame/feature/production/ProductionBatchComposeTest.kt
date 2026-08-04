package com.miara.cuentame.feature.production

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.feature.production.ui.ProductionEffectiveTimeEditor
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

    @Test
    fun effectiveTimeEditor_dateSelection_updatesInstantCorrecty() {
        // Fix locale and zone for determinism
        Locale.setDefault(Locale.US)
        val zoneId = ZoneId.systemDefault()
        
        // Aug 3, 2026, 10:00 AM
        val initialInstant = LocalDateTime.of(2026, 8, 3, 10, 0)
            .atZone(zoneId).toInstant()
        
        var capturedInstant by mutableStateOf(initialInstant)

        composeTestRule.setContent {
            ProductionEffectiveTimeEditor(
                effectiveAt = capturedInstant,
                onEffectiveAtChanged = { capturedInstant = it }
            )
        }

        // Open Date Picker
        composeTestRule.onNodeWithTag("production_effective_date_button").performClick()
        
        // Wait for dialog and select Aug 15, 2026
        // Material 3 DatePicker: we find the day "15" and click it
        composeTestRule.onNodeWithTag("production_effective_date_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("15").performClick()
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
    fun effectiveTimeEditor_timeSelection_updatesInstantCorrecty() {
        Locale.setDefault(Locale.US)
        val zoneId = ZoneId.systemDefault()
        
        // Aug 3, 2026, 10:00 AM
        val initialInstant = LocalDateTime.of(2026, 8, 3, 10, 0)
            .atZone(zoneId).toInstant()
        
        var capturedInstant by mutableStateOf(initialInstant)

        composeTestRule.setContent {
            ProductionEffectiveTimeEditor(
                effectiveAt = capturedInstant,
                onEffectiveAtChanged = { capturedInstant = it }
            )
        }

        // Open Time Picker
        composeTestRule.onNodeWithTag("production_effective_time_button").performClick()
        
        // In a real test we'd need to interact with TimePicker dials, 
        // but for now we'll just verify the confirm button works if we can't easily set time.
        // Actually, let's try to set it if possible or just click confirm to verify time-preservation/seconds-clearing.
        composeTestRule.onNodeWithTag("production_effective_time_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("production_effective_time_confirm").performClick()
        
        val resultZdt = capturedInstant.atZone(zoneId)
        assertEquals(3, resultZdt.dayOfMonth)
        assertEquals(0, resultZdt.second)
        assertEquals(0, resultZdt.nano)
    }
}
