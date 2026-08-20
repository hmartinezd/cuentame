package com.miara.cuentame.feature.ingredients

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.feature.ingredients.navigation.completeIngredientImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IngredientImportSuccessNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun existingInventory_returnsToExistingDestination_withoutDuplicate() {
        val controller = createController(TopLevelDestination.INVENTORY.route)

        composeRule.runOnUiThread {
            controller.navigate(Destination.INGREDIENT_IMPORT.route)
            controller.completeIngredientImport()
        }

        assertInventoryWithoutImport(controller)
        composeRule.runOnUiThread { assertFalse(controller.popBackStack()) }
    }

    @Test
    fun inventoryAbsent_navigatesToInventory_andRemovesCompletedImport() {
        val controller = createController(HOME_ROUTE)

        composeRule.runOnUiThread {
            controller.navigate(Destination.INGREDIENT_IMPORT.route)
            controller.completeIngredientImport()
        }

        assertInventoryWithoutImport(controller)
        composeRule.runOnUiThread {
            controller.popBackStack()
            assertEquals(HOME_ROUTE, controller.currentDestination?.route)
        }
    }

    @Test
    fun repeatedSuccessAction_doesNotCreateDuplicateInventoryDestination() {
        val controller = createController(HOME_ROUTE)

        composeRule.runOnUiThread {
            controller.navigate(Destination.INGREDIENT_IMPORT.route)
            controller.completeIngredientImport()
            controller.completeIngredientImport()
        }

        assertInventoryWithoutImport(controller)
        composeRule.runOnUiThread {
            controller.popBackStack()
            assertEquals(HOME_ROUTE, controller.currentDestination?.route)
        }
    }

    private fun createController(startDestination: String): TestNavHostController {
        lateinit var controller: TestNavHostController
        composeRule.setContent {
            controller = rememberTestNavController()
            NavHost(controller, startDestination = startDestination) {
                composable(HOME_ROUTE) {}
                composable(TopLevelDestination.INVENTORY.route) {}
                composable(Destination.INGREDIENT_IMPORT.route) {}
            }
        }
        composeRule.waitForIdle()
        return controller
    }

    private fun assertInventoryWithoutImport(controller: TestNavHostController) {
        composeRule.runOnUiThread {
            assertEquals(TopLevelDestination.INVENTORY.route, controller.currentDestination?.route)
            assertFalse(
                controller.currentBackStack.value.any {
                    it.destination.route == Destination.INGREDIENT_IMPORT.route
                }
            )
        }
    }

    @Composable
    private fun rememberTestNavController(): TestNavHostController {
        val context = LocalContext.current
        return androidx.compose.runtime.remember {
            TestNavHostController(context).apply {
                navigatorProvider.addNavigator(androidx.navigation.compose.ComposeNavigator())
            }
        }
    }

    private companion object {
        const val HOME_ROUTE = "home"
    }
}
