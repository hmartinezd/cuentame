package com.miara.cuentame.feature.inventory.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.app.navigation.Destination
import com.miara.cuentame.app.navigation.TopLevelDestination
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.feature.ingredients.ui.IngredientDetailRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientFormRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientListRoute

fun NavGraphBuilder.inventoryGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.INVENTORY.route) {
        IngredientListRoute(
            onAddIngredient = { navController.navigate(Destination.INGREDIENT_CREATE.route) },
            onIngredientClick = { id -> navController.navigate("ingredient/${id.value}") }
        )
    }
    composable(route = Destination.INGREDIENT_CREATE.route) {
        IngredientFormRoute(
            onBack = { navController.popBackStack() },
            onSaveSuccess = { id: IngredientId ->
                navController.navigate("ingredient/${id.value}") {
                    popUpTo(Destination.INGREDIENT_CREATE.route) { inclusive = true }
                }
            }
        )
    }
    composable(route = Destination.INGREDIENT_DETAIL.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("ingredientId")
        if (idStr != null) {
            IngredientDetailRoute(
                ingredientId = IngredientId(idStr),
                onEditClick = { id -> navController.navigate("ingredient/${id.value}/edit") },
                onBack = { navController.popBackStack() }
            )
        }
    }
    composable(route = Destination.INGREDIENT_EDIT.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("ingredientId")
        if (idStr != null) {
            IngredientFormRoute(
                ingredientId = IngredientId(idStr),
                onBack = { navController.popBackStack() },
                onSaveSuccess = { _ -> navController.popBackStack() }
            )
        }
    }
}
