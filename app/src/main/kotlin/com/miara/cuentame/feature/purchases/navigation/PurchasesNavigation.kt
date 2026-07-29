package com.miara.cuentame.feature.purchases.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.feature.purchases.ui.PurchaseDetailRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseDraftRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseLineRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseListRoute

fun NavGraphBuilder.purchasesGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.ACTIVITY.route) {
        PurchaseListRoute(
            onBack = { navController.popBackStack() },
            onAddPurchase = { navController.navigate(Destination.PURCHASE_CREATE.route) },
            onPurchaseClick = { id, status ->
                when (status) {
                    DocumentStatus.DRAFT ->
                        navController.navigate("purchase/${id.value}")
                    DocumentStatus.POSTED, DocumentStatus.VOIDED ->
                        navController.navigate("purchase/${id.value}/detail")
                }
            }
        )
    }
    composable(route = Destination.PURCHASE_CREATE.route) {
        PurchaseDraftRoute(
            purchaseId = null,
            onBack = { navController.popBackStack() },
            onNavigateToDraft = { id ->
                navController.navigate("purchase/${id.value}") {
                    popUpTo(Destination.PURCHASE_CREATE.route) { inclusive = true }
                }
            },
            onAddLine = {},
            onEditLine = { _, _ -> },
            onPostSuccess = {}
        )
    }
    composable(route = Destination.PURCHASE_DRAFT.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("purchaseId")
        if (idStr != null) {
            val purchaseId = PurchaseReceiptId(idStr)
            PurchaseDraftRoute(
                purchaseId = purchaseId,
                onBack = { navController.popBackStack() },
                onNavigateToDraft = {},
                onAddLine = { rid -> navController.navigate("purchase/${rid.value}/line/create") },
                onEditLine = { rid, lid -> navController.navigate("purchase/${rid.value}/line/${lid.value}/edit") },
                onPostSuccess = { rid ->
                    navController.navigate("purchase/${rid.value}/detail") {
                        popUpTo("purchase/${rid.value}") { inclusive = true }
                    }
                }
            )
        }
    }
    composable(route = Destination.PURCHASE_LINE_CREATE.route) {
        PurchaseLineRoute(
            onBack = { navController.popBackStack() }
        )
    }
    composable(route = Destination.PURCHASE_LINE_EDIT.route) {
        PurchaseLineRoute(
            onBack = { navController.popBackStack() }
        )
    }
    composable(route = Destination.PURCHASE_DETAIL.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("purchaseId")
        if (idStr != null) {
            PurchaseDetailRoute(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
