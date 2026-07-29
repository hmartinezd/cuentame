package com.miara.cuentame.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.feature.settings.ui.RestaurantProfileRoute
import com.miara.cuentame.feature.settings.ui.SettingsRoute

fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    onBackClick: () -> Unit
) {
    composable(route = Destination.SETTINGS.route) {
        SettingsRoute(
            onNavigateToAreas = { navController.navigate("settings/areas") },
            onNavigateToCategories = { navController.navigate("settings/categories") },
            onNavigateToRestaurant = { navController.navigate("settings/restaurant") },
            onNavigateToSuppliers = { navController.navigate(Destination.SUPPLIER_LIST.route) }
        )
    }
    composable("settings/restaurant") {
        RestaurantProfileRoute(onBack = onBackClick)
    }
}
