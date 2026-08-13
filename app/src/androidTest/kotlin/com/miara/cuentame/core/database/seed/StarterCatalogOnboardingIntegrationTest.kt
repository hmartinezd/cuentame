package com.miara.cuentame.core.database.seed

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.database.dao.IngredientCategoryDao
import com.miara.cuentame.core.database.dao.IngredientDao
import com.miara.cuentame.core.database.dao.RestaurantDao
import com.miara.cuentame.test.TestStateManager
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
    fun onboarding_seedsStarterCatalogForNewRestaurant() {
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

            // Verify Seed
            runBlocking {
                val restaurant = restaurantDao.getRestaurant()
                android.util.Log.d("StarterCatalogTest", "Found restaurant: $restaurant")
                if (restaurant != null) {
                    val ingredients = ingredientDao.getAllIngredients(restaurant.id)
                    android.util.Log.d("StarterCatalogTest", "Found ingredients: ${ingredients.size}")
                    assertThat(ingredients).hasSize(89)
                    // ...
                } else {
                    error("Restaurant not found after onboarding!")
                }
            }
        }
    }
}
