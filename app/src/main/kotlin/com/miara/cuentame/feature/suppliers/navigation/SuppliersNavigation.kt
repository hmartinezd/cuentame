package com.miara.cuentame.feature.suppliers.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.feature.suppliers.ui.SupplierFormRoute
import com.miara.cuentame.feature.suppliers.ui.SupplierListRoute

fun NavGraphBuilder.suppliersGraph(navController: NavHostController) {
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
