package com.miara.cuentame.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.*
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.model.inventory.InventoryMovementType
import com.miara.cuentame.core.model.inventory.SourceDocumentType
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
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
class DetailedReportsUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var db: RestaurantInventoryDatabase

    @Inject
    lateinit var preferencesRepository: AppPreferencesRepository

    private val testNow = Instant.now()
    private val restId = "rest-1"

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            db.clearAllTables()
            preferencesRepository.clearAll()
            db.unitDao().insertSeedUnits(com.miara.cuentame.core.database.seed.UnitSeeds.ALL_UNITS)
            preferencesRepository.setAppLocaleTag("en-US")
            
            db.restaurantDao().insert(RestaurantEntity(restId, "Test Restaurant", "USD", "en-US", testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.inventoryAreaDao().upsert(InventoryAreaEntity("area-1", restId, "Main Area", "main area", 1, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    private fun navigateToReports() {
        ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitUntil(20_000) {
            composeTestRule.onAllNodes(hasTestTag("nav_reports")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_reports").performClick()
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun reports_to_inventoryDetail() {
        runBlocking {
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-1", "area-1", "10.0", testNow.toEpochMilli()))
            db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId, "ing-1", "2.50", testNow.toEpochMilli()))
        }

        navigateToReports()
        
        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("reports_view_inventory_details"))
        composeTestRule.onNodeWithTag("reports_view_inventory_details").performClick()

        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasTestTag("inventory_report_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("inventory_report_total_value")
            .assertTextContains("$25.00", substring = true)
        composeTestRule.onNodeWithTag("inventory_report_item_ing-1").assertIsDisplayed()
    }

    @Test
    fun reports_to_purchaseDetail_withInheritedRange() {
        runBlocking {
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            
            // Recent purchase (2 days ago)
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(2, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
            
            // Older purchase (15 days ago)
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p2", restId, null, null, testNow.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l2", "p2", "ing-1", "area-1", "opt-1", "1", "1", "200.0", "1", null, 0L, 0L))
        }

        navigateToReports()
        
        // Select 7 days range in Overview
        composeTestRule.onNodeWithTag("reports_range_7").performClick()
        
        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("reports_view_purchase_details"))
        composeTestRule.onNodeWithTag("reports_view_purchase_details").performClick()

        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasTestTag("purchase_report_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        // Inherited range should be 7 days, so only p1 ($100.00) visible
        composeTestRule.onNodeWithTag("purchase_report_total")
            .assertTextContains("$100.00", substring = true)
        composeTestRule.onNodeWithTag("purchase_report_item_p1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("purchase_report_item_p2").assertDoesNotExist()
        
        // Change range in Detail to 30 days
        composeTestRule.onNodeWithTag("purchase_report_range_30").performClick()
        composeTestRule.onNodeWithTag("purchase_report_item_p2").assertIsDisplayed()
    }

    @Test
    fun reports_to_wasteDetail_withInheritedRange() {
        runBlocking {
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))

            val waste = WasteEventEntity("w1", restId, "ing-1", "area-1", "opt-1", "10", "1", "SPOILED", testNow.toEpochMilli(), null, null, DocumentStatus.POSTED.name, 0L, 0L, testNow.toEpochMilli(), null)
            db.wasteDao().insert(waste)
            
            val movement = InventoryMovementEntity("m1", restId, "ing-1", "area-1", InventoryMovementType.WASTE.name, "-10", "1", "50.0", 0L, SourceDocumentType.WASTE_EVENT.name, "w1", "op1", "m1", null, 0L)
            db.inventoryMovementDao().insert(movement)
        }

        navigateToReports()
        
        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("reports_view_waste_details"))
        composeTestRule.onNodeWithTag("reports_view_waste_details").performClick()

        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasTestTag("waste_report_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("waste_report_total")
            .assertTextContains("$50.00", substring = true)
        composeTestRule.onNodeWithTag("waste_report_item_w1").assertIsDisplayed()
    }

    @Test
    fun purchaseDetail_rangeRefresh_noFlicker() {
        runBlocking {
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(2, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
        }

        navigateToReports()
        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("reports_view_purchase_details"))
        composeTestRule.onNodeWithTag("reports_view_purchase_details").performClick()

        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasTestTag("purchase_report_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Initial data visible
        composeTestRule.onNodeWithTag("purchase_report_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("purchase_report_item_p1").assertIsDisplayed()

        // 2. Change range
        composeTestRule.onNodeWithTag("purchase_report_range_7").performClick()

        // 3. Verify elements remain (proving no full-screen flicker)
        composeTestRule.onNodeWithTag("purchase_report_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("purchase_report_range_selector").assertIsDisplayed()
        
        // 4. Verify full-screen loading does NOT appear
        composeTestRule.onNodeWithTag("purchase_report_loading").assertDoesNotExist()

        // 5. Wait for refresh to complete
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("purchase_report_item_p1").assertIsDisplayed()
    }

    @Test
    fun wasteDetail_rangeRefresh_noFlicker() {
        runBlocking {
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))

            val waste = WasteEventEntity("w1", restId, "ing-1", "area-1", "opt-1", "10", "1", "SPOILED", testNow.toEpochMilli(), null, null, DocumentStatus.POSTED.name, 0L, 0L, testNow.toEpochMilli(), null)
            db.wasteDao().insert(waste)
            
            val movement = InventoryMovementEntity("m1", restId, "ing-1", "area-1", InventoryMovementType.WASTE.name, "-10", "1", "50.0", 0L, SourceDocumentType.WASTE_EVENT.name, "w1", "op1", "m1", null, 0L)
            db.inventoryMovementDao().insert(movement)
        }

        navigateToReports()
        val scrollable = composeTestRule.onNode(hasScrollAction())
        scrollable.performScrollToNode(hasTestTag("reports_view_waste_details"))
        composeTestRule.onNodeWithTag("reports_view_waste_details").performClick()

        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodes(hasTestTag("waste_report_screen")).fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Initial data visible
        composeTestRule.onNodeWithTag("waste_report_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("waste_report_item_w1").assertIsDisplayed()

        // 2. Change range
        composeTestRule.onNodeWithTag("waste_report_range_7").performClick()

        // 3. Verify elements remain (proving no full-screen flicker)
        composeTestRule.onNodeWithTag("waste_report_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("waste_report_range_selector").assertIsDisplayed()
        
        // 4. Verify full-screen loading does NOT appear
        composeTestRule.onNodeWithTag("waste_report_loading").assertDoesNotExist()

        // 5. Wait for refresh to complete
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("waste_report_item_w1").assertIsDisplayed()
    }
}
