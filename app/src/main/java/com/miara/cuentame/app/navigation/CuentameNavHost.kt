package com.miara.cuentame.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.miara.cuentame.core.common.ids.IngredientId
import com.miara.cuentame.core.common.ids.PurchaseLineId
import com.miara.cuentame.core.common.ids.PurchaseReceiptId
import com.miara.cuentame.core.common.ids.SupplierId
import com.miara.cuentame.feature.areas.ui.AreaManagementRoute
import com.miara.cuentame.feature.categories.ui.CategoryManagementRoute
import com.miara.cuentame.feature.home.HomeRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientDetailRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientFormRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientListRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseDetailRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseDraftRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseLineRoute
import com.miara.cuentame.feature.purchases.ui.PurchaseListRoute
import com.miara.cuentame.feature.settings.ui.RestaurantProfileRoute
import com.miara.cuentame.feature.settings.ui.SettingsRoute
import com.miara.cuentame.feature.suppliers.ui.SupplierFormRoute
import com.miara.cuentame.feature.suppliers.ui.SupplierListRoute

@Composable
fun CuentameNavHost(
    navController: NavHostController,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = TopLevelDestination.HOME.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(route = TopLevelDestination.HOME.route) {
            HomeRoute()
        }
        composable(route = TopLevelDestination.INVENTORY.route) {
            IngredientListRoute(
                onAddIngredient = { navController.navigate(Destination.INGREDIENT_CREATE.route) },
                onIngredientClick = { id -> navController.navigate("ingredient/${id.value}") }
            )
        }
        composable(route = TopLevelDestination.COUNT.route) {
            PlaceholderScreen(TopLevelDestination.COUNT)
        }
        composable(route = TopLevelDestination.ACTIVITY.route) {
            PurchaseListRoute(
                onBack = { navController.popBackStack() },
                onAddPurchase = { navController.navigate(Destination.PURCHASE_CREATE.route) },
                onPurchaseClick = { id -> 
                    // We need to decide if we navigate to Draft or Detail based on status.
                    // But for now let's just go to a generic route that handles it or just Detail.
                    navController.navigate("purchase/${id.value}/detail")
                }
            )
        }
        composable(route = TopLevelDestination.REPORTS.route) {
            PlaceholderScreen(TopLevelDestination.REPORTS)
        }
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
        
        // Suppliers
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

        // Purchases
        composable(route = Destination.PURCHASE_CREATE.route) {
            PurchaseDraftRoute(
                purchaseId = null,
                onBack = { navController.popBackStack() },
                onNavigateToDraft = { id -> 
                    navController.navigate("purchase/${id.value}") {
                        popUpTo(Destination.PURCHASE_CREATE.route) { inclusive = true }
                    }
                },
                onAddLine = {}, // Not used when purchaseId is null
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
        
        composable(route = Destination.INGREDIENT_CREATE.route) {
            IngredientFormRoute(
                onBack = { navController.popBackStack() },
                onSaveSuccess = { id: IngredientId ->
                    navController.navigate("ingredient/${id.value}") {
                        popUpTo(Destination.INGREDIENT_CREATE.route) { inclusive = true }
                    }
                }
            )
        }
        composable(route = Destination.INGREDIENT_DETAIL.route) { backStackEntry ->
            val idStr = backStackEntry.arguments?.getString("ingredientId")
            if (idStr != null) {
                IngredientDetailRoute(
                    ingredientId = IngredientId(idStr),
                    onEditClick = { id -> navController.navigate("ingredient/${id.value}/edit") },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(route = Destination.INGREDIENT_EDIT.route) { backStackEntry ->
            val idStr = backStackEntry.arguments?.getString("ingredientId")
            if (idStr != null) {
                IngredientFormRoute(
                    ingredientId = IngredientId(idStr),
                    onBack = { navController.popBackStack() },
                    onSaveSuccess = { _ -> navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(destination: TopLevelDestination) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(destination.titleTextId), style = MaterialTheme.typography.headlineMedium)
            Text(text = "Feature placeholder")
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.headlineMedium)
            Text(text = "Feature placeholder")
        }
    }
}
