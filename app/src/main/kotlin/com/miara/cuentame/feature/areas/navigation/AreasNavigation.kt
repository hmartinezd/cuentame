package com.miara.cuentame.feature.areas.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.feature.areas.ui.AreaManagementRoute

fun NavGraphBuilder.areasGraph(navController: NavHostController) {
    composable(Destination.SETTINGS_AREAS.route) {
        AreaManagementRoute(
            onViewActivity = { id -> navController.navigate(AppRoutes.inventoryActivity(areaId = id)) }
        )
    }
}
