package com.venkoi.restaurantops.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.venkoi.restaurantops.core.presentation.navigation.TopLevelDestination
import com.venkoi.restaurantops.feature.activity.navigation.activityGraph
import com.venkoi.restaurantops.feature.areas.navigation.areasGraph
import com.venkoi.restaurantops.feature.categories.navigation.categoriesGraph
import com.venkoi.restaurantops.feature.counts.navigation.countsGraph
import com.venkoi.restaurantops.feature.home.navigation.homeGraph
import com.venkoi.restaurantops.feature.ingredients.navigation.ingredientsGraph
import com.venkoi.restaurantops.feature.preparations.navigation.preparationsGraph
import com.venkoi.restaurantops.feature.production.navigation.productionGraph
import com.venkoi.restaurantops.feature.purchases.navigation.purchasesGraph
import com.venkoi.restaurantops.feature.reports.navigation.reportsGraph
import com.venkoi.restaurantops.feature.priceintelligence.navigation.priceIntelligenceGraph
import com.venkoi.restaurantops.feature.settings.navigation.settingsGraph
import com.venkoi.restaurantops.feature.suppliers.navigation.suppliersGraph
import com.venkoi.restaurantops.feature.waste.navigation.wasteGraph
import com.venkoi.restaurantops.feature.menu.navigation.menuGraph
import com.venkoi.restaurantops.feature.reorder.reorderGraph
import com.venkoi.restaurantops.feature.sales.salesGraph

@Composable
fun CuentameNavHost(
    navController: NavHostController,
    onBackClick: () -> Unit,
    menuManagementEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    startDestination: String = TopLevelDestination.HOME.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        homeGraph(navController)
        activityGraph(navController)
        ingredientsGraph(navController)
        reorderGraph(navController)
        countsGraph(navController)
        purchasesGraph(navController)
        wasteGraph(navController)
        reportsGraph(navController)
        priceIntelligenceGraph(navController)
        settingsGraph(navController, onBackClick)
        preparationsGraph(navController, onBackClick)
        menuGraph(navController, onBackClick, menuManagementEnabled)
        productionGraph(navController, onBackClick)
        areasGraph(navController, onBackClick)
        categoriesGraph()
        suppliersGraph(navController)
        salesGraph(navController)
    }
}
