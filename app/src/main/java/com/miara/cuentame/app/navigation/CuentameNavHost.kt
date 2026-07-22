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
import com.miara.cuentame.feature.areas.ui.AreaManagementRoute
import com.miara.cuentame.feature.categories.ui.CategoryManagementRoute
import com.miara.cuentame.feature.home.HomeRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientDetailRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientFormRoute
import com.miara.cuentame.feature.ingredients.ui.IngredientListRoute
import com.miara.cuentame.feature.settings.ui.RestaurantProfileRoute
import com.miara.cuentame.feature.settings.ui.SettingsRoute

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
            PlaceholderScreen(TopLevelDestination.ACTIVITY)
        }
        composable(route = TopLevelDestination.REPORTS.route) {
            PlaceholderScreen(TopLevelDestination.REPORTS)
        }
        composable(route = Destination.SETTINGS.route) {
            SettingsRoute(
                onNavigateToAreas = { navController.navigate("settings/areas") },
                onNavigateToCategories = { navController.navigate("settings/categories") },
                onNavigateToRestaurant = { navController.navigate("settings/restaurant") }
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
