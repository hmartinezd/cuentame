package com.venkoi.cuentame.core.database.seed

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.venkoi.cuentame.MainActivity
import com.venkoi.cuentame.core.database.dao.IngredientCategoryDao
import com.venkoi.cuentame.core.database.dao.IngredientDao
import com.venkoi.cuentame.core.database.dao.InventoryAreaDao
import com.venkoi.cuentame.core.database.dao.RestaurantDao
import com.venkoi.cuentame.core.database.dao.UnitDao
import com.venkoi.cuentame.test.TestStateManager
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class StarterCatalogOnboardingIntegrationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var testStateManager: TestStateManager

    @Inject
    lateinit var restaurantDao: RestaurantDao

    @Inject
    lateinit var ingredientDao: IngredientDao

    @Inject
    lateinit var categoryDao: IngredientCategoryDao

    @Inject
    lateinit var inventoryAreaDao: InventoryAreaDao

    @Inject
    lateinit var unitDao: UnitDao

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking { testStateManager.resetAll() }
    }

    @After
    fun tearDown() {
        runBlocking { testStateManager.resetAll() }
    }

    @Test
    fun cleanOnboarding_createsSetupDataButNoIngredients() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Welcome step
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_setup_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_setup_button", useUnmergedTree = true).performClick()

            // 2. Restaurant step
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_restaurant_name", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_restaurant_name", useUnmergedTree = true).performTextInput("Cuban Foodies")
            composeTestRule.onNodeWithTag("onboarding_next_button", useUnmergedTree = true).performClick()

            // 3. Areas step
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_next_button", useUnmergedTree = true).performClick()

            // 4. Categories step
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_next_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_next_button", useUnmergedTree = true).performClick()

            // 5. Review step
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("onboarding_finish_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag("onboarding_finish_button", useUnmergedTree = true).performClick()

            // 6. Home screen appears
            composeTestRule.waitUntil(30000) {
                composeTestRule.onAllNodesWithTag("home_screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }

            // System reference/setup data is created, but restaurant ingredient data is opt-in.
            runBlocking {
                val restaurant = restaurantDao.getRestaurant()
                if (restaurant != null) {
                    val ingredients = ingredientDao.getAllIngredients(restaurant.id)
                    assertThat(inventoryAreaDao.getActiveAreasSync(restaurant.id)).isNotEmpty()
                    assertThat(categoryDao.getAllCategoriesForRestaurant(restaurant.id)).isEmpty()
                    assertThat(unitDao.countSeededUnits()).isGreaterThan(0)
                    assertThat(ingredients).isEmpty()
                } else {
                    error("Restaurant not found after onboarding!")
                }
            }
        }
    }
}
