package com.miara.cuentame.feature.categories.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.miara.cuentame.feature.categories.ui.CategoryManagementRoute

fun NavGraphBuilder.categoriesGraph() {
    composable("settings/categories") {
        CategoryManagementRoute()
    }
}
