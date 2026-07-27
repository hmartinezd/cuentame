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
    }

    private fun seedPopulatedData() = runBlocking {
        val restId = "rest-1"
        seedReadyState("Test Restaurant")
        
        // 1. Inventory: 1 valued, 1 missing cost, 1 negative area balance
        db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Valued", "valued", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-1", "area-1", "10.0", testNow.toEpochMilli()))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId, "ing-1", "2.0", testNow.toEpochMilli()))

        db.ingredientDao().insert(IngredientEntity("ing-2", restId, "No Cost", "no cost", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-2", "ing-2", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-2", "area-1", "5.0", testNow.toEpochMilli()))
        // No cost projection for ing-2

        db.ingredientDao().insert(IngredientEntity("ing-3", restId, "Negative", "negative", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-3", "ing-3", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-3", "area-1", "-1.0", testNow.toEpochMilli()))

        // 2. Purchases
        // Current period: 100
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(1, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-1", "area-1", "opt-1", "5", "5", "100.0", "1", null, 0L, 0L))
        // Previous period: 50
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_old", restId, null, null, testNow.minus(40, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l_old", "p_old", "ing-1", "area-1", "opt-1", "5", "5", "50.0", "1", null, 0L, 0L))

        // 3. Waste
        // Current period: 10
        val wid = "w1"
        db.wasteDao().insert(WasteEventEntity(wid, restId, "ing-1", "area-1", "opt-1", "1", "1", "SPOILED", testNow.minus(2, ChronoUnit.HOURS).toEpochMilli(), null, null, DocumentStatus.POSTED.name, 0L, 0L, testNow.toEpochMilli(), null))
        db.inventoryMovementDao().insert(InventoryMovementEntity("m1", restId, "ing-1", "area-1", "WASTE", "-1", "1", "10.0", testNow.minus(2, ChronoUnit.HOURS).toEpochMilli(), "WASTE_EVENT", wid, "op-1", "m1", null, 0L))

        // 4. Stock Count: 1 completed
        val cid = "c1"
        db.stockCountDao().insertCount(StockCountEntity(cid, restId, "Completed Count", testNow.toEpochMilli(), testNow.toEpochMilli(), testNow.toEpochMilli(), "COMPLETED", null, 0L, 0L, null))
        db.stockCountDao().insertCountAreas(listOf(StockCountAreaEntity("ca1", cid, "area-1", "COMPLETED", testNow.toEpochMilli(), testNow.toEpochMilli(), 1)))
        db.stockCountDao().insertCountLine(StockCountLineEntity("cl1", "ca1", "ing-1", "opt-1", "10", "10", "5", "5", null, 0L, 0L))
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
            composeTestRule.onNodeWithTag("reports_header", useUnmergedTree = true).assertIsDisplayed()
            
            // 2. Inventory Section: Value = 20 (10 lb * 2.0)
            scrollable.performScrollToNode(hasTestTag("reports_inventory_section"))
            composeTestRule.onNodeWithTag("reports_inventory_section", useUnmergedTree = true).onChildren()
                .filter(hasText("20.00", substring = true)).onFirst().assertExists()
            
            // 3. Purchase Section: 100 current, 50 previous
            scrollable.performScrollToNode(hasTestTag("reports_purchase_section"))
            composeTestRule.onNodeWithTag("reports_purchase_section", useUnmergedTree = true).onChildren()
                .filter(hasText("100.00", substring = true)).onFirst().assertExists()
            
            // 4. Waste Section: 10 current, 0 previous
            scrollable.performScrollToNode(hasTestTag("reports_waste_section"))
            composeTestRule.onNodeWithTag("reports_waste_section", useUnmergedTree = true).onChildren()
                .filter(hasText("10.00", substring = true)).onFirst().assertExists()
            
            // 5. Alerts
            scrollable.performScrollToNode(hasTestTag("reports_alerts_section"))
            composeTestRule.onNodeWithTag("reports_alerts_section", useUnmergedTree = true).onChildren()
                .filter(hasText("1")).onFirst().assertExists() // Negative: ing-3
            
            // 6. Top Waste
            scrollable.performScrollToNode(hasTestTag("reports_top_waste_list"))
            composeTestRule.onNodeWithTag("reports_top_waste_ing-1", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun reports_rangeSwitching_verification() {
        runBlocking {
            seedReadyState("Range Reports")
            val restId = "rest-1"
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))

            // Purchase 1: 5 days ago (Inside 7 and 30)
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_7", restId, null, null, testNow.minus(5, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p_7", "ing-1", "area-1", "opt-1", "1", "1", "70.0", "1", null, 0L, 0L))
            
            // Purchase 2: 15 days ago (Inside 30, Outside 7)
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_30", restId, null, null, testNow.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l2", "p_30", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
        }

        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasTestTag("nav_reports")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("nav_reports").performClick()
            
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasText("170", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("reports_range_7").performClick()
            
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasText("70", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
