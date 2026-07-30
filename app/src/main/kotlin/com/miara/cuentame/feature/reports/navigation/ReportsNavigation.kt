package com.miara.cuentame.feature.reports.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.feature.reports.ui.InventoryDetailRoute
import com.miara.cuentame.feature.reports.ui.PurchaseDetailRoute as ReportsPurchaseDetailRoute
import com.miara.cuentame.feature.reports.ui.ReportsRoute
import com.miara.cuentame.feature.reports.ui.WasteDetailRoute as ReportsWasteDetailRoute

fun NavGraphBuilder.reportsGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.REPORTS.route) {
        ReportsRoute(
            onNavigateToInventory = { navController.navigate(Destination.REPORT_INVENTORY_DETAIL.route) },
            onNavigateToPurchases = { range -> navController.navigate("reports/purchase?range=${range.name}") },
            onNavigateToWaste = { range -> navController.navigate("reports/waste?range=${range.name}") }
        )
    }
    composable(route = Destination.REPORT_INVENTORY_DETAIL.route) {
        InventoryDetailRoute(onBack = { navController.popBackStack() })
    }
    composable(route = Destination.REPORT_PURCHASE_DETAIL.route) {
        ReportsPurchaseDetailRoute(onBack = { navController.popBackStack() })
    }
    composable(route = Destination.REPORT_WASTE_DETAIL.route) {
        ReportsWasteDetailRoute(onBack = { navController.popBackStack() })
    }
}
