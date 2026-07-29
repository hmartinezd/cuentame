package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.TestSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DetailedReportsUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
            preferencesRepository.clearAll()
            TestSeeder.seedBaseline(database)
            preferencesRepository.setOnboardingCompleted(true)
            preferencesRepository.setAppLocaleTag("en-US")
        }
    }

    @Test
    fun reports_display_seeded_values() {
        runBlocking {
            TestSeeder.seedPostedPurchase(database, "p1", "123.45")
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("home_reports_button").performClick()
            
            // Wait for loading to finish and dashboard data to appear
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodesWithText("$123.45", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithText("$123.45", substring = true).assertIsDisplayed()
        }
    }
}
