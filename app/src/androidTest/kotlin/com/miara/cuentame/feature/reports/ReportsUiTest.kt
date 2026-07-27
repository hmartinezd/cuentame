package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
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

    private val testNow = Instant.now()

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            db.clearAllTables()
            preferencesRepository.clearAll()
            db.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            preferencesRepository.setAppLocaleTag("en-US")
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
        
        // 1. Inventory: A(valued), B(no cost), C(negative balance)
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

        // 2. Purchases
        // Current: 100
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(1, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-a", "area-1", "opt-a", "5", "5", "100.0", "1", null, 0L, 0L))
        // Previous: 50
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_old", restId, null, null, testNow.minus(40, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l_old", "p_old", "ing-a", "area-1", "opt-a", "5", "5", "50.0", "1", null, 0L, 0L))

        // 3. Waste
        // Current: 10
        val wid = "w1"
        db.wasteDao().insert(WasteEventEntity(wid, restId, "ing-a", "area-1", "opt-a", "1", "1", "SPOILED", testNow.minus(2, ChronoUnit.HOURS).toEpochMilli(), null, null, DocumentStatus.POSTED.name, 0L, 0L, testNow.toEpochMilli(), null))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m1", restId, "ing-a", "area-1", "WASTE", "-1", "1", "10.0", testNow.minus(2, ChronoUnit.HOURS).toEpochMilli(), SourceDocumentType.WASTE_EVENT.name, wid, "op-1", "m1", null, 0L))

        // 4. Stock Count
        val cid = "c1"
        db.stockCountDao().insertCount(StockCountEntity(cid, restId, "Completed Count", testNow.toEpochMilli(), testNow.toEpochMilli(), testNow.toEpochMilli(), StockCountStatus.COMPLETED.name, null, 0L, 0L, null))
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("ca1", cid, "area-1", "COMPLETED", testNow.toEpochMilli(), testNow.toEpochMilli(), 1)))
        db.stockCountDao().insertCountLine(StockCountLineEntity("cl1", "ca1", "ing-a", "opt-a", "10", "10", "5", "5", null, 0L, 0L))

        // Ingredient with zero unit options for alert
        db.ingredientDao().insert(IngredientEntity("ing-no-opt", restId, "No Opt", "no opt", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
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
            
            // 2. Inventory: Value = 20 (10 lb * 2.0). Ratio 1 / 3.
            scrollable.performScrollToNode(hasTestTag("reports_inventory_section"))
            composeTestRule.onNodeWithTag("reports_inventory_value", useUnmergedTree = true).onChildren()
                .filter(hasText("20.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_inventory_coverage", useUnmergedTree = true).onChildren()
                .filter(hasText("1 / 3", substring = true)).onFirst().assertExists()
            
            // 3. Purchase: 100.00 current, 50.00 previous
            scrollable.performScrollToNode(hasTestTag("reports_purchase_section"))
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("100.00", substring = true)).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_purchase_section_previous", useUnmergedTree = true).onChildren()
                .filter(hasText("50.00", substring = true)).onFirst().assertExists()
            
            // 4. Waste: 10.00 current
            scrollable.performScrollToNode(hasTestTag("reports_waste_section"))
            composeTestRule.onNodeWithTag("reports_waste_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("10.00", substring = true)).onFirst().assertExists()
            
            // 5. Alerts: Negative balance count = 1 (ing-c), missing unit options = 1 (ing-no-opt)
            scrollable.performScrollToNode(hasTestTag("reports_alerts_section"))
            composeTestRule.onNodeWithTag("reports_negative_balances", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_missing_unit_options", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
            
            // 6. Counts: 1 completed, 1 adjusted line
            scrollable.performScrollToNode(hasTestTag("reports_stock_count_section"))
            composeTestRule.onNodeWithTag("reports_completed_counts", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
            composeTestRule.onNodeWithTag("reports_adjusted_lines", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists()
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
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_30", restId, null, null, testNow.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p_30", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
            
            // 60 days ago: 200
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_90", restId, null, null, testNow.minus(60, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l2", "p_90", "ing-1", "area-1", "opt-1", "1", "1", "200.0", "1", null, 0L, 0L))
        }

        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            composeTestRule.waitForTag("reports_screen")
            
            // Initial 30 days: 100
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("100.00", substring = true)).onFirst().assertExists()
            
            // Switch to 90 days
            composeTestRule.onNodeWithTag("reports_range_90").performClick()
            
            // Result 300 (100 + 200)
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasText("300.00", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filter(hasText("300.00", substring = true)).onFirst().assertExists()
            
            // Confirm 100.00 gone (as exact value)
            composeTestRule.onAllNodes(hasText("100.00", substring = false)).fetchSemanticsNodes().isEmpty()
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
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_7", restId, null, null, testNow.minus(5, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p_7", "ing-1", "area-1", "opt-1", "1", "1", "70.0", "1", null, 0L, 0L))
            
            // 15 days ago: 100
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_30", restId, null, null, testNow.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l2", "p_30", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
        }

        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            composeTestRule.waitForTag("reports_screen")
            
            // Initial 30 days: 170
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filterToOne(hasText("170.00", substring = true)).assertExists()
            
            // Switch to 7 days
            composeTestRule.onNodeWithTag("reports_range_7").performClick()
            
            // Result 70
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasText("70.00", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("reports_purchase_section_current", useUnmergedTree = true).onChildren()
                .filterToOne(hasText("70.00", substring = true)).assertExists()
            
            // Confirm 170 gone
            composeTestRule.onAllNodes(hasText("170.00", substring = false)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun reports_navigation_backBehavior() {
        seedReadyState()
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasTestTag("view_reports_button")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("view_reports_button"))
            composeTestRule.onNodeWithTag("view_reports_button").performClick()
            
            composeTestRule.waitForTag("reports_screen")
            
            // System back
            androidx.test.espresso.Espresso.pressBack()
            
            composeTestRule.waitForTag("home_screen")
        }
    }
}
