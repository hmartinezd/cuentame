package com.miara.cuentame.feature.preparations.navigation

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.MainActivity
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PreparationsNavigationStackTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        hiltRule.inject()
        navController = TestNavHostController(ApplicationProvider.getApplicationContext<android.app.Application>())
        composeTestRule.setContent {
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            NavHost(navController, startDestination = Destination.PREPARATION_RECIPE_LIST.route) {
                preparationsGraph(
                    navController = navController,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }

    @Test
    fun componentToDetail_removesDraftFromStack() {
        val recipeId = PreparationRecipeId("rec1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDraft(recipeId))
            navController.navigate(AppRoutes.preparationRecipeComponentCreate(recipeId))
        }

        assertThat(navController.currentBackStackEntry?.destination?.route)
            .isEqualTo(Destination.PREPARATION_RECIPE_COMPONENT_CREATE.route)

        // Simulate NavigateToDetail event logic as in PreparationsNavigation.kt
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDetail(recipeId)) {
                popUpTo(Destination.PREPARATION_RECIPE_DRAFT.route) { inclusive = true }
                launchSingleTop = true
            }
        }

        assertThat(navController.currentBackStackEntry?.destination?.route)
            .isEqualTo(Destination.PREPARATION_RECIPE_DETAIL.route)
        
        val backStack = navController.currentBackStack.value
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_DRAFT.route }).isFalse()
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_COMPONENT_CREATE.route }).isFalse()
    }

    @Test
    fun detailToEditor_removesDetailFromStack() {
        val recipeId = PreparationRecipeId("rec1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDetail(recipeId))
        }

        assertThat(navController.currentBackStackEntry?.destination?.route)
            .isEqualTo(Destination.PREPARATION_RECIPE_DETAIL.route)

        // Simulate NavigateToEditor event logic as in PreparationsNavigation.kt
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDraft(recipeId)) {
                popUpTo(Destination.PREPARATION_RECIPE_DETAIL.route) { inclusive = true }
                launchSingleTop = true
            }
        }

        assertThat(navController.currentBackStackEntry?.destination?.route)
            .isEqualTo(Destination.PREPARATION_RECIPE_DRAFT.route)
        
        val backStack = navController.currentBackStack.value
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_DETAIL.route }).isFalse()
    }
}
