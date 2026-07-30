package com.miara.cuentame.feature.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
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

    @Inject
    lateinit var testStateManager: TestStateManager

    private val testNow = Instant.parse("2026-01-01T12:00:00Z")

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            testStateManager.resetAll()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            testStateManager.resetAll()
        }
    }

    private fun seedReadyState(name: String = "Test Restaurant") = runBlocking {
        val restId = "rest-1"
        db.restaurantDao().insert(RestaurantEntity(restId, name, "USD", "en-US", testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area-1", restId, "Main Area", "main area", 0, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
        preferencesRepository.setOnboardingCompleted(true)
        preferencesRepository.setAppLocaleTag("en-US")
    }

    @Test
    fun dashboard_emptyState_whenNoActivity() {
        seedReadyState("Empty Rest")
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(20000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("dashboard_restaurant_name", useUnmergedTree = true).assertTextEquals("Empty Rest")
            composeTestRule.onNodeWithTag("dashboard_inventory_value", useUnmergedTree = true).assertTextContains("$0.00", substring = true)
            composeTestRule.onNodeWithTag("dashboard_purchase_spend", useUnmergedTree = true).assertTextContains("$0.00", substring = true)
            composeTestRule.onNodeWithTag("dashboard_waste_value", useUnmergedTree = true).assertTextContains("$0.00", substring = true)
            
            // Assert negative balance count is zero
            composeTestRule.onNodeWithTag("dashboard_negative_balance_count", useUnmergedTree = true).assertTextContains("0", substring = true)
            
            // Assert empty states
            composeTestRule.onNodeWithTag("dashboard_top_waste_empty").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dashboard_recent_activity_empty").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("view_reports_button").assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_fullVerification_populatedData() {
        runBlocking {
            seedReadyState("The Integrity Kitchen")
            val restId = "rest-1"
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", "area-1", null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            
            // Seed 10 units at $2 each = $20.00 valuation
            db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-1", "area-1", "10.0", testNow.toEpochMilli()))
            db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId, "ing-1", "2.0", testNow.toEpochMilli()))
            
            // Seed posted purchase of $100
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(1, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-1", "area-1", "opt-1", "5", "5", "100.0", "20.0", null, 0L, 0L))
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("dashboard_inventory_value")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("dashboard_restaurant_name", useUnmergedTree = true).assertTextEquals("The Integrity Kitchen")
            
            // Assert authoritative totals
            composeTestRule.onNodeWithTag("dashboard_inventory_value", useUnmergedTree = true).assertTextContains("$20.00", substring = true)
            composeTestRule.onNodeWithTag("dashboard_purchase_spend", useUnmergedTree = true).assertTextContains("$100.00", substring = true)
        }
    }

    @Test
    fun dashboard_navigation_to_reports() {
        seedReadyState()
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("view_reports_button")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("view_reports_button", useUnmergedTree = true).performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reports_screen").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("reports_back_button").performClick()
            
            composeTestRule.waitUntil(10000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }
}
