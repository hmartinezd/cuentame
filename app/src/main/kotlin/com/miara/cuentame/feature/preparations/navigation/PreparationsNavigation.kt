package com.miara.cuentame.feature.preparations.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miara.cuentame.core.common.ids.PreparationRecipeId
import com.miara.cuentame.core.model.ingredient.PreparationRecipeStatus
import com.miara.cuentame.feature.preparations.ui.PreparationRecipeComponentRoute
import com.miara.cuentame.feature.preparations.ui.PreparationRecipeDetailRoute
import com.miara.cuentame.feature.preparations.ui.PreparationRecipeEditorRoute
import com.miara.cuentame.feature.preparations.ui.PreparationRecipeListRoute
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination

fun NavController.navigateToPreparationRecipeList() {
    this.navigate(Destination.PREPARATION_RECIPE_LIST.route)
}

fun NavGraphBuilder.preparationsGraph(
    navController: NavHostController,
    onBackClick: () -> Unit,
) {
    composable(route = Destination.PREPARATION_RECIPE_LIST.route) {
        PreparationRecipeListRoute(
            onBackClick = onBackClick,
            onCreateRecipe = { navController.navigate(Destination.PREPARATION_RECIPE_CREATE.route) },
            onRecipeClick = { id, status ->
                val route = if (status == PreparationRecipeStatus.DRAFT) {
                    AppRoutes.preparationRecipeDraft(id)
                } else {
                    AppRoutes.preparationRecipeDetail(id)
                }
                navController.navigate(route)
            }
        )
    }

    composable(route = Destination.PREPARATION_RECIPE_CREATE.route) {
        PreparationRecipeEditorRoute(
            onBack = { navController.popBackStack() },
            onRecipeCreated = { id ->
                navController.navigate(AppRoutes.preparationRecipeDraft(id)) {
                    popUpTo(Destination.PREPARATION_RECIPE_CREATE.route) { inclusive = true }
                }
            },
            onSaveSuccess = { navController.popBackStack() },
            onAddComponent = { id -> navController.navigate(AppRoutes.preparationRecipeComponentCreate(id)) },
            onEditComponent = { rId, cId -> navController.navigate(AppRoutes.preparationRecipeComponentEdit(rId, cId)) },
            onNavigateToDetail = { id -> 
                navController.navigate(AppRoutes.preparationRecipeDetail(id)) {
                    popUpTo(Destination.PREPARATION_RECIPE_DRAFT.route) { inclusive = true }
                }
            }
        )
    }

    composable(
        route = Destination.PREPARATION_RECIPE_DRAFT.route,
        arguments = listOf(
            navArgument("recipeId") { type = NavType.StringType }
        )
    ) {
        PreparationRecipeEditorRoute(
            onBack = { navController.popBackStack() },
            onRecipeCreated = { },
            onSaveSuccess = { navController.popBackStack() },
            onAddComponent = { id -> navController.navigate(AppRoutes.preparationRecipeComponentCreate(id)) },
            onEditComponent = { rId, cId -> navController.navigate(AppRoutes.preparationRecipeComponentEdit(rId, cId)) },
            onNavigateToDetail = { id -> 
                navController.navigate(AppRoutes.preparationRecipeDetail(id)) {
                    popUpTo(Destination.PREPARATION_RECIPE_DRAFT.route) { inclusive = true }
                }
            }
        )
    }

    composable(
        route = Destination.PREPARATION_RECIPE_COMPONENT_CREATE.route,
        arguments = listOf(
            navArgument("recipeId") { type = NavType.StringType }
        )
    ) {
        PreparationRecipeComponentRoute(
            onBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() }
        )
    }

    composable(
        route = Destination.PREPARATION_RECIPE_COMPONENT_EDIT.route,
        arguments = listOf(
            navArgument("recipeId") { type = NavType.StringType },
            navArgument("componentId") { type = NavType.StringType }
        )
    ) {
        PreparationRecipeComponentRoute(
            onBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() }
        )
    }

    composable(
        route = Destination.PREPARATION_RECIPE_DETAIL.route,
        arguments = listOf(
            navArgument("recipeId") { type = NavType.StringType }
        )
    ) {
        PreparationRecipeDetailRoute(
            onBack = onBackClick,
            onEdit = { id -> navController.navigate(AppRoutes.preparationRecipeDraft(id)) }
        )
    }
}
