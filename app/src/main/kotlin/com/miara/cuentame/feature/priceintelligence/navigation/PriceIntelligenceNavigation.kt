package com.miara.cuentame.feature.priceintelligence.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.feature.priceintelligence.ui.PriceAlertsRoute
import com.miara.cuentame.feature.priceintelligence.ui.PriceHistoryRoute

fun NavGraphBuilder.priceIntelligenceGraph(navController: NavHostController) {
    composable(Destination.INGREDIENT_PRICE_HISTORY.route) {
        PriceHistoryRoute(
            onBack = { navController.popBackStack() },
            onSource = { receiptId, lineId -> navController.navigate(AppRoutes.purchaseDetail(receiptId, lineId)) }
        )
    }
    composable(Destination.REPORT_PRICE_INCREASES.route) {
        PriceAlertsRoute(
            onBack = { navController.popBackStack() },
            onSource = { receiptId, lineId -> navController.navigate(AppRoutes.purchaseDetail(receiptId, lineId)) }
        )
    }
}
