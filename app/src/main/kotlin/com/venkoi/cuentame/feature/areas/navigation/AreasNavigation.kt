package com.venkoi.cuentame.feature.areas.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.venkoi.cuentame.core.presentation.navigation.AppRoutes
import com.venkoi.cuentame.core.presentation.navigation.Destination
import com.venkoi.cuentame.feature.areas.ui.AreaManagementRoute

fun NavGraphBuilder.areasGraph(
    navController: NavHostController,
    onBack: () -> Unit
) {
    composable(Destination.SETTINGS_AREAS.route) {
        AreaManagementRoute(
            onBack = onBack,
            onViewActivity = { id -> navController.navigate(AppRoutes.inventoryActivity(areaId = id)) }
        )
    }
}
