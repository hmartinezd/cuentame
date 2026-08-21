package com.venkoi.restaurantops.feature.activity.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.venkoi.restaurantops.core.model.inventory.InventoryActivitySourceTarget
import com.venkoi.restaurantops.core.presentation.navigation.AppRoutes
import com.venkoi.restaurantops.core.presentation.navigation.Destination
import com.venkoi.restaurantops.core.presentation.navigation.TopLevelDestination
import com.venkoi.restaurantops.feature.activity.logic.LocalInventoryActivityTextResolver
import com.venkoi.restaurantops.feature.activity.ui.InventoryActivityDetailRoute
import com.venkoi.restaurantops.feature.activity.ui.InventoryActivityListRoute
import com.venkoi.restaurantops.feature.activity.viewmodel.InventoryActivityDetailViewModel
import com.venkoi.restaurantops.feature.activity.viewmodel.InventoryActivityListViewModel

fun NavGraphBuilder.activityGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.ACTIVITY.route) {
        val viewModel = hiltViewModel<InventoryActivityListViewModel>()
        CompositionLocalProvider(LocalInventoryActivityTextResolver provides viewModel.textResolver) {
            InventoryActivityListRoute(
                viewModel = viewModel,
                showTopBar = false,
                onBack = {},
                onPurchases = { navController.navigate("purchases") },
                onActivityDetail = { movementId ->
                    navController.navigate(AppRoutes.inventoryActivityDetail(movementId))
                }
            )
        }
    }

    composable(
        route = Destination.INVENTORY_ACTIVITY.route,
        arguments = listOf(
            navArgument("ingredientId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("areaId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) {
        val viewModel = hiltViewModel<InventoryActivityListViewModel>()
        CompositionLocalProvider(
            LocalInventoryActivityTextResolver provides viewModel.textResolver
        ) {
            InventoryActivityListRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPurchases = { navController.navigate("purchases") },
                onActivityDetail = { movementId ->
                    navController.navigate(AppRoutes.inventoryActivityDetail(movementId))
                }
            )
        }
    }

    composable(
        route = Destination.INVENTORY_ACTIVITY_DETAIL.route,
        arguments = listOf(
            navArgument("movementId") { type = NavType.StringType }
        )
    ) {
        val viewModel = hiltViewModel<InventoryActivityDetailViewModel>()
        CompositionLocalProvider(
            LocalInventoryActivityTextResolver provides viewModel.textResolver
        ) {
            InventoryActivityDetailRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenSource = { target ->
                    when (target) {
                        is InventoryActivitySourceTarget.Purchase -> navController.navigate(AppRoutes.purchaseDetail(target.receiptId))
                        is InventoryActivitySourceTarget.Waste -> navController.navigate(AppRoutes.wasteDetail(target.wasteEventId))
                        is InventoryActivitySourceTarget.StockCount -> navController.navigate(AppRoutes.stockCountDetail(target.stockCountId))
                        is InventoryActivitySourceTarget.Production -> navController.navigate(AppRoutes.productionBatchDetail(target.batchId))
                        InventoryActivitySourceTarget.Unavailable -> {}
                    }
                },
                onOpenMovement = { movementId ->
                    navController.navigate(AppRoutes.inventoryActivityDetail(movementId)) {
                        popUpTo(Destination.INVENTORY_ACTIVITY_DETAIL.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}
