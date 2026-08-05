package com.miara.cuentame.feature.purchases.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.core.presentation.navigation.AppRoutes
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
                getPurchaseNavigationRoute(id, status)?.let { route ->
                    navController.navigate(route)
                }
            }
        )
    }
    composable(route = Destination.PURCHASE_CREATE.route) {
        PurchaseDraftRoute(
            purchaseId = null,
            onBack = { navController.popBackStack() },
            onNavigateToDraft = { id ->
                navController.navigate(AppRoutes.purchaseDraft(id)) {
                    popUpTo(Destination.PURCHASE_CREATE.route) { inclusive = true }
                }
            },
            onAddLine = {},
            onEditLine = { _, _ -> },
            onPostSuccess = {}
        )
    }
    composable(route = Destination.PURCHASE_DRAFT.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("receiptId")
        if (idStr != null) {
            val purchaseId = PurchaseReceiptId(idStr)
            PurchaseDraftRoute(
                purchaseId = purchaseId,
                onBack = { navController.popBackStack() },
                onNavigateToDraft = {},
                onAddLine = { rid -> navController.navigate(AppRoutes.purchaseLineCreate(rid)) },
                onEditLine = { rid, lid -> navController.navigate(AppRoutes.purchaseLineEdit(rid, lid)) },
                onPostSuccess = { rid ->
                    navController.navigate(AppRoutes.purchaseDetail(rid)) {
                        popUpTo(AppRoutes.purchaseDraft(rid)) { inclusive = true }
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
        val idStr = backStackEntry.arguments?.getString("receiptId")
        if (idStr != null) {
            PurchaseDetailRoute(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

fun getPurchaseNavigationRoute(id: PurchaseReceiptId, status: DocumentStatus): String? {
    return when (status) {
        DocumentStatus.DRAFT -> AppRoutes.purchaseDraft(id)
        DocumentStatus.POSTED, DocumentStatus.VOIDED, DocumentStatus.UNKNOWN -> AppRoutes.purchaseDetail(id)
    }
}
