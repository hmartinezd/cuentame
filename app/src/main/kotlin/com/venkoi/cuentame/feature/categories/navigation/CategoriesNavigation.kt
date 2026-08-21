package com.venkoi.cuentame.feature.categories.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.venkoi.cuentame.core.presentation.navigation.Destination
import com.venkoi.cuentame.feature.categories.ui.CategoryManagementRoute

fun NavGraphBuilder.categoriesGraph() {
    composable(Destination.SETTINGS_CATEGORIES.route) {
        CategoryManagementRoute()
    }
}
