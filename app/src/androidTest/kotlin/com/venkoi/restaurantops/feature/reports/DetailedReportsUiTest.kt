package com.venkoi.restaurantops.feature.reports

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.venkoi.restaurantops.MainActivity
import com.venkoi.restaurantops.core.common.ids.*
import com.venkoi.restaurantops.core.database.RestaurantInventoryDatabase
import com.venkoi.restaurantops.core.domain.repository.PurchaseRepository
import com.venkoi.restaurantops.test.TestSeeder
import com.venkoi.restaurantops.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class DetailedReportsUiTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var database: RestaurantInventoryDatabase

    @Inject
    lateinit var purchaseRepository: PurchaseRepository

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

    @Test
    fun reports_display_seeded_values() {
        runBlocking {
            testStateManager.seedBaseline()
            TestSeeder.seedPostedPurchase(
                db = database,
                repo = purchaseRepository,
                restaurantId = RestaurantId(TestSeeder.RESTAURANT_ID),
                ingredientId = IngredientId(TestSeeder.ING_ID),
                areaId = InventoryAreaId(TestSeeder.AREA_ID),
                unitOptionId = IngredientUnitOptionId(TestSeeder.OPTION_ID),
                quantityEntered = java.math.BigDecimal.ONE,
                unitCostBase = java.math.BigDecimal("123.45"),
                effectiveAt = java.time.Instant.now()
            )
        }
        
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodes(hasTestTag("home_screen")).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("nav_reports", useUnmergedTree = true).performClick()
            
            // Wait for loading to finish and data to appear
            composeTestRule.waitUntil(15000) {
                composeTestRule.onAllNodesWithText("$123.45", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            
            composeTestRule.onAllNodesWithText("$123.45", substring = true)[0].assertIsDisplayed()
        } finally {
            scenario.close()
        }
    }
}
