package com.venkoi.cuentame.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.venkoi.cuentame.core.presentation.navigation.TopLevelDestination
import com.venkoi.cuentame.feature.activity.navigation.activityGraph
import com.venkoi.cuentame.feature.areas.navigation.areasGraph
import com.venkoi.cuentame.feature.categories.navigation.categoriesGraph
import com.venkoi.cuentame.feature.counts.navigation.countsGraph
import com.venkoi.cuentame.feature.home.navigation.homeGraph
import com.venkoi.cuentame.feature.ingredients.navigation.ingredientsGraph
import com.venkoi.cuentame.feature.preparations.navigation.preparationsGraph
import com.venkoi.cuentame.feature.production.navigation.productionGraph
import com.venkoi.cuentame.feature.purchases.navigation.purchasesGraph
import com.venkoi.cuentame.feature.reports.navigation.reportsGraph
import com.venkoi.cuentame.feature.priceintelligence.navigation.priceIntelligenceGraph
import com.venkoi.cuentame.feature.settings.navigation.settingsGraph
import com.venkoi.cuentame.feature.suppliers.navigation.suppliersGraph
import com.venkoi.cuentame.feature.waste.navigation.wasteGraph
import com.venkoi.cuentame.feature.menu.navigation.menuGraph
import com.venkoi.cuentame.feature.reorder.reorderGraph
import com.venkoi.cuentame.feature.sales.salesGraph

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
