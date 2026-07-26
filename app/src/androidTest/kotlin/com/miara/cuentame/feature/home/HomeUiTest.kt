package com.miara.cuentame.feature.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.common.ids.RestaurantId
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.waste.ui.waitForTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import javax.inject.Inject

@HiltAndroidTest
class HomeUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var db: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            db.clearAllTables()
            preferencesRepository.clearAll()
            db.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            
            preferencesRepository.setAppLocaleTag("en")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    private fun seedRestaurantAndArea(name: String = "Test Restaurant") {
        runBlocking {
            val now = Instant.now()
            val restId = RestaurantId("rest-1")
            db.restaurantDao().insert(RestaurantEntity(restId.value, name, "USD", "en-US", now.toEpochMilli(), now.toEpochMilli(), null))
            db.inventoryAreaDao().upsert(InventoryAreaEntity("area-1", restId.value, "Main Area", "main area", 1, true, now.toEpochMilli(), now.toEpochMilli(), null))
        }
    }

    @Test
    fun dashboard_emptyState_whenNoActivity() {
        seedRestaurantAndArea("Empty Rest")
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("dashboard_inventory_value")
            
            composeTestRule.onNodeWithText("Empty Rest").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dashboard_inventory_value").assertIsDisplayed()
            
            // Scroll to the bottom to find the empty activity message
            composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("dashboard_recent_activity_empty"))
            composeTestRule.onNodeWithTag("dashboard_recent_activity_empty", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun dashboard_populatedState() {
        seedRestaurantAndArea("Populated Rest")
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("dashboard_inventory_value")
            
            composeTestRule.onNodeWithText("Populated Rest").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dashboard_inventory_value").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dashboard_purchase_spend").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dashboard_waste_value").assertIsDisplayed()
            
            // Quick Actions
            composeTestRule.onNodeWithTag("log_waste_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("new_purchase_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("start_count_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("view_reports_button").assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_rangeSwitching() {
        seedRestaurantAndArea()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("dashboard_inventory_value")
            
            // Verify default 30 days selected
            composeTestRule.onNodeWithTag("home_range_30").assertIsSelected()
            
            // Switch to 7 days
            composeTestRule.onNodeWithTag("home_range_7").performClick()
            composeTestRule.onNodeWithTag("home_range_7").assertIsSelected()
            composeTestRule.onNodeWithTag("home_range_30").assertIsNotSelected()
        }
    }
}
