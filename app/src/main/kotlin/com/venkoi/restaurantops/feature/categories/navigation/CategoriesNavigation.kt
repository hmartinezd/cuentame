package com.venkoi.restaurantops.feature.categories.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.venkoi.restaurantops.core.presentation.navigation.Destination
import com.venkoi.restaurantops.feature.categories.ui.CategoryManagementRoute

fun NavGraphBuilder.categoriesGraph() {
    composable(Destination.SETTINGS_CATEGORIES.route) {
        CategoryManagementRoute()
    }
}
