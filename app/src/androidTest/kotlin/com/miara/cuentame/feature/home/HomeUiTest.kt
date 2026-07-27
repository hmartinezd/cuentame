package com.miara.cuentame.feature.home

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.RestaurantInventoryDatabase
import com.miara.cuentame.core.database.entity.IngredientCostProjectionEntity
import com.miara.cuentame.core.database.entity.IngredientEntity
import com.miara.cuentame.core.database.entity.IngredientUnitOptionEntity
import com.miara.cuentame.core.database.entity.InventoryAreaEntity
import com.miara.cuentame.core.database.entity.InventoryBalanceProjectionEntity
import com.miara.cuentame.core.database.entity.PurchaseLineEntity
import com.miara.cuentame.core.database.entity.PurchaseReceiptEntity
import com.miara.cuentame.core.database.entity.RestaurantEntity
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
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
        db.inventoryAreaDao().upsert(InventoryAreaEntity("area-2", restId, "Secondary Area", "secondary area", 2, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
        preferencesRepository.setOnboardingCompleted(true)
        
        assertThat(db.restaurantDao().observeRestaurant().first()?.id).isEqualTo(restId)
    }

    @Test
    fun app_routesToOnboarding_whenSetupIsIncomplete() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasTestTag("onboarding_screen_root")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun dashboard_emptyState_whenNoActivity() {
        seedReadyState("Empty Rest")
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasTestTag("dashboard_inventory_value")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("dashboard_restaurant_name", useUnmergedTree = true).assertTextEquals("Empty Rest")
        }
    }

    @Test
    fun dashboard_fullVerification_populatedData() {
        runBlocking {
            seedReadyState("Test Restaurant")
            val restId = "rest-1"
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.inventoryProjectionDao().upsert(InventoryBalanceProjectionEntity(restId, "ing-1", "area-1", "10.0", testNow.toEpochMilli()))
            db.ingredientCostProjectionDao().upsert(IngredientCostProjectionEntity(restId, "ing-1", "2.0", testNow.toEpochMilli()))
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p1", restId, null, null, testNow.minus(1, ChronoUnit.HOURS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l1", "p1", "ing-1", "area-1", "opt-1", "5", "5", "100.0", "1", null, 0L, 0L))
        }
        
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodes(hasTestTag("dashboard_inventory_value")).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("dashboard_restaurant_name", useUnmergedTree = true).assertTextEquals("Test Restaurant")
            
            // Assert authoritative values
            composeTestRule.onAllNodes(hasText("$20.00", substring = true), useUnmergedTree = true).onFirst().assertExists()
            composeTestRule.onAllNodes(hasText("$100.00", substring = true), useUnmergedTree = true).onFirst().assertExists()
            
            // Navigation to Reports
            val scrollable = composeTestRule.onNode(hasScrollAction())
            scrollable.performScrollToNode(hasTestTag("view_reports_button"))
            composeTestRule.onNodeWithTag("view_reports_button").performClick()
            
            // Wait for reports screen
            composeTestRule.waitUntil(10_000) {
                composeTestRule.onAllNodes(hasTestTag("reports_screen")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun dashboard_rangeSwitching_updatesValues() {
        runBlocking {
            seedReadyState("Range Rest")
            val restId = "rest-1"
            db.ingredientDao().insert(IngredientEntity("ing-1", restId, "Chicken", "chicken", null, "mass_lb", null, null, null, null, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.ingredientUnitOptionDao().insert(IngredientUnitOptionEntity("opt-1", "ing-1", "lb", "lb", null, BigDecimal.ONE, true, true, true, true, testNow.toEpochMilli(), testNow.toEpochMilli(), null))
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_recent", restId, null, null, testNow.minus(2, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l_recent", "p_recent", "ing-1", "area-1", "opt-1", "1", "1", "50.0", "1", null, 0L, 0L))
            db.purchaseDao().insertReceipt(PurchaseReceiptEntity("p_old", restId, null, null, testNow.minus(15, ChronoUnit.DAYS).toEpochMilli(), DocumentStatus.POSTED.name, null, null, 0L, 0L, testNow.toEpochMilli(), null))
            db.purchaseDao().insertLine(PurchaseLineEntity("l_old", "p_old", "ing-1", "area-1", "opt-1", "1", "1", "100.0", "1", null, 0L, 0L))
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasText("150", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onNodeWithTag("home_range_7").performClick()
            
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasText("50", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun dashboard_navigation_quickActions() {
        seedReadyState()
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasTestTag("home_date_range_selector")).fetchSemanticsNodes().isNotEmpty()
            }
            val scrollable = composeTestRule.onNode(hasScrollAction())
            scrollable.performScrollToNode(hasTestTag("log_waste_button"))
            composeTestRule.onNodeWithTag("log_waste_button").performClick()
            
            composeTestRule.waitUntil(20_000) {
                composeTestRule.onAllNodes(hasTestTag("waste_form_screen")).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
