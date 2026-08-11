package com.miara.cuentame.feature.purchases.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.TopLevelDestination
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.model.inventory.DocumentStatus
import com.miara.cuentame.feature.purchases.ui.PurchaseDetailRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseDocumentViewerRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseDraftRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseLineRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseListRoute
import com.miara.cuentame.feature.purchases.ui.ReviewDetectedInvoiceRoute
import com.miara.cuentame.feature.purchases.ui.RawOcrViewerScreen

fun NavGraphBuilder.purchasesGraph(navController: NavHostController) {
    composable(route = TopLevelDestination.ACTIVITY.route) {
        PurchaseListRoute(
            onBack = { navController.popBackStack() },
            onAddPurchase = { navController.navigate(Destination.PURCHASE_CREATE.route) },
            onPurchaseClick = { id, status ->
                val route = getPurchaseNavigationRoute(id, status)
                navController.navigate(route)
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
            onNavigateToDocument = { id -> navController.navigate(AppRoutes.purchaseDocument(id)) },
            onNavigateToRawOcr = { id -> navController.navigate(AppRoutes.purchaseRawOcr(id)) },
            onReviewInvoice = { id -> navController.navigate(AppRoutes.purchaseReview(id)) },
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
                onNavigateToDocument = { rid -> navController.navigate(AppRoutes.purchaseDocument(rid)) },
                onNavigateToRawOcr = { rid -> navController.navigate(AppRoutes.purchaseRawOcr(rid)) },
                onReviewInvoice = { rid -> navController.navigate(AppRoutes.purchaseReview(rid)) },
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
    composable(route = Destination.PURCHASE_RAW_OCR.route) {
        RawOcrViewerScreen(
            onBack = { navController.popBackStack() }
        )
    }
    composable(route = Destination.PURCHASE_REVIEW.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("receiptId")
        if (idStr != null) {
            val purchaseId = PurchaseReceiptId(idStr)
            ReviewDetectedInvoiceRoute(
                receiptId = purchaseId,
                onBack = { navController.popBackStack() },
                onViewDocument = { rid -> navController.navigate(AppRoutes.purchaseDocument(rid)) },
                onViewRawOcr = { rid -> navController.navigate(AppRoutes.purchaseRawOcr(rid)) },
                onAddIngredient = { name -> navController.navigate(AppRoutes.ingredientCreate(name)) }
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
    composable(route = Destination.PURCHASE_DETAIL.route, arguments = listOf(navArgument("highlightLineId") { nullable = true; defaultValue = null })) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("receiptId")
        if (idStr != null) {
            PurchaseDetailRoute(
                highlightLineId = backStackEntry.arguments?.getString("highlightLineId")?.let { PurchaseLineId(it) },
                onBack = { navController.popBackStack() },
                onNavigateToDocument = { rid -> navController.navigate(AppRoutes.purchaseDocument(rid)) }
            )
        }
    }
    composable(route = Destination.PURCHASE_DOCUMENT.route) { backStackEntry ->
        val idStr = backStackEntry.arguments?.getString("receiptId")
        if (idStr != null) {
            PurchaseDocumentViewerRoute(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

fun getPurchaseNavigationRoute(id: PurchaseReceiptId, status: DocumentStatus): String {
    return when (status) {
        DocumentStatus.DRAFT -> AppRoutes.purchaseDraft(id)
        DocumentStatus.POSTED, DocumentStatus.VOIDED, DocumentStatus.UNKNOWN -> AppRoutes.purchaseDetail(id)
    }
}
