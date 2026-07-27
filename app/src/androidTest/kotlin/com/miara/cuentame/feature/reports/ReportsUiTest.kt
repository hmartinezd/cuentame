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
        db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        
        db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-1", "area-1", "10.0", testNow.toEpochMilli()))
        db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId, "ing-1", "2.0", testNow.toEpochMilli()))

        // Current period purchase
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(1, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-1", "area-1", "opt-1", "5", "5", "100.0", "1", null, 0L, 0L))

        // Previous period purchase (40 days ago)
        db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_old", restId, null, null, testNow.minus(40, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
        db.purchaseDao().insertLine(PurchaseLineEntity("l_old", "p_old", "ing-1", "area-1", "opt-1", "5", "5", "50.0", "1", null, 0L, 0L))
    }

    @Test
    fun reports_populatedData_verification() {
        seedPopulatedData()
        
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use {
            // Navigate from Home
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasTestTag("view_reports_button")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("view_reports_button"))
            composeTestRule.onNodeWithTag("view_reports_button").performClick()
            
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("reports_header").assertIsDisplayed()
            composeTestRule.onNodeWithText("Test Restaurant").assertIsDisplayed()
            
            composeTestRule.onNodeWithTag("reports_inventory_section").assertIsDisplayed()
            composeTestRule.onAllNodes(hasText("20.00", substring = true)).onFirst().assertExists()
            
            composeTestRule.onNodeWithTag("reports_purchase_section").assertIsDisplayed()
            composeTestRule.onAllNodes(hasText("100.00", substring = true)).onFirst().assertExists()
            composeTestRule.onAllNodes(hasText("50.00", substring = true)).onFirst().assertExists()
            composeTestRule.onAllNodes(hasText("Increase", substring = true)).onFirst().assertExists()
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
