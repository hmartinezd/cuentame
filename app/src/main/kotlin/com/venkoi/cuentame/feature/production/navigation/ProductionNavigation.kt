package com.venkoi.cuentame.feature.production.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.venkoi.cuentame.core.common.ids.ProductionBatchComponentId
import com.venkoi.cuentame.core.common.ids.ProductionBatchId
import com.venkoi.cuentame.core.model.inventory.DocumentStatus
import com.venkoi.cuentame.core.presentation.navigation.AppRoutes
import com.venkoi.cuentame.core.presentation.navigation.Destination
import com.venkoi.cuentame.feature.production.ui.*

fun NavController.navigateToProductionBatchList() {
    this.navigate(Destination.PRODUCTION_BATCH_LIST.route)
}

internal fun NavController.replaceProductionCreateWithDraft(
    batchId: ProductionBatchId
) {
    navigate(AppRoutes.productionBatchDraft(batchId)) {
        popUpTo(Destination.PRODUCTION_BATCH_CREATE.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

internal fun NavController.replaceProductionDraftWithDetail(
    batchId: ProductionBatchId
) {
    navigate(AppRoutes.productionBatchDetail(batchId)) {
        popUpTo(Destination.PRODUCTION_BATCH_DRAFT.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

internal fun NavController.replaceProductionPreviewWithDetail(
    batchId: ProductionBatchId
) {
    navigate(AppRoutes.productionBatchDetail(batchId)) {
        popUpTo(Destination.PRODUCTION_BATCH_DRAFT.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

internal fun NavController.replaceProductionDetailWithDraft(
    batchId: ProductionBatchId
) {
    navigate(AppRoutes.productionBatchDraft(batchId)) {
        popUpTo(Destination.PRODUCTION_BATCH_DETAIL.route) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

fun NavGraphBuilder.productionGraph(
    navController: NavHostController,
    onBackClick: () -> Unit
) {
    composable(route = Destination.PRODUCTION_BATCH_LIST.route) {
        ProductionBatchListRoute(
            onBackClick = onBackClick,
            onCreateBatch = { navController.navigate(AppRoutes.productionBatchCreate()) },
            onBatchClick = { id, status ->
                val route = if (status == DocumentStatus.DRAFT) AppRoutes.productionBatchDraft(id)
                else AppRoutes.productionBatchDetail(id)
                navController.navigate(route)
            }
        )
    }

    composable(
        route = Destination.PRODUCTION_BATCH_CREATE.route,
        arguments = listOf(
            navArgument("recipeId") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        ProductionBatchCreateRoute(
            onBack = { navController.popBackStack() },
            onBatchCreated = { id -> 
                navController.replaceProductionCreateWithDraft(id)
            }
        )
    }

    composable(
        route = Destination.PRODUCTION_BATCH_DRAFT.route,
        arguments = listOf(
            navArgument("batchId") { type = NavType.StringType }
        )
    ) {
        ProductionBatchDraftRoute(
            onBack = { navController.popBackStack() },
            onNavigateToDetail = { id -> 
                navController.replaceProductionDraftWithDetail(id)
            },
            onDeleted = { navController.popBackStack() },
            onEditComponent = { bId, cId -> 
                navController.navigate(AppRoutes.productionBatchComponent(bId, cId))
            },
            onReview = { id -> 
                navController.navigate(AppRoutes.productionBatchPreview(id))
            }
        )
    }

    composable(
        route = Destination.PRODUCTION_BATCH_COMPONENT.route,
        arguments = listOf(
            navArgument("batchId") { type = NavType.StringType },
            navArgument("componentId") { type = NavType.StringType }
        )
    ) {
        ProductionBatchComponentRoute(
            onBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() }
        )
    }

    composable(
        route = Destination.PRODUCTION_BATCH_PREVIEW.route,
        arguments = listOf(
            navArgument("batchId") { type = NavType.StringType }
        )
    ) {
        ProductionBatchPostingPreviewRoute(
            onBack = { navController.popBackStack() },
            onPosted = { id -> 
                navController.replaceProductionPreviewWithDetail(id)
            }
        )
    }

    composable(
        route = Destination.PRODUCTION_BATCH_DETAIL.route,
        arguments = listOf(
            navArgument("batchId") { type = NavType.StringType }
        )
    ) {
        ProductionBatchDetailRoute(
            onBack = { navController.popBackStack() },
            onNavigateToDraft = { id -> 
                navController.replaceProductionDetailWithDraft(id)
            }
        )
    }
}
