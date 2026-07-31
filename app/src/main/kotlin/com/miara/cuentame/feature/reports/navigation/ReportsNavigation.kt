package com.miara.cuentame.feature.reports.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.feature.reports.ui.InventoryDetailRoute
import com.miara.cuentame.feature.reports.ui.PurchaseDetailRoute as ReportsPurchaseDetailRoute
import com.miara.cuentame.feature.reports.ui.ReportsRoute
import com.miara.cuentame.feature.reports.ui.WasteDetailRoute as ReportsWasteDetailRoute

fun NavGraphBuilder.reportsGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.REPORTS.route) {
        ReportsRoute(
            onNavigateToInventory = { navController.navigate(Destination.REPORT_INVENTORY_DETAIL.route) },
            onNavigateToPurchases = { range -> navController.navigate(AppRoutes.reportPurchaseDetail(range.name)) },
            onNavigateToWaste = { range -> navController.navigate(AppRoutes.reportWasteDetail(range.name)) }
        )
    }
    composable(route = Destination.REPORT_INVENTORY_DETAIL.route) {
        InventoryDetailRoute(onBack = { navController.popBackStack() })
    }
    composable(
        route = Destination.REPORT_PURCHASE_DETAIL.route,
        arguments = listOf(
            navArgument("range") {
                type = NavType.StringType
                defaultValue = "LAST_30_DAYS"
                nullable = true
            }
        )
    ) {
        ReportsPurchaseDetailRoute(onBack = { navController.popBackStack() })
    }
    composable(
        route = Destination.REPORT_WASTE_DETAIL.route,
        arguments = listOf(
            navArgument("range") {
                type = NavType.StringType
                defaultValue = "LAST_30_DAYS"
                nullable = true
            }
        )
    ) {
        ReportsWasteDetailRoute(onBack = { navController.popBackStack() })
    }
}
