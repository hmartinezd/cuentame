package com.miara.cuentame.feature.ingredients.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.feature.ingredients.ui.IngredientDetailRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientFormRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientListRoute

fun NavGraphBuilder.ingredientsGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.INVENTORY.route) {
        IngredientListRoute(
            onIngredientClick = { id -> navController.navigate(AppRoutes.ingredientDetail(id)) },
            onAddIngredient = { navController.navigate(Destination.INGREDIENT_CREATE.route) },
            onManagePreparations = { navController.navigate(Destination.PREPARATION_RECIPE_LIST.route) }
        )
    }
    composable(route = Destination.INGREDIENT_CREATE.route) {
        IngredientFormRoute(
            ingredientId = null,
            onBack = { navController.popBackStack() },
            onSaveSuccess = { id ->
                navController.navigate(AppRoutes.ingredientDetail(id)) {
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
                onEditClick = { id -> navController.navigate(AppRoutes.ingredientEdit(id)) },
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
                onSaveSuccess = { navController.popBackStack() }
            )
        }
    }
}
