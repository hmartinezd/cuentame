package com.miara.cuentame.feature.activity.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miara.cuentame.core.common.ids.ProductionBatchId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.common.ids.WasteEventId
import com.miara.cuentame.core.model.inventory.InventoryActivitySourceTarget
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.feature.activity.ui.InventoryActivityDetailRoute
import com.miara.cuentame.feature.activity.ui.InventoryActivityListRoute
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityDetailViewModel
import com.miara.cuentame.feature.activity.viewmodel.InventoryActivityListViewModel

import androidx.compose.runtime.CompositionLocalProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.miara.cuentame.feature.activity.logic.InventoryActivityTextResolver
import com.miara.cuentame.feature.activity.logic.LocalInventoryActivityTextResolver
import javax.inject.Inject

// This would ideally be a separate class or handled differently if we can't inject into a file-level function easily.
// But for now, we can use hiltViewModel() inside the composables to get the resolver if needed, 
// OR just get it from the entry point.

fun NavGraphBuilder.activityGraph(navController: NavHostController) {
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
        val resolver = hiltViewModel<InventoryActivityListViewModel>().getTextResolver() // I'll add this getter
        CompositionLocalProvider(LocalInventoryActivityTextResolver provides resolver) {
            InventoryActivityListRoute(
                onBack = { navController.popBackStack() },
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
        val resolver = hiltViewModel<InventoryActivityDetailViewModel>().getTextResolver() // I'll add this getter
        CompositionLocalProvider(LocalInventoryActivityTextResolver provides resolver) {
            InventoryActivityDetailRoute(
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
                    navController.navigate(AppRoutes.inventoryActivityDetail(movementId))
                }
            )
        }
    }
}
