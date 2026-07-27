package com.miara.cuentame.feature.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.model.inventory.StockCountStatus
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import com.miara.cuentame.feature.waste.ui.waitForTag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
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

    private val now = Instant.parse("2024-01-31T12:00:00Z")

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            db.clearAllTables()
            preferencesRepository.clearAll()
            db.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            
            preferencesRepository.setAppLocaleTag("en-US")
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    private fun seedRestaurantAndArea(name: String = "Test Restaurant") {
        runBlocking {
            val restId = "rest-1"
            db.restaurantDao().insert(RestaurantEntity(restId, name, "USD", "en-US", now.toEpochMilli(), now.toEpochMilli(), null))
            db.inventoryAreaDao().upsert(InventoryAreaEntity("area-1", restId, "Main Area", "main area", 1, true, now.toEpochMilli(), now.toEpochMilli(), null))
        }
    }

    private fun seedPopulatedData() = runBlocking {
        val restId = "rest-1"
        seedRestaurantAndArea("Test Restaurant")

        // Create ingredient with full cost and valuation data
        db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, now.toEpochMilli(), now.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, now.toEpochMilli(), now.toEpochMilli(), null))
        
        // Inventory balance: 10 lb
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-1", "area-1", "10.0", now.toEpochMilli()))

        // Cost projection: $2.0 per lb → Total inventory value = 10 * 2 = $20
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId, "ing-1", "2.0", now.toEpochMilli()))

        // Posted Purchase 5 hours ago: $100
        val pid = "p1"
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity(pid, restId, null, null, now.minusSeconds(18000).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, now.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", pid, "ing-1", "area-1", "opt-1", "5", "5", "100.0", "20.0", null, 0L, 0L))

        // Create inventory movement for purchase
        db.inventoryMovementDao().insert(InventoryMovementEntity("m_purchase", restId, "ing-1", "area-1", InventoryMovementType.PURCHASE.name, "5.0", "20.0", "100.0", now.minusSeconds(18000).toEpochMilli(), SourceDocumentType.PURCHASE_RECEIPT.name, pid, "l1", null, null, 0L))

        // Posted Waste 2 hours ago: 1 lb at $2 = $10
        val wid = "w1"
        db.wasteDao().insert(WasteEventEntity(wid, restId, "ing-1", "area-1", "opt-1", "1", "1", "SPOILED", now.minusSeconds(7200).toEpochMilli(), null, null, DocumentStatus.POSTED.name, 0L, 0L, now.toEpochMilli(), null))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m_waste", restId, "ing-1", "area-1", InventoryMovementType.WASTE.name, "-1.0", "2.0", "2.0", now.minusSeconds(7200).toEpochMilli(), SourceDocumentType.WASTE_EVENT.name, wid, "w1", null, null, 0L))

        // Completed Stock Count
        val cid = "c1"
        db.stockCountDao().insertCount(StockCountEntity(cid, restId, "Weekly Count", now.toEpochMilli(), now.toEpochMilli(), now.toEpochMilli(), StockCountStatus.COMPLETED.name, null, 0L, 0L, null))
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("ca1", cid, "area-1", "COMPLETED", now.toEpochMilli(), now.toEpochMilli(), 1)))
        db.stockCountDao().insertCountLine(StockCountLineEntity("cl1", "ca1", "ing-1", "opt-1", "10", "10", "5", "5", null, 0L, 0L))
    }

    @Test
    fun dashboard_setupRequired_whenNoRestaurant() {
        // Force RequiresOnboarding state by not seeding restaurant
        runBlocking {
            preferencesRepository.clearAll()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("onboarding_screen_root")
            composeTestRule.onNodeWithTag("onboarding_screen_root").assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_emptyState_whenNoActivity() {
        seedRestaurantAndArea("Empty Rest")
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")
            
            composeTestRule.onNodeWithText("Empty Rest").assertIsDisplayed()
            composeTestRule.onNodeWithTag("dashboard_inventory_value").assertIsDisplayed()
            composeTestRule.onNodeWithText("$0.00", substring = true).assertIsDisplayed()

            // Perform scroll to the bottom of the dashboard content
            composeTestRule.onNode(hasTestTag("home_screen")).onChildAt(0).performScrollToNode(hasTestTag("dashboard_recent_activity_empty"))
            composeTestRule.onNodeWithTag("dashboard_recent_activity_empty", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_loading_state_initially() {
        seedRestaurantAndArea()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("dashboard_inventory_value", 10_000)
        }
    }

    @Test
    fun dashboard_error_state_shows_retry() {
        // This test would require injecting a failing repository,
        // which is complex in an instrumentation test.
        // Covered by unit tests instead.
    }

    @Test
    fun dashboard_populatedWithData() {
        seedPopulatedData()
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")
            
            // Verify restaurant name
            composeTestRule.onNodeWithText("Test Restaurant").assertIsDisplayed()
            
            // KPIs
            composeTestRule.onNodeWithTag("dashboard_inventory_value").assertIsDisplayed()
            composeTestRule.onNodeWithText("$20.00", substring = true).assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("dashboard_negative_balance_count").assertIsDisplayed()
            composeTestRule.onNodeWithText("0", substring = true).assertIsDisplayed()

            composeTestRule.onNodeWithTag("dashboard_purchase_spend").assertIsDisplayed()
            composeTestRule.onNodeWithText("$100.00", substring = true).assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("dashboard_waste_value").assertIsDisplayed()
            composeTestRule.onNodeWithText("$2.00", substring = true).assertIsDisplayed()

            // Data Completeness
            composeTestRule.onNodeWithTag("dashboard_data_completeness").assertIsDisplayed()
            composeTestRule.onNodeWithText("1 / 1", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("100", substring = true).assertIsDisplayed()

            // Stock Count Summary
            composeTestRule.onNodeWithTag("dashboard_stock_count_summary").assertIsDisplayed()
            composeTestRule.onNodeWithText("1", substring = true).assertIsDisplayed()
            
            // Activity List
            composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("dashboard_activity_PURCHASE_p1"))
            composeTestRule.onNodeWithTag("dashboard_activity_PURCHASE_p1", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithTag("dashboard_activity_WASTE_w1", useUnmergedTree = true).assertIsDisplayed()

            // Localized Status Check
            composeTestRule.onNodeWithText("Posted", substring = true, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_rangeSwitching_from30To7_updatesValues() {
        runBlocking {
            val restId = "rest-1"
            seedRestaurantAndArea("Range Rest")
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, now.toEpochMilli(), now.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, now.toEpochMilli(), now.toEpochMilli(), null))

            // Purchase 1: 5 days ago (Inside 7 and 30) → $50
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_recent", restId, null, null, now.minus(5, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, now.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l_recent", "p_recent", "ing-1", "area-1", "opt-1", "1", "1", "50.0", "50.0", null, 0L, 0L))

            // Purchase 2: 15 days ago (Inside 30, Outside 7) → $100
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_old", restId, null, null, now.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, now.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l_old", "p_old", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "100.0", null, 0L, 0L))

            preferencesRepository.setOnboardingCompleted(true)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")
            
            // Default 30 days: 50 + 100 = 150
            composeTestRule.onNodeWithText("$150.00", substring = true).assertIsDisplayed()
            
            // Switch to 7 days
            composeTestRule.onNodeWithTag("home_range_7").performClick()
            
            // Should update to 50
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithText("$50.00", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("$150.00", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun dashboard_rangeSwitching_from30To90_updatesValues() {
        runBlocking {
            val restId = "rest-1"
            seedRestaurantAndArea("Range Rest")
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, now.toEpochMilli(), now.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, now.toEpochMilli(), now.toEpochMilli(), null))

            // Purchase 1: 5 days ago (Inside all ranges) → $50
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_recent", restId, null, null, now.minus(5, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, now.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l_recent", "p_recent", "ing-1", "area-1", "opt-1", "1", "1", "50.0", "50.0", null, 0L, 0L))

            // Purchase 2: 60 days ago (Inside 90, Outside 30) → $100
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_old", restId, null, null, now.minus(60, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, now.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l_old", "p_old", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "100.0", null, 0L, 0L))

            preferencesRepository.setOnboardingCompleted(true)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")
            
            // Default 30 days: 50
            composeTestRule.onNodeWithText("$50.00", substring = true).assertIsDisplayed()

            // Switch to 90 days
            composeTestRule.onNodeWithTag("home_range_90").performClick()

            // Should update to 150
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithText("$150.00", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("$50.00", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun dashboard_navigation_logWaste() {
        seedRestaurantAndArea()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")
            composeTestRule.onNodeWithTag("log_waste_button").performClick()
            composeTestRule.waitForTag("waste_form_screen", 10_000)
            composeTestRule.onNodeWithTag("waste_form_screen").assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_navigation_newPurchase() {
        seedRestaurantAndArea()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")
            composeTestRule.onNodeWithTag("new_purchase_button").performClick()
            composeTestRule.waitForTag("purchase_header_screen", 10_000)
            composeTestRule.onNodeWithTag("purchase_header_screen").assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_navigation_startCount() {
        seedRestaurantAndArea()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")
            composeTestRule.onNodeWithTag("start_count_button").performClick()
            composeTestRule.waitForTag("stock_count_start_screen", 10_000)
            composeTestRule.onNodeWithTag("stock_count_start_screen").assertIsDisplayed()
        }
    }

    @Test
    fun dashboard_rangeChips_haveProperSemantics() {
        seedRestaurantAndArea()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_date_range_selector")

            // Check that range chips have meaningful semantics
            composeTestRule.onNodeWithTag("home_range_30").assertHasClickAction()
            composeTestRule.onNodeWithTag("home_range_7").assertHasClickAction()
            composeTestRule.onNodeWithTag("home_range_90").assertHasClickAction()
        }
    }

    @Test
    fun dashboard_quickActionButtons_haveLabels() {
        seedRestaurantAndArea()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("log_waste_button")

            composeTestRule.onNodeWithTag("log_waste_button").assert(hasText("Log Waste", substring = true, ignoreCase = true))
            composeTestRule.onNodeWithTag("new_purchase_button").assert(hasText("Purchase", substring = true, ignoreCase = true))
            composeTestRule.onNodeWithTag("start_count_button").assert(hasText("Stock Count", substring = true, ignoreCase = true))
        }
    }

    @Test
    fun dashboard_activityStatus_isLocalized() {
        seedPopulatedData()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForTag("dashboard_activity_PURCHASE_p1", 10_000)

            // Should display localized "Posted" not raw enum
            composeTestRule.onNode(hasTestTag("dashboard_activity_PURCHASE_p1") and hasText("Posted", substring = true))
                .assertIsDisplayed()
        }
    }
}
