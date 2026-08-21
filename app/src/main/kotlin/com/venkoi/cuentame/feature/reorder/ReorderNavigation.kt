package com.venkoi.cuentame.feature.reorder

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.venkoi.cuentame.core.presentation.navigation.Destination

fun NavGraphBuilder.reorderGraph(navController: NavHostController) {
    composable(Destination.REORDER_ASSISTANCE.route) {
        ReorderRoute(
            onBack = { navController.popBackStack() },
            onConfigureIngredient = { id -> navController.navigate("inventory/${id.value}/edit") }
        )
    }
}
