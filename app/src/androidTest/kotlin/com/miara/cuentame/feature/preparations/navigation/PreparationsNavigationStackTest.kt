package com.miara.cuentame.feature.preparations.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreparationsNavigationStackTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: TestNavHostController

    @Before
    fun setup() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            NavHost(
                navController = navController,
                startDestination = Destination.PREPARATION_RECIPE_LIST.route
            ) {
                composable(Destination.PREPARATION_RECIPE_LIST.route) { Box(Modifier.testTag("list")) }
                composable(Destination.PREPARATION_RECIPE_CREATE.route) { Box(Modifier.testTag("create")) }
                composable(Destination.PREPARATION_RECIPE_DRAFT.route) { Box(Modifier.testTag("draft")) }
                composable(Destination.PREPARATION_RECIPE_COMPONENT_CREATE.route) { Box(Modifier.testTag("comp_create")) }
                composable(Destination.PREPARATION_RECIPE_COMPONENT_EDIT.route) { Box(Modifier.testTag("comp_edit")) }
                composable(Destination.PREPARATION_RECIPE_DETAIL.route) { Box(Modifier.testTag("detail")) }
            }
        }
    }

    @Test
    fun componentCreateToDetail_removesDraftAndComponentFromStack() {
        val recipeId = PreparationRecipeId("rec1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDraft(recipeId))
            navController.navigate(AppRoutes.preparationRecipeComponentCreate(recipeId))
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_COMPONENT_CREATE.route)

        composeTestRule.runOnUiThread {
            navController.replacePreparationDraftWithDetail(recipeId)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_DETAIL.route)
        
        val backStack = navController.currentBackStack.value
        assertThat(backStack.count { it.destination.route == Destination.PREPARATION_RECIPE_DETAIL.route }).isEqualTo(1)
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_DRAFT.route }).isFalse()
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_COMPONENT_CREATE.route }).isFalse()
        
        // Back returns to list
        composeTestRule.runOnUiThread { navController.popBackStack() }
        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_LIST.route)
    }

    @Test
    fun componentEditToDetail_removesDraftAndComponentFromStack() {
        val recipeId = PreparationRecipeId("rec1")
        val componentId = com.miara.cuentame.core.common.ids.PreparationRecipeComponentId("c1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDraft(recipeId))
            navController.navigate(AppRoutes.preparationRecipeComponentEdit(recipeId, componentId))
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_COMPONENT_EDIT.route)

        composeTestRule.runOnUiThread {
            navController.replacePreparationDraftWithDetail(recipeId)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_DETAIL.route)
        
        val backStack = navController.currentBackStack.value
        assertThat(backStack.count { it.destination.route == Destination.PREPARATION_RECIPE_DETAIL.route }).isEqualTo(1)
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_DRAFT.route }).isFalse()
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_COMPONENT_EDIT.route }).isFalse()
    }

    @Test
    fun detailToDraftEditor_removesDetailFromStack() {
        val recipeId = PreparationRecipeId("rec1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDetail(recipeId))
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_DETAIL.route)

        composeTestRule.runOnUiThread {
            navController.replacePreparationDetailWithDraft(recipeId)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_DRAFT.route)
        
        val backStack = navController.currentBackStack.value
        assertThat(backStack.count { it.destination.route == Destination.PREPARATION_RECIPE_DRAFT.route }).isEqualTo(1)
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_DETAIL.route }).isFalse()
    }

    @Test
    fun existingDetailUnderneathDraftEditor_resultsInSingleDetail() {
        val recipeId = PreparationRecipeId("rec1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(AppRoutes.preparationRecipeDetail(recipeId))
            navController.navigate(AppRoutes.preparationRecipeDraft(recipeId))
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_DRAFT.route)

        composeTestRule.runOnUiThread {
            navController.replacePreparationDraftWithDetail(recipeId)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_DETAIL.route)
        
        val backStack = navController.currentBackStack.value
        assertThat(backStack.count { it.destination.route == Destination.PREPARATION_RECIPE_DETAIL.route }).isEqualTo(1)
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_DRAFT.route }).isFalse()
    }

    @Test
    fun createRecipeToDraft_removesCreateFromStack() {
        val recipeId = PreparationRecipeId("rec1")
        
        composeTestRule.runOnUiThread {
            navController.navigate(Destination.PREPARATION_RECIPE_CREATE.route)
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_CREATE.route)

        composeTestRule.runOnUiThread {
            // Use the centralized navigation helper
            navController.replaceProductionOrRecipeCreateWithDraft(
                recipeOrBatchDraftRoute = AppRoutes.preparationRecipeDraft(recipeId),
                createDestinationRoute = Destination.PREPARATION_RECIPE_CREATE.route
            )
        }

        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_DRAFT.route)
        
        val backStack = navController.currentBackStack.value
        assertThat(backStack.any { it.destination.route == Destination.PREPARATION_RECIPE_CREATE.route }).isFalse()
        
        // Back returns to list
        composeTestRule.runOnUiThread { navController.popBackStack() }
        assertThat(navController.currentDestination?.route).isEqualTo(Destination.PREPARATION_RECIPE_LIST.route)
    }
}
