package com.miara.cuentame.feature.counts.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.app.navigation.Destination
import com.miara.cuentame.app.navigation.TopLevelDestination
import com.miara.cuentame.core.model.inventory.StockCountStatus
import com.miara.cuentame.feature.counts.ui.StartStockCountRoute
import com.miara.cuentame.feature.counts.ui.StockCountAreaRoute
import com.miara.cuentame.feature.counts.ui.StockCountDetailRoute
import com.miara.cuentame.feature.counts.ui.StockCountListRoute

fun NavGraphBuilder.countsGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.COUNT.route) {
        StockCountListRoute(
            onStartCount = { navController.navigate(Destination.STOCK_COUNT_START.route) },
            onCountClick = { id, status ->
                if (status == StockCountStatus.DRAFT) {
                    navController.navigate("count/${id.value}")
                } else {
                    navController.navigate("count/${id.value}/detail")
                }
            }
        )
    }
    composable(route = Destination.STOCK_COUNT_START.route) {
        StartStockCountRoute(
            onBack = { navController.popBackStack() },
            onCountStarted = { id ->
                navController.navigate("count/${id.value}") {
                    popUpTo(Destination.STOCK_COUNT_START.route) { inclusive = true }
                }
            }
        )
    }
    composable(route = Destination.STOCK_COUNT_DRAFT.route) {
        StockCountDetailRoute(
            onBack = { navController.popBackStack() },
            onAreaClick = { aid ->
                val cid = navController.currentBackStackEntry?.arguments?.getString("countId")
                if (cid != null) {
                    navController.navigate("count/$cid/area/${aid.value}")
                }
            }
        )
    }
    composable(route = Destination.STOCK_COUNT_AREA.route) {
        StockCountAreaRoute(
            onBack = { navController.popBackStack() }
        )
    }
    composable(route = Destination.STOCK_COUNT_DETAIL.route) {
        StockCountDetailRoute(
            onBack = { navController.popBackStack() },
            onAreaClick = { aid ->
                val cid = navController.currentBackStackEntry?.arguments?.getString("countId")
                if (cid != null) {
                    navController.navigate("count/$cid/area/${aid.value}")
                }
            }
        )
    }
}
