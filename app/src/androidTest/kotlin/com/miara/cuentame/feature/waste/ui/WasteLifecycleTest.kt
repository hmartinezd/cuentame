package com.miara.cuentame.feature.waste.ui

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
class WasteLifecycleTest {

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
    fun navigateToWasteAndBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("view_waste_button")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("view_waste_button", useUnmergedTree = true).performClick()
            composeTestRule.onNodeWithTag("waste_list_screen").assertExists()
            
            composeTestRule.onNodeWithTag("waste_list_back").performClick()
            composeTestRule.onNodeWithTag("home_screen").assertExists()
        }
    }
}
