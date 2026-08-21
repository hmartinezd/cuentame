package com.venkoi.restaurantops.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.venkoi.restaurantops.MainActivity
import com.venkoi.restaurantops.core.backup.api.RestoreStartupState
import com.venkoi.restaurantops.core.backup.internal.RestoreOperationGate
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.database.entity.*
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.core.model.inventory.InventoryMovementType
import com.venkoi.restaurantops.core.model.inventory.SourceDocumentType
import com.venkoi.restaurantops.core.model.inventory.StockCountStatus
import com.venkoi.restaurantops.core.preferences.repository.AppPreferencesRepository
import com.venkoi.restaurantops.feature.waste.ui.waitForTag
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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first

@HiltAndroidTest
class ReportsUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var db: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    @Inject
    lateinit var restoreGate: RestoreOperationGate

    private val testNow = Instant.now()

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            db.clearAllTables()
            preferencesRepository.clearAll()
            db.unitDao().insertSeedUnits(com.venkoi.restaurantops.core.database.seed.UnitSeeds.ALL_UNITS)
            preferencesRepository.setAppLocaleTag("en-US")
            restoreGate.updateRecoveryState(RestoreStartupState.Ready)
        }
    }

    private fun seedReadyState(name: String = "Test Restaurant") = runBlocking {
        val restId = "rest-1"
        db.restaurantDao().insert(RestaurantEntity(restId, name, "USD", "en-US", testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area-1", restId, "Main Area", "main area", 1, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        preferencesRepository.setOnboardingCompleted(true)
        
        assertThat(db.restaurantDao().observeRestaurant().first()?.id).isEqualTo(restId)
        assertThat(preferencesRepository.observePreferences().first().onboardingCompleted).isTrue()
    }

    private fun seedPopulatedData() = runBlocking {
        val restId = "rest-1"
        seedReadyState("Test Restaurant")
        
        // 1. Inventory: A(valued), B(no cost), C(negative balance), D(no options, no stock)
        db.ingredientDao().insert(IngredientEntity("ing-a", restId, "Ing A", "ing a", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-a", "ing-a", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-a", "area-1", "10.0", testNow.toEpochMilli()))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId, "ing-a", "2.0", testNow.toEpochMilli()))

        db.ingredientDao().insert(IngredientEntity("ing-b", restId, "Ing B", "ing b", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-b", "ing-b", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-b", "area-1", "5.0", testNow.toEpochMilli()))

        db.ingredientDao().insert(IngredientEntity("ing-c", restId, "Ing C", "ing c", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-c", "ing-c", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-c", "area-1", "-1.0", testNow.toEpochMilli()))

        db.ingredientDao().insert(IngredientEntity("ing-d", restId, "Ing D", "ing d", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        // No options for ing-d. No stock projection for ing-d.

        // 2. Purchases
        // Current POSTED: 100
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(1, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-a", "area-1", "opt-a", "5", "5", "100.0", "1", null, 0L, 0L))
        // Previous POSTED: 50
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_old", restId, null, null, testNow.minus(40, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l_old", "p_old", "ing-a", "area-1", "opt-a", "5", "5", "50.0", "1", null, 0L, 0L))
        // Current DRAFT (Excluded): 700
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_draft", restId, null, null, testNow.minus(2, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.DRAFT.name, null, null, null, 0L, 0L, null, null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l_draft", "p_draft", "ing-a", "area-1", "opt-a", "1", "1", "700.0", "1", null, 0L, 0L))
        // Current VOIDED (Excluded): 900
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_void", restId, null, null, testNow.minus(3, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.VOIDED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), testNow.toEpochMilli()))
        db.purchaseDao().insertLine(PurchaseLineEntity("l_void", "p_void", "ing-a", "area-1", "opt-a", "1", "1", "900.0", "1", null, 0L, 0L))

        // 3. Waste
        // Current POSTED: 10 (Historical)
        db.wasteDao().insert(WasteEventEntity("w1", restId, "ing-a", "area-1", "opt-a", "1", "1", "SPOILED", testNow.minus(4, ChronoUnit.HOURS).toEpochMilli(), null, null, null, DocumentStatus.POSTED.name, 0L, 0L, testNow.toEpochMilli(), null))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m1", restId, "ing-a", "area-1", InventoryMovementType.WASTE.name, "-1", "1", "10.0", testNow.minus(4, ChronoUnit.HOURS).toEpochMilli(), SourceDocumentType.WASTE_EVENT.name, "w1", "op-1", "m1", null, 0L))
        // Previous POSTED: 4
        db.wasteDao().insert(WasteEventEntity("w_old", restId, "ing-a", "area-1", "opt-a", "1", "1", "SPOILED", testNow.minus(40, ChronoUnit.DAYS).toEpochMilli(), null, null, null, DocumentStatus.POSTED.name, 0L, 0L, testNow.toEpochMilli(), null))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m_old", restId, "ing-a", "area-1", InventoryMovementType.WASTE.name, "-1", "1", "4.0", testNow.minus(40, ChronoUnit.DAYS).toEpochMilli(), SourceDocumentType.WASTE_EVENT.name, "w_old", "op-2", "m_old", null, 0L))
        // Current VOIDED (Excluded): 20
        db.wasteDao().insert(WasteEventEntity("w_void", restId, "ing-a", "area-1", "opt-a", "1", "1", "SPOILED", testNow.minus(5, ChronoUnit.HOURS).toEpochMilli(), null, null, null, DocumentStatus.VOIDED.name, 0L, 0L, testNow.toEpochMilli(), testNow.toEpochMilli()))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m_void", restId, "ing-a", "area-1", InventoryMovementType.WASTE.name, "-1", "1", "20.0", testNow.minus(5, ChronoUnit.HOURS).toEpochMilli(), SourceDocumentType.WASTE_EVENT.name, "w_void", "op-3", "m_void", null, 0L))

        // 4. Stock Count
        // COMPLETED count: 1
        db.stockCountDao().insertCount(StockCountEntity("c1", restId, "Completed Count", testNow.toEpochMilli(), testNow.toEpochMilli(), testNow.toEpochMilli(), StockCountStatus.COMPLETED.name, null, 0L, 0L, null))
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("ca1", "c1", "area-1", "COMPLETED", testNow.toEpochMilli(), testNow.toEpochMilli(), 1)))
        // 1 non-zero adjustment
        db.stockCountDao().insertCountLine(StockCountLineEntity("l_adj", "ca1", "ing-a", "opt-a", "10", "10", "5", "5", null, 0L, 0L))
        // 1 zero adjustment (Excluded from count)
        db.stockCountDao().insertCountLine(StockCountLineEntity("l_zero", "ca1", "ing-b", "opt-b", "10", "10", "0", "0", null, 0L, 0L))
        
        // DRAFT count (Excluded)
        db.stockCountDao().insertCount(StockCountEntity("c_draft", restId, "Draft Count", testNow.toEpochMilli(), testNow.toEpochMilli(), null, StockCountStatus.DRAFT.name, null, 0L, 0L, null))
    }

    @Test
    fun reports_populatedData_verification() {
        seedPopulatedData()
        
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasTestTag("nav_reports")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            
            composeTestRule.waitForTag("reports_screen")
            val scrollable = composeTestRule.onNode(hasScrollAction())

            // 1. Header and range
            composeTestRule.onNodeWithTag("reports_header", useUnmergedTree = true).onChildren()
                .filterToOne(hasText("30 days", substring = true)).assertExists()
            
            // 2. Inventory: Value = 20 (10 lb * 2.0). Ratio 1 / 3 (A, B, C are stocked. D is not). Percentage 33.3%
            scrollable.performScrollToNode(hasTestTag("reports_inventory_section"))
            composeTestRule.onNodeWithTag("reports_inventory_value", useUnmergedTree = true).onChildren()
                .filter(hasText("20.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_inventory_coverage", useUnmergedTree = true).onChildren()
                .filter(hasText("1 / 3", substring = true)).onFirst().assertExists()
            
            // 3. Purchase: Current 100, Previous 50. Absolute 50. Trend 100%
            scrollable.performScrollToNode(hasTestTag("reports_purchase_section"))
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("100.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_purchase_section_previous", useUnmergedTree = true).onChildren()
                .filter(hasText("50.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_purchase_section_absolute", useUnmergedTree = true).onChildren()
                .filter(hasText("50.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_purchase_section_trend", useUnmergedTree = true).onChildren()
                .filter(hasText("100.0%", substring = true)).onFirst().assertExists()
            
            // 4. Waste: Current 10, Previous 4. Absolute 6. Trend 150%
            scrollable.performScrollToNode(hasTestTag("reports_waste_section"))
            composeTestRule.onNodeWithTag("reports_waste_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("10.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_waste_section_previous", useUnmergedTree = true).onChildren()
                .filter(hasText("4.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_waste_section_absolute", useUnmergedTree = true).onChildren()
                .filter(hasText("6.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_waste_section_trend", useUnmergedTree = true).onChildren()
                .filter(hasText("150.0%", substring = true)).onFirst().assertExists()
            
            // 5. Alerts: 1 negative (C), 2 missing cost (B, C), 1 missing options (D)
            scrollable.performScrollToNode(hasTestTag("reports_alerts_section"))
            composeTestRule.onNodeWithTag("reports_negative_balances", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_missing_costs", useUnmergedTree = true).onChildren()
                .filter(hasText("2")).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_missing_unit_options", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
            
            // 6. Counts: 1 completed, 1 adjusted line
            scrollable.performScrollToNode(hasTestTag("reports_stock_count_section"))
            composeTestRule.onNodeWithTag("reports_completed_counts", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_adjusted_lines", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
            
            // 7. Top Waste Row
            scrollable.performScrollToNode(hasTestTag("reports_top_waste_ing-a"))
            composeTestRule.onNodeWithTag("reports_top_waste_ing-a", useUnmergedTree = true).assertExists()
            composeTestRule.onNodeWithText("Ing A", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun reports_rangeSwitching_7_days() {
        runBlocking {
            seedReadyState("Range 7")
            val restId = "rest-1"
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))

            // 5 days ago: 70
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_7", restId, null, null, testNow.minus(5, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p_7", "ing-1", "area-1", "opt-1", "1", "1", "70.0", "1", null, 0L, 0L))
            
            // 15 days ago: 100
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_30", restId, null, null, testNow.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l2", "p_30", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
        }

        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            composeTestRule.waitForTag("reports_screen")
            
            // 1. Initial 30 days selected
            composeTestRule.onNodeWithTag("reports_range_30").assertIsSelected()
            
            // 2. Initial 30 days value: 170.00
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filterToOne(hasText("170.00", substring = true)).assertExists()
            
            // 3. Switch to 7 days
            composeTestRule.onNodeWithTag("reports_range_7").performClick()
            composeTestRule.onNodeWithTag("reports_range_7").assertIsSelected()
            
            // 4. Result 70.00
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasText("70.00", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filterToOne(hasText("70.00", substring = true)).assertExists()
            
            // 5. Confirm 170.00 is no longer the current value
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("170.00", substring = true)).onFirst().assertDoesNotExist()
        }
    }

    @Test
    fun reports_rangeSwitching_90_days() {
        runBlocking {
            seedReadyState("Range 90")
            val restId = "rest-1"
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))

            // 15 days ago: 100
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_30", restId, null, null, testNow.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p_30", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
            
            // 60 days ago: 200
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_90", restId, null, null, testNow.minus(60, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l2", "p_90", "ing-1", "area-1", "opt-1", "1", "1", "200.0", "1", null, 0L, 0L))
        }

        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            composeTestRule.waitForTag("reports_screen")
            
            // Initial 30 days: 100.00
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filterToOne(hasText("100.00", substring = true)).assertExists()
            
            // Switch to 90 days
            composeTestRule.onNodeWithTag("reports_range_90").performClick()
            composeTestRule.onNodeWithTag("reports_range_90").assertIsSelected()
            
            // Result 300.00 (100 + 200)
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasText("300.00", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filterToOne(hasText("300.00", substring = true)).assertExists()
            
            // Confirm 100.00 is no longer the current value
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("100.00", substring = true)).onFirst().assertDoesNotExist()
        }
    }

    @Test
    fun reports_navigation_homeToReports_andBack() {
        seedReadyState()
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.waitForTag("home_dashboard_list")
            composeTestRule.onNodeWithTag("home_dashboard_list", useUnmergedTree = true)
                .performScrollToNode(hasTestTag("view_reports_button"))
            composeTestRule.onNodeWithTag("view_reports_button").performClick()
            
            composeTestRule.waitUntil(30_000) {
                composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reports_screen").assertIsDisplayed()
            
            // System back
            androidx.test.espresso.Espresso.pressBack()
            
            composeTestRule.waitForTag("home_screen")
            composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        }
    }

    @Test
    fun reportsOverview_rangeRefresh_noFlicker() {
        seedReadyState("Flicker Test")
        runBlocking {
            db.ingredientDao().insert(IngredientEntity("ing-1", "rest-1", "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", "rest-1", null, null, testNow.minus(2, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
        }

        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            composeTestRule.waitForTag("reports_screen")

            // 1. Initial data visible
            composeTestRule.onNodeWithTag("reports_header").assertIsDisplayed()
            
            // 2. Change range
            composeTestRule.onNodeWithTag("reports_range_7").performClick()

            // 3. Verify elements remain (proving no full-screen flicker)
            composeTestRule.onNodeWithTag("reports_header").assertIsDisplayed()
            composeTestRule.onNodeWithTag("reports_date_range_selector").assertIsDisplayed()
            
            // 4. Verify full-screen loading does NOT appear
            composeTestRule.onNodeWithTag("reports_loading").assertDoesNotExist()

            // 5. Wait for refresh to complete
            composeTestRule.waitForIdle()
        }
    }
}
