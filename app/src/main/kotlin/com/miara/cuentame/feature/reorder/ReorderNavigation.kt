package com.miara.cuentame.feature.reorder

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination

fun NavGraphBuilder.reorderGraph(navController: NavHostController) {
    composable(Destination.REORDER_ASSISTANCE.route) {
        ReorderRoute(onBack = { navController.popBackStack() })
    }
}
