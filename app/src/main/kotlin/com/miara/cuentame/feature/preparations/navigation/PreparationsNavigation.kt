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

internal fun NavController.replaceProductionOrRecipeCreateWithDraft(
    recipeOrBatchDraftRoute: String,
    createDestinationRoute: String
) {
    navigate(recipeOrBatchDraftRoute) {
        popUpTo(createDestinationRoute) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

internal fun NavController.replacePreparationDraftWithDetail(
    recipeId: PreparationRecipeId
) {
    navigate(AppRoutes.preparationRecipeDetail(recipeId)) {
        popUpTo(Destination.PREPARATION_RECIPE_DRAFT.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

internal fun NavController.replacePreparationDetailWithDraft(
    recipeId: PreparationRecipeId
) {
    navigate(AppRoutes.preparationRecipeDraft(recipeId)) {
        popUpTo(Destination.PREPARATION_RECIPE_DETAIL.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

fun NavGraphBuilder.preparationsGraph(
    navController: NavHostController,
    onBackClick: () -> Unit,
) {
    composable(route = Destination.PREPARATION_RECIPE_LIST.route) {
        PreparationRecipeListRoute(
            onBackClick = onBackClick,
            onCreateRecipe = { navController.navigate(Destination.PREPARATION_RECIPE_CREATE.route) },
            onViewProduction = { navController.navigate(Destination.PRODUCTION_BATCH_LIST.route) },
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
                navController.replaceProductionOrRecipeCreateWithDraft(
                    recipeOrBatchDraftRoute = AppRoutes.preparationRecipeDraft(id),
                    createDestinationRoute = Destination.PREPARATION_RECIPE_CREATE.route
                )
            },
            onSaveSuccess = { navController.popBackStack() },
            onAddComponent = { id -> navController.navigate(AppRoutes.preparationRecipeComponentCreate(id)) },
            onEditComponent = { rId, cId -> navController.navigate(AppRoutes.preparationRecipeComponentEdit(rId, cId)) },
            onNavigateToDetail = { id -> navController.replacePreparationDraftWithDetail(id) }
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
            onNavigateToDetail = { id -> navController.replacePreparationDraftWithDetail(id) }
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
            onSaveSuccess = { navController.popBackStack() },
            onNavigateToDetail = { id -> navController.replacePreparationDraftWithDetail(id) }
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
            onSaveSuccess = { navController.popBackStack() },
            onNavigateToDetail = { id -> navController.replacePreparationDraftWithDetail(id) }
        )
    }

    composable(
        route = Destination.PREPARATION_RECIPE_DETAIL.route,
        arguments = listOf(
            navArgument("recipeId") { type = NavType.StringType }
        )
    ) {
        PreparationRecipeDetailRoute(
            onBack = { navController.popBackStack() },
            onEdit = { id -> navController.replacePreparationDetailWithDraft(id) },
            onCreateProduction = { id -> navController.navigate(AppRoutes.productionBatchCreate(id)) },
            onNavigateToEditor = { id -> navController.replacePreparationDetailWithDraft(id) }
        )
    }
}
