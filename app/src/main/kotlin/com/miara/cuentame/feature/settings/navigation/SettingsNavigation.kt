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
            onNavigateToAreas = { navController.navigate(Destination.SETTINGS_AREAS.route) },
            onNavigateToCategories = { navController.navigate(Destination.SETTINGS_CATEGORIES.route) },
            onNavigateToRestaurant = { navController.navigate(Destination.SETTINGS_RESTAURANT.route) },
            onNavigateToSuppliers = { navController.navigate(Destination.SUPPLIER_LIST.route) }
        )
    }
    composable(Destination.SETTINGS_RESTAURANT.route) {
        RestaurantProfileRoute(onBack = onBackClick)
    }
}
