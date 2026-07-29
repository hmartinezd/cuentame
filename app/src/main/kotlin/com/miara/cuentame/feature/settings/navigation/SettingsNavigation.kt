package com.miara.cuentame.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.app.navigation.Destination
import com.miara.cuentame.feature.areas.ui.AreaManagementRoute
import com.miara.cuentame.feature.categories.ui.CategoryManagementRoute
import com.miara.cuentame.feature.settings.ui.RestaurantProfileRoute
import com.miara.cuentame.feature.settings.ui.SettingsRoute
import com.miara.cuentame.feature.suppliers.ui.SupplierFormRoute
import com.miara.cuentame.feature.suppliers.ui.SupplierListRoute

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
    composable("settings/areas") {
        AreaManagementRoute()
    }
    composable("settings/categories") {
        CategoryManagementRoute()
    }
    composable("settings/restaurant") {
        RestaurantProfileRoute(onBack = onBackClick)
    }
    composable(route = Destination.SUPPLIER_LIST.route) {
        SupplierListRoute(
            onBack = { navController.popBackStack() },
            onAddSupplier = { navController.navigate(Destination.SUPPLIER_CREATE.route) },
            onEditSupplier = { id -> navController.navigate("supplier/${id.value}/edit") }
        )
    }
    composable(route = Destination.SUPPLIER_CREATE.route) {
        SupplierFormRoute(
            onBack = { navController.popBackStack() },
            onSaveSuccess = { navController.popBackStack() }
        )
    }
    composable(route = Destination.SUPPLIER_EDIT.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("supplierId")
        if (idStr != null) {
            SupplierFormRoute(
                onBack = { navController.popBackStack() },
                onSaveSuccess = { navController.popBackStack() }
            )
        }
    }
}
