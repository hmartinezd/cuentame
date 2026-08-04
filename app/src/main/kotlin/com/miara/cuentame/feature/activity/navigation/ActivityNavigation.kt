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

fun NavGraphBuilder.activityGraph(navController: NavHostController) {
    composable(
        route = Destination.INVENTORY_ACTIVITY.route,
        arguments = listOf(
            navArgument("ingredientId") { type = NavType.StringType; nullable = true },
            navArgument("areaId") { type = NavType.StringType; nullable = true }
        )
    ) {
        InventoryActivityListRoute(
            onBack = { navController.popBackStack() },
            onActivityDetail = { movementId ->
                navController.navigate(AppRoutes.inventoryActivityDetail(movementId))
            }
        )
    }

    composable(
        route = Destination.INVENTORY_ACTIVITY_DETAIL.route,
        arguments = listOf(
            navArgument("movementId") { type = NavType.StringType }
        )
    ) {
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
