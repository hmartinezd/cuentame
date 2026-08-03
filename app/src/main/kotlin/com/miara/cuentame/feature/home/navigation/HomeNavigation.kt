package com.miara.cuentame.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.feature.home.HomeRoute

fun NavGraphBuilder.homeGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.HOME.route) {
        HomeRoute(
            onLogWaste = { navController.navigate(Destination.WASTE_CREATE.route) },
            onViewWaste = { navController.navigate(Destination.WASTE_LIST.route) },
            onNewPurchase = { navController.navigate(Destination.PURCHASE_CREATE.route) },
            onStartCount = { navController.navigate(Destination.STOCK_COUNT_START.route) },
            onViewReports = { navController.navigate(TopLevelDestination.REPORTS.route) },
            onViewPreparations = { navController.navigate(Destination.PREPARATION_RECIPE_LIST.route) }
        )
    }
}
