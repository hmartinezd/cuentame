package com.miara.cuentame.feature.counts.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.common.ids.StockCountId
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.core.presentation.navigation.AppRoutes
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
                    navController.navigate(AppRoutes.stockCountDraft(id))
                } else {
                    navController.navigate(AppRoutes.stockCountDetail(id))
                }
            }
        )
    }
    composable(route = Destination.STOCK_COUNT_START.route) {
        StartStockCountRoute(
            onBack = { navController.popBackStack() },
            onCountStarted = { id ->
                navController.navigate(AppRoutes.stockCountDraft(id)) {
                    popUpTo(Destination.STOCK_COUNT_START.route) { inclusive = true }
                }
            }
        )
    }
    composable(route = Destination.STOCK_COUNT_DRAFT.route) { entry ->
        val countId = entry.arguments?.getString("countId")?.let { StockCountId(it) }
        StockCountDetailRoute(
            onBack = { navController.popBackStack() },
            onAreaClick = { aid ->
                if (countId != null) {
                    navController.navigate(AppRoutes.stockCountArea(countId, aid))
                }
            }
        )
    }
    composable(route = Destination.STOCK_COUNT_AREA.route) {
        StockCountAreaRoute(
            onBack = { navController.popBackStack() }
        )
    }
    composable(route = Destination.STOCK_COUNT_DETAIL.route) { entry ->
        val countId = entry.arguments?.getString("countId")?.let { StockCountId(it) }
        StockCountDetailRoute(
            onBack = { navController.popBackStack() },
            onAreaClick = { aid ->
                if (countId != null) {
                    navController.navigate(AppRoutes.stockCountArea(countId, aid))
                }
            }
        )
    }
}
