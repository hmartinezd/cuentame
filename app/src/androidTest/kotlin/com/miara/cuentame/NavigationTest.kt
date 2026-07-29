package com.miara.cuentame

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.test.TestSeeder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class NavigationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun app_startsOnOnboarding_whenNoRestaurant() {
        runBlocking {
            database.clearAllTables()
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("onboarding_screen").assertExists()
        }
    }

    @Test
    fun app_startsOnHome_whenRestaurantExists() {
        runBlocking {
            database.clearAllTables()
            TestSeeder.seedBaseline(database)
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("home_screen").assertExists()
        }
    }

    @Test
    fun navigateToSettingsAndBack() {
        runBlocking {
            database.clearAllTables()
            TestSeeder.seedBaseline(database)
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("home_settings_button").performClick()
            composeTestRule.onNodeWithTag("settings_screen").assertExists()
            
            composeTestRule.onNodeWithTag("settings_back_button").performClick()
            composeTestRule.onNodeWithTag("home_screen").assertExists()
        }
    }
}
