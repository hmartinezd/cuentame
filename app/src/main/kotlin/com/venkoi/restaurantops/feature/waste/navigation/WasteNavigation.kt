package com.venkoi.restaurantops.feature.waste.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.venkoi.restaurantops.core.presentation.navigation.Destination
import com.venkoi.restaurantops.core.presentation.navigation.TopLevelDestination
import com.venkoi.restaurantops.core.presentation.navigation.AppRoutes
import com.venkoi.restaurantops.core.model.inventory.DocumentStatus
import com.venkoi.restaurantops.feature.waste.ui.WasteDetailRoute
import com.venkoi.restaurantops.feature.waste.ui.WasteFormRoute
import com.venkoi.restaurantops.feature.waste.ui.WasteListRoute

fun NavGraphBuilder.wasteGraph(navController: NavHostController) {
    composable(route = Destination.WASTE_LIST.route) {
        WasteListRoute(
            onBack = { navController.popBackStack() },
            onAddWaste = { navController.navigate(Destination.WASTE_CREATE.route) },
            onWasteClick = { id, status ->
                if (status == DocumentStatus.DRAFT) {
                    navController.navigate(AppRoutes.wasteDraft(id))
                } else {
                    navController.navigate(AppRoutes.wasteDetail(id))
                }
            }
        )
    }
    composable(route = Destination.WASTE_CREATE.route) {
        WasteFormRoute(
            onBack = { navController.popBackStack() },
            onSuccess = { id ->
                navController.navigate(AppRoutes.wasteDetail(id)) {
                    popUpTo(Destination.WASTE_CREATE.route) { inclusive = true }
                }
            }
        )
    }
    composable(route = Destination.WASTE_DRAFT.route) {
        WasteDetailRoute(
            onBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(AppRoutes.wasteEdit(id)) }
        )
    }
    composable(route = Destination.WASTE_EDIT.route) {
        WasteFormRoute(
            onBack = { navController.popBackStack() },
            onSuccess = { navController.popBackStack() }
        )
    }
    composable(route = Destination.WASTE_DETAIL.route) {
        WasteDetailRoute(
            onBack = { navController.popBackStack() },
            onEdit = {}
        )
    }
}
