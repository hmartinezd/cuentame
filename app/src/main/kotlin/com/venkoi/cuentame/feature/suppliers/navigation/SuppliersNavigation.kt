package com.venkoi.cuentame.feature.suppliers.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.venkoi.cuentame.core.presentation.navigation.Destination
import com.venkoi.cuentame.core.presentation.navigation.AppRoutes
import com.venkoi.cuentame.feature.suppliers.ui.SupplierFormRoute
import com.venkoi.cuentame.feature.suppliers.ui.SupplierListRoute

fun NavGraphBuilder.suppliersGraph(navController: NavHostController) {
    composable(route = Destination.SUPPLIER_LIST.route) {
        SupplierListRoute(
            onBack = { navController.popBackStack() },
            onAddSupplier = { navController.navigate(Destination.SUPPLIER_CREATE.route) },
            onEditSupplier = { id -> navController.navigate(AppRoutes.supplierEdit(id)) }
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
